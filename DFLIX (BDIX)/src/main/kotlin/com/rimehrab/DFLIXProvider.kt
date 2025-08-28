package com.rimehrab


import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.jsoup.select.Elements


//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(DFLIXProvider())
////    providerTester.testAll()
//    providerTester.testMainPage(verbose = true)
////    providerTester.testSearch(query = "gun",verbose = true)
////    providerTester.testLoad("https://dflix.discoveryftp.net/m/view/34449")
//}

object DflixSelectors {
    const val mainPageCard = "div.card"
    const val searchResultCard = "div.moviegrid > div.card:not(:has(div.poster.disable))"
    const val cardLink = "div.card > a:nth-child(1)"
    const val cardTitle = "div.card > div:nth-child(2) > h3:nth-child(1)"
    const val cardQuality = "div.feedback > span:nth-child(1)"
    const val cardPoster = "div.poster > img:nth-child(1)"
    const val cardDualAudio = "div.card > a:nth-child(1) > span:nth-child(1)"

    const val movieDetailTitle = ".movie-detail-content > h3:nth-child(1)"
    const val movieDetailDataUrl = "div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)"
    const val movieDetailSize = ".badge.badge-fill"
    const val movieDetailPoster = ".movie-detail-banner > img:nth-child(1)"
    const val movieDetailPlot = ".storyline"
    const val movieDetailTags = ".ganre-wrapper > a"
    const val movieDetailActors = "div.col-lg-2"
    const val movieDetailRecommendations = "div.badge-outline > a"

    const val actorImage = "div.col-lg-2 > a:nth-child(1) > img:nth-child(1)"
    const val actorName = "div.col-lg-2 > a:nth-child(1) > img:nth-child(1)"
    const val actorRole = "div.col-lg-2 > p.text-center.text-white"
}

class DFLIXProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "DFLIX"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie
    )
    override val mainPage = mainPageOf(
        "category/Bangla" to "Bangla",
        "category/English" to "English",
        "category/Hindi" to "Hindi",
        "category/Tamil" to "Tamil",
        "category/Animation" to "Animation",
        "category/Others" to "Others"
    )

    private var loginCookie: Map<String, String>? = null
    private suspend fun login() {
        if (loginCookie.isNullOrEmpty()) {
            val client =
                app.get("https://dflix.discoveryftp.net/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    private fun parseMovieCards(elements: Elements): List<SearchResponse> {
        return elements.mapNotNull { post ->
            val url = mainUrl + post.select(DflixSelectors.cardLink).attr("href")
            val title = post.select(DflixSelectors.cardTitle).text() + ' ' +
                    post.select(DflixSelectors.cardQuality).text()
            newAnimeSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = post.selectFirst(DflixSelectors.cardPoster)?.attr("src")
                val check = post.select(DflixSelectors.cardDualAudio).text()
                this.quality = getSearchQuality(check)
                addDubStatus(
                    dubExist = when {
                        "DUAL" in check -> true
                        else -> false
                    },
                    subExist = false
                )
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie!!).document
        val homeResponse = doc.select(DflixSelectors.mainPageCard)
        val home = parseMovieCards(homeResponse)
        return newHomePageResponse(request.name, home, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie!!).document
        val searchResponse = doc.select(DflixSelectors.searchResultCard)
        return parseMovieCards(searchResponse)
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(DflixSelectors.movieDetailTitle).text()
        val dataUrl = doc.select(DflixSelectors.movieDetailDataUrl)
            .attr("href")
        val size = doc.select(DflixSelectors.movieDetailSize).text()
        val img = doc.select(DflixSelectors.movieDetailPoster).attr("src")
        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = img
            this.plot = "<b>$size</b><br><br>" + doc.select(DflixSelectors.movieDetailPlot).text()
            this.tags = doc.select(DflixSelectors.movieDetailTags).map { it.text().replace(",", "") }
            this.actors = doc.select(DflixSelectors.movieDetailActors).map { actor(it) }
            this.recommendations = doc.select(DflixSelectors.movieDetailRecommendations).map { qualityRecommendations(it,title,img) }
        }
    }
    private fun qualityRecommendations(post: Element, title:String, imageLink:String): SearchResponse{
        val movieName = title +" "+ post.text()
        val movieUrl = mainUrl + post.attr("href")
        return newMovieSearchResponse(movieName,movieUrl,TvType.Movie) {
            this.posterUrl = imageLink
        }
    }

    private fun actor(post: Element): ActorData {
        val html = post.select(DflixSelectors.actorImage)
        val img = html.attr("src")
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(
                name,
                img
            ), roleString = post.select(DflixSelectors.actorRole).text()
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data,
                this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }
}
