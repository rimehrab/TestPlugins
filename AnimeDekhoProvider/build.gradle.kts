version = 57

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

cloudstream {
    language = "hi"
    authors = listOf("Hindi Provider")
    description = "Includes AnimeDekho,OnePace(DUB,SUB) and HindiSubAnime"
    status = 1
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "Cartoon"
    )
    iconUrl = "https://animedekho.co/wp-content/uploads/2023/07/AnimeDekho-Logo-300x-1.png"
    isCrossPlatform = true
}