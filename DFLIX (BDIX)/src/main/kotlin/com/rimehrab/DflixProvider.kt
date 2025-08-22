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

// Local testing entrypoint (requires cloudstream test harness dependency)
// suspend fun main() {
//     val tester = com.lagradost.cloudstreamtest.ProviderTester(DflixProvider())
//     // tester.testAll()
//     tester.testMainPage(verbose = true)
//     // tester.testSearch(query = "gun", verbose = true)
//     // tester.testLoad("https://dflix.discoveryftp.net/m/view/34449")
// }

private object DflixConfig {
    const val BASE_URL = "https://dflix.discoveryftp.net"
    const val LOGIN_DEMO = "/login/demo"
    const val LISTING_PREFIX = "/m/"
    const val SEARCH_PREFIX = "/m/find/"
}

class DflixProvider : MainAPI() {
    override var mainUrl = DflixConfig.BASE_URL
    override var name = "DFLIX (BDIX) beta"
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

    private var sessionCookies: Map<String, String>? = null

    private suspend fun fetchSessionCookies(): Map<String, String> {
        val cached = sessionCookies
        if (cached != null && cached.isNotEmpty()) return cached
        val response = app.get(mainUrl + DflixConfig.LOGIN_DEMO, allowRedirects = false)
        return response.cookies.also { sessionCookies = it }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val cookies = fetchSessionCookies()
        val url = buildString { append(mainUrl).append(DflixConfig.LISTING_PREFIX).append(request.data).append("/").append(page) }
        val doc = app.get(url, cookies = cookies).document
        val cards = doc.select("div.card")
        val items = cards.mapNotNull { card -> parseCard(card) }
        return newHomePageResponse(request.name, items, true)
    }

    private fun parseCard(card: Element): SearchResponse? {
        val anchor = card.selectFirst("div.card > a:nth-child(1)") ?: return null
        val href = anchor.attr("href")
        if (href.isNullOrBlank()) return null
        val url = mainUrl + href

        val baseTitle = card.select("div.card > div:nth-child(2) > h3:nth-child(1)").text()
        val badge = card.select("div.feedback > span:nth-child(1)").text()
        val title = listOf(baseTitle, badge).filter { it.isNotBlank() }.joinToString(" ")

        return newAnimeSearchResponse(title.ifBlank { baseTitle.ifBlank { "Unknown" } }, url, TvType.Movie) {
            this.posterUrl = card.selectFirst("div.poster > img:nth-child(1)")?.attr("src")
            val badgeText = anchor.selectFirst("span:nth-child(1)")?.text().orEmpty()
            this.quality = mapQuality(badgeText)
            addDubStatus(
                dubExist = badgeText.contains("DUAL", ignoreCase = true),
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cookies = fetchSessionCookies()
        val doc = app.get(mainUrl + DflixConfig.SEARCH_PREFIX + query, cookies = cookies).document
        val cards = doc.select("div.card:not(:has(div.poster.disable))")
        return cards.mapNotNull { parseCard(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val cookies = fetchSessionCookies()
        val doc = app.get(url, cookies = cookies).document
        val title = doc.selectFirst(".movie-detail-content > h3:nth-child(1)")?.text().orEmpty()
        val dataUrl = doc.selectFirst("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)")?.attr("href").orEmpty()
        val size = doc.select(".badge.badge-fill").text()
        val poster = doc.selectFirst(".movie-detail-banner > img:nth-child(1)")?.attr("src")
        return newMovieLoadResponse(title.ifBlank { "Unknown" }, url, TvType.Movie, dataUrl) {
            this.posterUrl = poster
            this.plot = buildString {
                if (size.isNotBlank()) append("<b>").append(size).append("</b><br><br>")
                append(doc.select(".storyline").text())
            }
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").mapNotNull { parseActor(it) }
            this.recommendations = doc.select("div.badge-outline > a").map { buildVariant(it, title, poster) }
        }
    }
    private fun buildVariant(anchor: Element, baseTitle: String, posterUrl: String?): SearchResponse {
        val movieName = (baseTitle.ifBlank { "Unknown" } + " " + anchor.text()).trim()
        val movieUrl = mainUrl + anchor.attr("href")
        return newMovieSearchResponse(movieName, movieUrl, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun parseActor(card: Element): ActorData? {
        val html = card.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = html.attr("src")
        val name = html.attr("alt")
        if (name.isNullOrBlank() && img.isNullOrBlank()) return null
        val role = card.select("div.col-lg-2 > p.text-center.text-white").text()
        return ActorData(Actor(name, img), roleString = role)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
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
    private fun mapQuality(raw: String?): SearchQuality? {
        val text = raw?.lowercase()?.trim() ?: return null
        return when {
            "2160" in text || "4k" in text -> SearchQuality.FourK
            "1080" in text || ("hd" in text && !("cam" in text || "ts" in text)) -> SearchQuality.HD
            "webrip" in text || "web-r" in text || "web-dl" in text -> SearchQuality.WebRip
            "bluray" in text || "brrip" in text || "br" in text -> SearchQuality.BlueRay
            "hdts" in text || "hdcam" in text || "hdtc" in text -> SearchQuality.HdCam
            "cam" in text || "ts" in text -> SearchQuality.Cam
            else -> null
        }
    }
}
}