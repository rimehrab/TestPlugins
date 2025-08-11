// use an integer for version numbers
version = 57

android {
    namespace = "com.rimehrab"
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "AnimeDekho Provider"
    authors = listOf("Mehrab Mahmud Udoy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "Cartoon",
    )
    language = "hi"

    iconUrl = "https://animedekho.co/wp-content/uploads/2023/07/AnimeDekho-Logo-300x-1.png"
    
    isCrossPlatform = false
}