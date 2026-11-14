package com.example.rankingfilter.data.trending

import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.assetUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class TrendingRepository(private val configUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = configUrl.isNotBlank()

    /**
     * Tải config.json và trả về danh sách video với URL đầy đủ.
     * Ném IOException khi lỗi mạng hoặc server trả mã khác 200,
     * SerializationException khi file JSON sai cấu trúc.
     */
    suspend fun fetchTrending(): List<TrendingVideo> = withContext(Dispatchers.IO) {
        val text = download(configUrl)
        val config = json.decodeFromString<TrendingConfig>(text)
        val base = config.mediaBaseUrl.ifBlank { configUrl.substringBeforeLast('/') + "/" }
            .let { if (it.endsWith("/")) it else "$it/" }
        val baseUri = URI(base)
        config.trending.map { entry ->
            TrendingVideo(
                id = entry.id,
                thumbUrl = baseUri.resolve(entry.thumb).toString(),
                videoUrl = baseUri.resolve(entry.video).toString(),
                title = entry.title,
                filterId = entry.filterId,
            )
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code khi tải $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** Dữ liệu dự phòng: mỗi filter có sẵn thành một ô, dùng ảnh avatar, không có video. */
        fun fallbackFrom(filters: List<FilterDto>): List<TrendingVideo> = filters.map {
            TrendingVideo(
                id = "filter_${it.id}",
                thumbUrl = assetUri(it.avatar),
                videoUrl = null,
                title = it.title,
                filterId = it.id,
            )
        }
    }
}
