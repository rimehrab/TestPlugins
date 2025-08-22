package com.rimehrab


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.util.Locale

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(BdixICCFtpProvider())
////    providerTester.testAll()
////    providerTester.testMainPage(verbose = true)
////   providerTester.testSearch(query = "dragon", verbose = true)
//    providerTester.testLoad("http://10.16.100.244/player.php?play=40575")
//}

open class ICCFtpProvider : MainAPI() {
    companion object {
        private const val DEFAULT_BASE_URL = "http://10.16.100.244/"

        private const val SELECTOR_HOME_ANCHORS = "div.post-wrapper > a"
        private const val SELECTOR_TABLE_BODY = ".table > tbody:nth-child(1)"
        private const val SELECTOR_POSTER = ".col-md-4 > img"
        private const val SELECTOR_EPISODE_LIST = ".btn-group > ul > li"
        private const val SELECTOR_PRIMARY_BUTTON = "a.btn"
        private const val SELECTOR_TRAILER = ".pull-left"
        private const val ATTR_DATA_TRAILER = "data-thevideo"
    }

    override var mainUrl = DEFAULT_BASE_URL
    override var name = "(BDIX) ICC FTP test"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
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

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl${request.data}").document
        val homeAnchors = doc.select(SELECTOR_HOME_ANCHORS)
        val home = homeAnchors.mapNotNull { post -> getPostResult(post) }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResult(post: Element): SearchResponse {
        val imageElement = post.selectFirst("img")
        val anchorElement = post.selectFirst("a")
        val title = imageElement?.attr("alt").orEmpty()
        val href = anchorElement?.attr("href").orEmpty()
        val imagePath = imageElement?.attr("src").orEmpty()
        val absoluteUrl = if (href.startsWith("http")) href else mainUrl + href
        val absoluteImage = if (imagePath.startsWith("http")) imagePath else mainUrl + imagePath
        return newMovieSearchResponse(title, absoluteUrl, TvType.Movie) { this.posterUrl = absoluteImage }
    }

    private fun getSearchResult(post: SearchJsonItem): SearchResponse {
        val name = post.name.toString()
        val url = "${mainUrl}player.php?play=${post.id}"
        val image = "${mainUrl}files/${post.image}"
        return newMovieSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = image
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val body = ("cSearch=" + q).toRequestBody(mediaType)
        val responseText = app.post("${mainUrl}command.php", requestBody = body).text
        val json = try {
            AppUtils.parseJson<SearchJson>(responseText)
        } catch (_: Throwable) {
            return emptyList()
        }
        return json.mapNotNull { getSearchResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val table = doc.select(SELECTOR_TABLE_BODY)
        val title = table.select("tr:nth-child(1)").text()
        val year = table.select("tr:nth-child(2) > td:nth-child(2)").text().toIntOrNull()
        val genre = table.select("tr:nth-child(5) > td:nth-child(2)").text().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val plot = table.select("tr:nth-child(12) > td:nth-child(2)").text()
        val duration = table.select("tr:nth-child(4) > td:nth-child(2)").text()
        val trailer = doc.selectFirst(SELECTOR_TRAILER)?.attr(ATTR_DATA_TRAILER)
        val trailerData = mutableListOf<TrailerData>()
        if (!trailer.isNullOrEmpty()) {
            trailerData.add(
                TrailerData(
                    extractorUrl = trailer,
                    raw = false,
                    referer = mainUrl
                )
            )
        }
        val posterPath = doc.selectFirst(SELECTOR_POSTER)?.attr("src").orEmpty()
        val image = if (posterPath.startsWith("http")) posterPath else mainUrl + posterPath
        val episodeLis = doc.select(SELECTOR_EPISODE_LIST)
        if (episodeLis.isEmpty()) {
            val link = doc.selectFirst(SELECTOR_PRIMARY_BUTTON)?.attr("href")
            return newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                this.tags = genre
                this.duration = getDurationFromString(duration)
                this.trailers = trailerData
            }
        } else {
            val episodesData = mutableListOf<Episode>()
            episodeLis.forEach {
                val link = it.selectFirst("a")?.attr("href").orEmpty()
                val name = it.selectFirst("a")?.text().orEmpty()
                val span = it.selectFirst("span")?.text().orEmpty()
                episodesData.add(
                    newEpisode(link) {
                        this.name = name.replace(span, "").trim()
                    }
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = image
                this.year = year
                this.plot = plot
                this.tags = genre
                this.duration = getDurationFromString(duration)
                this.trailers = trailerData
            }
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
 ): Boolean {
    val quality = inferQualityFromUrl(data)
    callback.invoke(
        newExtractorLink(
            this.name,
            this.name,
            data,
            null, // type
            quality,
            mainUrl,
            mapOf("Referer" to mainUrl)
         )
     )
     return true
 }

    private fun inferQualityFromUrl(url: String): Int {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            listOf("2160", "4k").any { lower.contains(it) } -> 2160
            lower.contains("1440") -> 1440
            lower.contains("1080") -> 1080
            lower.contains("720") -> 720
            lower.contains("480") -> 480
            lower.contains("360") -> 360
            else -> 0
        }
    }

    class SearchJson : ArrayList<SearchJsonItem>()

    data class SearchJsonItem(
        val id: String?, // 367
        val image: String?, // 2_guns.jpg
        val name: String?, // 2 Guns
        val type: String? // 1
    )
}
