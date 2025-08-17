package com.rimehrab

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DflixProvider : MainAPI() {
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "DFLIX (BDIX)"
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

    private suspend fun loginIfNeeded() {
        if (loginCookie?.size != 2) {
            val response = app.get("$mainUrl/login/demo", allowRedirects = false)
            loginCookie = response.cookies
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loginIfNeeded()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie!!).document
        val cards = doc.select("div.card")
        val items = cards.mapNotNull { parseCardToSearchResponse(it) }
        return newHomePageResponse(request.name, items, true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        loginIfNeeded()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie!!).document
        val cards = doc.select("div.card:not(:has(div.poster.disable))")
        return cards.mapNotNull { parseCardToSearchResponse(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        loginIfNeeded()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(".movie-detail-content > h3:nth-child(1)").text()
        val dataUrl = doc.select("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)").attr("href")
        val size = doc.select(".badge.badge-fill").text()
        val posterUrl = doc.select(".movie-detail-banner > img:nth-child(1)").attr("src")
        val plot = "<b>$size</b><br><br>" + doc.select(".storyline").text()
        val tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
        val actors = doc.select("div.col-lg-2").map { parseActor(it) }
        val recommendations = doc.select("div.badge-outline > a").map { buildRecommendation(it, title, posterUrl) }

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
        callback(
            newExtractorLink(
                data,
                this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    // --- Helper functions ---

    private fun parseCardToSearchResponse(card: Element): SearchResponse? {
        val url = mainUrl + card.select("div.card > a:nth-child(1)").attr("href")
        val title = card.select("div.card > div:nth-child(2) > h3:nth-child(1)").text() + " " +
                    card.select("div.feedback > span:nth-child(1)").text()
        val posterUrl = card.selectFirst("div.poster > img:nth-child(1)")?.attr("src")
        val qualityStr = card.select("div.card > a:nth-child(1) > span:nth-child(1)").text()

        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getSearchQuality(qualityStr)
            addDubStatus(
                dubExist = "DUAL" in qualityStr,
                subExist = false
            )
        }
    }

    private fun buildRecommendation(card: Element, title: String, posterUrl: String): SearchResponse {
        val recTitle = "$title ${card.text()}"
        val recUrl = mainUrl + card.attr("href")
        return newMovieSearchResponse(recTitle, recUrl, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun parseActor(card: Element): ActorData {
        val imgElem = card.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = imgElem.attr("src")
        val name = imgElem.attr("alt")
        val role = card.select("div.col-lg-2 > p.text-center.text-white").text()
        return ActorData(actor = Actor(name, img), roleString = role)
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val value = check?.lowercase() ?: return null
        return when {
            "4k" in value -> SearchQuality.FourK
            "web-r" in value || "web-dl" in value -> SearchQuality.WebRip
            "br" in value -> SearchQuality.BlueRay
            listOf("hdts", "hdcam", "hdtc").any { it in value } -> SearchQuality.HdCam
            "cam" in value -> SearchQuality.Cam
            "hd" in value || "1080p" in value -> SearchQuality.HD
            else -> null
        }
    }
}