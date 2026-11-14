package com.example.rankingfilter.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

private const val TAG = "BuiltInFilters"
private const val FILTERS_JSON = "filters.json"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Đọc 30 filter có sẵn từ assets/filters.json.
 * Đọc file nên gọi ngoài main thread (ví dụ trong Dispatchers.IO).
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
