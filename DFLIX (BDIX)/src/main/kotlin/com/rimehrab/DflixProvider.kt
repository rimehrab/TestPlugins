package com.rimehrab

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.intercept
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import okhttp3.Interceptor
import org.jsoup.nodes.Element

/**
 * Main provider class for Dflix.
 * This provider scrapes movie information and direct video links from the website.
 */
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

    // This interceptor automatically handles the login logic for every network request.
    // It ensures that a valid session cookie is attached before any page is fetched.
    override val anroidInterceptor: Interceptor?
        get() = intercept { chain ->
            val request = chain.request()
            val cookies = app.get("$mainUrl/login/demo", allowRedirects = false).cookies
            val newRequest = request.newBuilder()
                .header(
                    "Cookie",
                    cookies.map { (key, value) -> "$key=$value" }.joinToString("; ")
                )
                .build()
            chain.proceed(newRequest)
        }


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/m/${request.data}/$page"
        val document = app.get(url).document

        val movies = document.select("div.card").mapNotNull {
            toSearchResponse(it)
        }

        return newHomePageResponse(request.name, movies, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/m/find/$query"
        val document = app.get(url).document

        // The ":not" selector is used to filter out disabled or invalid items.
        return document.select("div.card:not(:has(div.poster.disable))").mapNotNull {
            toSearchResponse(it)
        }
    }

    // A reusable helper function to parse movie card elements from the site.
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
            this.quality = getQualityFromName(qualityBadge)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".movie-detail-content > h3")?.text()?.trim()
            ?: return newMovieLoadResponse("", url, TvType.Movie, "")

        val posterUrl = fixUrlNull(document.selectFirst(".movie-detail-banner > img")?.attr("src"))
        val plot = "<b>${document.select(".badge.badge-fill").text()}</b><br><br>" +
                document.select(".storyline").text()

        // The direct link to the video file, which will be passed to `loadLinks`.
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
        data: String, // This `data` is the direct video URL from the `load` function.
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
}