package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class HanimeTV : MainAPI() {

    override var mainUrl = "https://hanime.tv"
    override var name = "HanimeTV"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    override val mainPage = mainPageOf(
        "trending" to "Trending Now"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val trending = app.get("$mainUrl/api/v8/video?trending=day&page=$page")
            .parsedSafe<HanimeTrendingResponse>()

        val items = trending?.results?.map { video ->
            newMovieSearchResponse(video.name, video.slug, TvType.Movie) {
                this.posterUrl = video.coverUrl
            }
        } ?: emptyList()

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = "Trending Now",
                    list = items,
                    isHorizontalImages = false
                )
            ),
            hasNext = (trending?.nextPage != null)
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val response = app.get("$mainUrl/api/v8/search?q=${query.trim()}")
            .parsedSafe<HanimeSearchResponse>()

        return response?.hits?.map { hit ->
            newMovieSearchResponse(hit.name, hit.slug, TvType.Movie) {
                this.posterUrl = hit.coverUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get("$mainUrl/api/v8/video?id=$url")
            .parsedSafe<HanimeVideoResponse>() ?: return null
        val video = response.hentaiVideo ?: return null

        return newMovieLoadResponse(
            name = video.name,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.posterUrl = video.coverUrl
            this.plot = video.description?.replace(Regex("<[^>]*>"), "")?.trim()
            this.year = video.releaseDate?.take(4)?.toIntOrNull()
            this.tags = video.tags?.map { it.text }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get("$mainUrl/api/v8/video?id=$data")
            .parsedSafe<HanimeVideoResponse>() ?: return false

        val streams = response.videosManifest?.servers
            ?.flatMap { it.streams ?: emptyList() }
            ?.filter { it.url.isNotBlank() }
            ?: return false

        streams.forEach { stream ->
            val qualityLabel = when (stream.height) {
                1080 -> "1080p"
                720  -> "720p"
                480  -> "480p"
                360  -> "360p"
                else -> "${stream.height}p"
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "$name - $qualityLabel",
                    url = stream.url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.quality = when (stream.height) {
                        1080 -> Qualities.P1080.value
                        720  -> Qualities.P720.value
                        480  -> Qualities.P480.value
                        360  -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                    this.referer = mainUrl
                }
            )
        }
        return streams.isNotEmpty()
    }
}

// ======================================================================
//  DATA MODELS
// ======================================================================

data class HanimeTrendingResponse(
    @JsonProperty("hentai_videos") val results: List<HanimeTrendingItem>? = null,
    @JsonProperty("next_page")     val nextPage: String? = null
)

data class HanimeTrendingItem(
    @JsonProperty("name")       val name: String,
    @JsonProperty("slug")       val slug: String,
    @JsonProperty("cover_url")  val coverUrl: String?
)

data class HanimeSearchResponse(
    @JsonProperty("hits") val hits: List<HanimeSearchHit>? = null
)

data class HanimeSearchHit(
    @JsonProperty("name")       val name: String,
    @JsonProperty("slug")       val slug: String,
    @JsonProperty("cover_url")  val coverUrl: String?
)

data class HanimeVideoResponse(
    @JsonProperty("hentai_video")    val hentaiVideo: HanimeVideo? = null,
    @JsonProperty("videos_manifest") val videosManifest: HanimeManifest? = null
)

data class HanimeVideo(
    @JsonProperty("name")         val name: String,
    @JsonProperty("description")  val description: String? = null,
    @JsonProperty("cover_url")    val coverUrl: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("tags")         val tags: List<HanimeTag>? = null
)

data class HanimeTag(
    @JsonProperty("text") val text: String
)

data class HanimeManifest(
    @JsonProperty("servers") val servers: List<HanimeServer>? = null
)

data class HanimeServer(
    @JsonProperty("streams") val streams: List<HanimeStream>? = null
)

data class HanimeStream(
    @JsonProperty("url")      val url: String,
    @JsonProperty("height")   val height: Int,
    @JsonProperty("size_mbs") val sizeMbs: Double? = null
)