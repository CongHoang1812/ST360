package com.example.rankingfilter.data

import kotlinx.serialization.Serializable

/**
 * Một filter có sẵn, đọc từ assets/filters.json.
 * [avatar] và [photos] là đường dẫn tương đối trong assets,
 * hiển thị bằng Coil: imageView.load("file:///android_asset/${filter.avatar}")
 */
@Serializable
data class FilterDto(
    val id: String,
    val title: String,
    val pack: String,
    val avatar: String,
    val photos: List<String>,
) {
    companion object {
        /** Tên pack của 6 chủ đề hiện ở banner "Explore pack" trên Home. */
        const val PACK_NEW = "New Filter"

        /** Mỗi filter có đúng 12 ảnh, ứng với 12 ô hạng trên Camera. */
        const val PHOTO_COUNT = 12
    }
}
