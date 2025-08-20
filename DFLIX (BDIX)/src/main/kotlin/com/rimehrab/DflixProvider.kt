package com.rimehrab

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class DflixProvider : MainAPI() {

    override var name = "DFLIX (BDIX)"
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)

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
        if (loginCookie == null) {
            val client = app.get("$mainUrl/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        login()
        val url = "$mainUrl/m/${request.data}/$page"
        val document = app.get(url, cookies = loginCookie!!).document

        val movies = document.select("div.card").mapNotNull {
            toSearchResponse(it)
        }

        return newHomePageResponse(request.name, movies, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val url = "$mainUrl/m/find/$query"
        val document = app.get(url, cookies = loginCookie!!).document

        return document.select("div.card:not(:has(div.poster.disable))").mapNotNull {
            toSearchResponse(it)
        }
    }

    private fun toSearchResponse(element: Element): SearchResponse? {
        val linkElement = element.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = element.selectFirst("h3")?.text() ?: return null

        val posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
        val qualityBadge = linkElement.selectFirst("span")?.text()
        val extraInfo = element.selectFirst("div.feedback > span")?.text()
        val fullTitle = if (extraInfo != null) "$title $extraInfo" else title

        return newMovieSearchResponse(fullTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(qualityBadge)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val document = app.get(url, cookies = loginCookie!!).document

        val title = document.selectFirst(".movie-detail-content > h3")?.text()?.trim()
            ?: return newMovieLoadResponse("", url, TvType.Movie, "")

        val posterUrl = fixUrlNull(document.selectFirst(".movie-detail-banner > img")?.attr("src"))
        val plot = "<b>${document.select(".badge.badge-fill").text()}</b><br><br>" +
                document.select(".storyline").text()

        val dataUrl = fixUrl(
            document.selectFirst("div.col-md-12:nth-child(3) > div > a")?.attr("href") ?: ""
        )

        val tags = document.select(".ganre-wrapper > a").map { it.text() }
        val actors = document.select("div.col-lg-2").mapNotNull {
            val actorName = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val actorImage = fixUrlNull(it.selectFirst("img")?.attr("src"))
            val role = it.selectFirst("p")?.text()
            ActorData(Actor(actorName, actorImage), roleString = role)
        }
        val recommendations = document.select("div.badge-outline > a").mapNotNull {
            toSearchResponse(it)
        }

        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.tags = tags
            this.actors = actors
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                referer = mainUrl,
                quality = getQualityFromName(data)
            )
        )
        return true
    }

    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains("hdtc") -> SearchQuality.HdCam
                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }

    private fun getQualityFromName(name: String): Int {
        val lowerCaseName = name.lowercase()
        return when {
            lowerCaseName.contains("4k") || lowerCaseName.contains("2160") -> Qualities.P2160.value
            lowerCaseName.contains("1080") -> Qualities.P1080.value
            lowerCaseName.contains("720") -> Qualities.P720.value
            lowerCaseName.contains("480") -> Qualities.P480.value
            lowerCaseName.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}