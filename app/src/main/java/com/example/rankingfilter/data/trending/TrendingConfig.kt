package com.example.rankingfilter.data.trending

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cấu trúc file config.json trên server. */
@Serializable
data class TrendingConfig(
    @SerialName("config_version") val configVersion: Int = 1,
    /** Để trống thì đường dẫn tính từ thư mục chứa config.json. */
    @SerialName("media_base_url") val mediaBaseUrl: String = "",
    val trending: List<TrendingEntry> = emptyList(),
)

@Serializable
data class TrendingEntry(
    val id: String,
    /** Đường dẫn ảnh thumbnail (.jpg), tương đối hoặc tuyệt đối. */
    val thumb: String,
    /** Đường dẫn video (.mp4), tương đối hoặc tuyệt đối. */
    val video: String,
    /** Tùy chọn: tên filter hiện ở màn Preview ("Name the Filter" trong design). */
    val title: String? = null,
    /** Tùy chọn: id filter có sẵn, để nút "Try now" mở đúng filter. */
    @SerialName("filter_id") val filterId: String? = null,
)

/** Một ô trong lưới Trending, đã có URL đầy đủ. */
data class TrendingVideo(
    val id: String,
    val thumbUrl: String,
    /** null với dữ liệu dự phòng (ảnh avatar filter, không có video). */
    val videoUrl: String?,
    val title: String?,
    val filterId: String?,
)
