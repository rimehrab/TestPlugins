package com.rimehrab

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DflixMoviesProvider : MainAPI() {
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "dflix rewritten test"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(TvType.Movie, TvType.AnimeMovie)

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
        if (loginCookie?.size != 2) {
            val client = app.get("$mainUrl/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie!!).document
        val items = doc.select("div.card").mapNotNull { toResult(it) }
        return newHomePageResponse(request.name, items, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie!!).document
        return doc.select("div.card:not(:has(div.poster.disable))").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(".movie-detail-content > h3:nth-child(1)").text()
        val dataUrl = doc.select("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)").attr("href")
        val size = doc.select(".badge.badge-fill").text()
        val img = doc.select(".movie-detail-banner > img:nth-child(1)").attr("src")
        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = img
            this.plot = "<b>$size</b><br><br>" + doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
            this.recommendations = doc.select("div.badge-outline > a").map { qualityRecommendations(it, title, img) }
        }
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

    private fun toResult(post: Element): SearchResponse? {
        val url = mainUrl + post.select("div.card > a:nth-child(1)").attr("href")
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text() + " " +
                post.select("div.feedback > span:nth-child(1)").text()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("div.poster > img:nth-child(1)")?.attr("src")
            val check = post.select("div.card > a:nth-child(1) > span:nth-child(1)").text()
            this.quality = getSearchQuality(check)
            addDubStatus(
                dubExist = "DUAL" in check,
                subExist = false
            )
        }
    }

    private fun qualityRecommendations(post: Element, title: String, imageLink: String): SearchResponse {
        val movieName = "$title ${post.text()}"
        val movieUrl = mainUrl + post.attr("href")
        return newMovieSearchResponse(movieName, movieUrl, TvType.Movie) {
            this.posterUrl = imageLink
        }
    }

    private fun actor(post: Element): ActorData {
        val html = post.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = html.attr("src")
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(name, img),
            roleString = post.select("div.col-lg-2 > p.text-center.text-white").text()
        )
    }

    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase() ?: return null
        return when {
            "4k" in lowercaseCheck -> SearchQuality.FourK
            "web-r" in lowercaseCheck || "web-dl" in lowercaseCheck -> SearchQuality.WebRip
            "br" in lowercaseCheck -> SearchQuality.BlueRay
            listOf("hdts", "hdcam", "hdtc").any { it in lowercaseCheck } -> SearchQuality.HdCam
            "cam" in lowercaseCheck -> SearchQuality.Cam
            "hd" in lowercaseCheck || "1080p" in lowercaseCheck -> SearchQuality.HD
            else -> null
        }
    }
}