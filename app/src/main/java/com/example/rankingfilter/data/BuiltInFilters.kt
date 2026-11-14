package com.example.rankingfilter.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

private const val TAG = "BuiltInFilters"
private const val FILTERS_JSON = "filters.json"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Đọc 30 filter có sẵn từ assets/filters.json.
 * Đọc file nên gọi ngoài main thread; thường dùng qua [BuiltInFilterRepository].
 * Trả về danh sách rỗng nếu chưa có file (chưa chạy export_figma_topics.py).
 */
fun loadBuiltInFilters(context: Context): List<FilterDto> {
    val text = try {
        context.assets.open(FILTERS_JSON).bufferedReader().use { it.readText() }
    } catch (e: FileNotFoundException) {
        Log.w(TAG, "Thiếu assets/$FILTERS_JSON, hãy chạy export_figma_topics.py", e)
        return emptyList()
    }
    val filters = json.decodeFromString<List<FilterDto>>(text)
    filters.filter { it.photos.size != FilterDto.PHOTO_COUNT }.forEach {
        Log.w(TAG, "Filter '${it.id}' có ${it.photos.size} ảnh, cần ${FilterDto.PHOTO_COUNT}")
    }
    return filters
}

/** Đọc filters.json một lần rồi giữ trong bộ nhớ cho cả app (Home, Choose a Filter, Camera…). */
object BuiltInFilterRepository {

    private val mutex = Mutex()
    @Volatile
    private var cache: List<FilterDto>? = null

    suspend fun getAll(context: Context): List<FilterDto> =
        cache ?: mutex.withLock {
            cache ?: withContext(Dispatchers.IO) { loadBuiltInFilters(context.applicationContext) }
                .also {
                    Log.d(TAG, "loadBuiltInFilters: ${it.size} filter")
                    // Không cache danh sách rỗng, để lần sau thử đọc lại.
                    if (it.isNotEmpty()) cache = it
                }
        }

    suspend fun findById(context: Context, id: String): FilterDto? = getAll(context).firstOrNull { it.id == id }
}

/** Đường dẫn để Coil tải ảnh trong assets. */
fun assetUri(path: String): String = "file:///android_asset/$path"
