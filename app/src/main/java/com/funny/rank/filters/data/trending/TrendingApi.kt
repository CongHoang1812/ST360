package com.funny.rank.filters.data.trending

import android.net.Uri
import com.funny.rank.filters.data.FilterDto

/**
 * Server video Trending (ST316): chỉ có file tĩnh, không có config.json.
 * - BASE/folders.json: mảng tên thư mục, dạng "N. Tên chủ đề".
 * - BASE/<thư mục>/avatar.png: ảnh bìa 560x1000.
 * - BASE/<thư mục>/<thư mục>.mp4: video.
 */
object TrendingApi {
    const val BASE =
        "https://lvtglobal.site/public/app/ST316_RankingFilterFunChallenge_0783/VideosTrending/"
    const val FOLDERS_URL = BASE + "folders.json"

    /** folders.json ghi ' và ..., thư mục thật dùng ’ và …. Không đổi thì 10/30 thư mục báo 404. */
    private fun serverName(folder: String) =
        folder.trim().replace("'", "’").replace("...", "…")

    /** Uri.encode: khoảng trắng thành %20 (URLEncoder sẽ ra dấu +, server báo 404). */
    private fun segment(folder: String) = Uri.encode(serverName(folder))

    fun thumbUrl(folder: String) = BASE + segment(folder) + "/avatar.png"

    fun videoUrl(folder: String): String {
        val s = segment(folder)
        return "$BASE$s/$s.mp4"
    }

    /** "12. McDonald's menu" -> "McDonald's menu" */
    fun displayTitle(folder: String) = folder.substringAfter(". ", folder).trim()

    /** Tìm filter có sẵn cùng chủ đề, ví dụ "Which Ice Cream Is" khớp "Which Ice Cream Is #1". */
    fun matchFilterId(folder: String, filters: List<FilterDto>): String? {
        fun norm(s: String) =
            s.replace('’', '\'').replace("…", "...").trim().lowercase()
        val topic = norm(displayTitle(folder))
        if (topic.isEmpty()) return null
        return filters.firstOrNull { norm(it.title) == topic }?.id
            ?: filters.firstOrNull { norm(it.title).startsWith(topic) }?.id
    }
}
