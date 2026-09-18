// Use an integer for version numbers
version = 1

cloudstream {
    description = "Hanime.tv provider for CloudStream."
    authors = listOf("antonioallomedia-blip")
    status = 1
    tvTypes = listOf("Movie")
    requiresResources = true
    language = "en"
    iconUrl = "https://hanime.tv/favicon.ico"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}