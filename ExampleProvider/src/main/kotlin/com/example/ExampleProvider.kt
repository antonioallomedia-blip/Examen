package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class HanimeTV : MainAPI() {

    override var mainUrl = "https://hanime.tv"
    override var name = "HanimeTV"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    override val mainPage = mainPageOf(
        "$mainUrl/browse/trending" to "Trending Now",
        "$mainUrl/browse/newest" to "Newest"
    )

    // ------------------------------------------------------------------
    //  HOME PAGE
    // ------------------------------------------------------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}?page=$page").document
        val items = doc.select("div.hv-card").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = items,
                    isHorizontalImages = false
                )
            ),
            hasNext = items.isNotEmpty()
        )
    }

    // ------------------------------------------------------------------
    //  SEARCH
    // ------------------------------------------------------------------
    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/search?q=${query.trim()}"
        val doc = app.get(url).document
        return doc.select("div.hv-card").mapNotNull { it.toSearchResult() }
    }

    // ------------------------------------------------------------------
    //  LOAD (DETAIL PAGE)
    // ------------------------------------------------------------------
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.hv-title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: return null

        val poster = doc.selectFirst("img.hv-image")?.attr("src")
            ?: doc.selectFirst("div.hv-cover img")?.attr("src")

        val description = doc.selectFirst("div.hv-description")?.text()
            ?: doc.selectFirst("div.hv-synopsis")?.text()

        val tags = doc.select("div.hv-tags a").map { it.text() }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    // ------------------------------------------------------------------
    //  LOAD LINKS (VIDEO SOURCES)
    // ------------------------------------------------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // Cherche un lecteur embarqué (iframe, script avec .m3u8)
        val scripts = doc.select("script").map { it.html() }.joinToString("\n")
        val m3u8Regex = Regex("""https?://[^"']+\.m3u8[^"']*""")
        val matches = m3u8Regex.findAll(scripts).map { it.value }.toSet()

        if (matches.isEmpty()) return false

        matches.forEach { streamUrl ->
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    // ------------------------------------------------------------------
    //  HELPER
    // ------------------------------------------------------------------
    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("div.hv-card-title")?.text()
            ?: this.selectFirst("h3")?.text()
            ?: return null
        val poster = this.selectFirst("img")?.attr("src")

        val fullUrl = if (link.startsWith("http")) link else "$mainUrl$link"

        return newMovieSearchResponse(title, fullUrl, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}