package com.funny.rank.filters.data.trending

/** Một ô trong lưới Trending, đã có URL đầy đủ. */
data class TrendingVideo(
    /** Nguyên chuỗi trong folders.json (có số), vì tên chủ đề bị lặp. */
    val id: String,
    val thumbUrl: String,
    /** null với dữ liệu dự phòng (ảnh avatar filter, không có video). */
    val videoUrl: String?,
    val title: String?,
    /** Filter có sẵn cùng chủ đề, để nút "Try now" mở đúng filter; null nếu không khớp. */
    val filterId: String?,
)
