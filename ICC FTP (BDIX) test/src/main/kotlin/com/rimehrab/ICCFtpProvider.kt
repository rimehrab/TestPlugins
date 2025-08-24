package com.rimehrab

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

class ICCFtpProvider : MainAPI() {
    override var mainUrl = "http://10.16.100.244/"
    override var name = "ICC FTP (BDIX TEST)"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.AsianDrama,
        TvType.Documentary,
        TvType.OVA,
        TvType.Others
    )

    override val mainPage = mainPageOf(
        "index.php?category=0" to "Latest",
        "index.php?category=59" to "Bangla Movies",
        "index.php?category=2" to "Hindi Movies",
        "index.php?category=19" to "English Movies",
        "index.php?category=43" to "Dual Audio",
        "index.php?category=32" to "South Movies",
        "index.php?category=33" to "Animated",
        "index.php?category=36" to "English Series",
        "index.php?category=37" to "Hindi Series",
        "index.php?category=41" to "Documentary"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}").document
        val results = document.select("div.post-wrapper > a")
            .mapNotNull { extractHomePost(it) }
        return newHomePageResponse(request.name, results, false)
    }

    private fun extractHomePost(element: Element): SearchResponse {
        val title = element.select("img").attr("alt")
        val link = mainUrl + element.select("a").attr("href")
        val thumbnail = mainUrl + element.select("img").attr("src")
        return newMovieSearchResponse(title, link, TvType.Movie) {
            posterUrl = thumbnail
        }
    }

    private fun extractSearchItem(item: SearchJsonItem): SearchResponse {
        val movieTitle = item.name.toString()
        val movieUrl = "${mainUrl}player.php?play=${item.id}"
        val poster = "${mainUrl}files/${item.image}"
        return newMovieSearchResponse(movieTitle, movieUrl, TvType.Movie) {
            posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val requestBody = "cSearch=$query".toRequestBody(mediaType)
        val responseText = app.post("$mainUrl/command.php", requestBody = requestBody).text
        val items = AppUtils.parseJson<SearchJson>(responseText)
        return items.mapNotNull { extractSearchItem(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val infoTable = doc.select(".table > tbody:nth-child(1)")
        val title = infoTable.select("tr:nth-child(1)").text()
        val year = infoTable.select("tr:nth-child(2) > td:nth-child(2)").text().toIntOrNull()
        val genres = infoTable.select("tr:nth-child(5) > td:nth-child(2)").text().split(",")
        val description = infoTable.select("tr:nth-child(12) > td:nth-child(2)").text()
        val time = infoTable.select("tr:nth-child(4) > td:nth-child(2)").text()
        val trailerUrl = doc.selectFirst(".pull-left")?.attr("data-thevideo")
        val trailers = mutableListOf<TrailerData>()
        trailerUrl?.let {
            trailers.add(TrailerData(it, false, mainUrl))
        }
        val posterUrl = mainUrl + (doc.selectFirst(".col-md-4 > img")?.attr("src") ?: "")
        val episodeLinks = doc.select(".btn-group > ul > li")

        return if (episodeLinks.isEmpty()) {
            val directLink = doc.selectFirst("a.btn")?.attr("href")
            newMovieLoadResponse(title, url, TvType.Movie, directLink) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = getDurationFromString(time)
                this.trailers = trailers
            }
        } else {
            val episodes = episodeLinks.map {
                val epLink = it.select("a").attr("href")
                val epTitle = it.select("a").text()
                val epSpan = it.select("span").text()
                newEpisode(epLink) {
                    name = epTitle.replace(epSpan, "")
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = genres
                this.duration = getDurationFromString(time)
                this.trailers = trailers
            }
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
                url = data
            )
        )
        return true
    }

    class SearchJson : ArrayList<SearchJsonItem>()
    data class SearchJsonItem(
        val id: String?,
        val image: String?,
        val name: String?,
        val type: String?
    )
}