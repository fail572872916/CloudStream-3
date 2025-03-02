package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.network.url
import com.lagradost.cloudstream3.pmap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup

/**
 * overrideMainUrl is necessary for for other vidstream clones like vidembed.cc
 * If they diverge it'd be better to make them separate.
 * */
class Vidstream(val mainUrl: String) {
    val name: String = "Vidstream"

    private fun getExtractorUrl(id: String): String {
        return "$mainUrl/streaming.php?id=$id"
    }

    private fun getDownloadUrl(id: String): String {
        return "$mainUrl/download?id=$id"
    }

    private val normalApis = arrayListOf(MultiQuality())

    // https://gogo-stream.com/streaming.php?id=MTE3NDg5
    fun getUrl(id: String, isCasting: Boolean = false, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            normalApis.pmap { api ->
                val url = api.getExtractorUrl(id)
                val source = api.getSafeUrl(url)
                source?.forEach { callback.invoke(it) }
            }
            val extractorUrl = getExtractorUrl(id)

            /** Stolen from GogoanimeProvider.kt extractor */
            normalSafeApiCall {
                val link = getDownloadUrl(id)
                println("Generated vidstream download link: $link")
                val page = app.get(link, referer = extractorUrl)

                val pageDoc = Jsoup.parse(page.text)
                val qualityRegex = Regex("(\\d+)P")

                pageDoc.select(".dowload > a[download]").forEach {
                    val qual = if (it.text()
                            .contains("HDP")
                    ) "1080" else qualityRegex.find(it.text())?.destructured?.component1().toString()

                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            if (qual == "null") this.name else "${this.name} - " + qual + "p",
                            it.attr("href"),
                            page.url,
                            getQualityFromName(qual),
                            it.attr("href").contains(".m3u8")
                        )
                    )
                }
            }

            with(app.get(extractorUrl)) {
                val document = Jsoup.parse(this.text)
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")
                //val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()

                // All vidstream links passed to extractors
                primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                    val link = element.attr("data-video")
                    //val name = element.text()

                    // Matches vidstream links with extractors
                    extractorApis.filter { !it.requiresReferer || !isCasting }.pmap { api ->
                        if (link.startsWith(api.mainUrl)) {
                            val extractedLinks = api.getSafeUrl(link, extractorUrl)
                            if (extractedLinks?.isNotEmpty() == true) {
                                extractedLinks.forEach {
                                    callback.invoke(it)
                                }
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }
}