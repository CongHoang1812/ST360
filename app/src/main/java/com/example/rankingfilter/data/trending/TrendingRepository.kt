package com.example.rankingfilter.data.trending

import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.assetUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TrendingRepository(private val foldersUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tải folders.json và trả về danh sách video, giữ đúng thứ tự và giữ cả chủ đề lặp lại.
     * [filters] dùng để tìm filterId cho nút "Try now".
     * Ném IOException khi lỗi mạng hoặc server trả mã khác 200,
     * SerializationException khi file JSON sai cấu trúc.
     */
    suspend fun fetchTrending(filters: List<FilterDto>): List<TrendingVideo> = withContext(Dispatchers.IO) {
        val folders = json.decodeFromString<List<String>>(download(foldersUrl))
        folders.filter { it.isNotBlank() }.map { folder ->
            TrendingVideo(
                id = folder,
                thumbUrl = TrendingApi.thumbUrl(folder),
                videoUrl = TrendingApi.videoUrl(folder),
                title = TrendingApi.displayTitle(folder),
                filterId = TrendingApi.matchFilterId(folder, filters),
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
