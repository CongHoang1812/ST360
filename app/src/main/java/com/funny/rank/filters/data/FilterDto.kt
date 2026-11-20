package com.funny.rank.filters.data

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

        /** Số ảnh tối đa của một filter (filter có sẵn luôn đủ 12). Camera có bấy nhiêu ô hạng. */
        const val PHOTO_COUNT = 12

        /** Filter tự tạo cần ít nhất 10 ảnh (Figma 57:247 "Minimum 10 photos"). */
        const val MIN_PHOTO_COUNT = 10

        /** Số ô hạng trên Camera cho filter có [photoCount] ảnh: 10..12. */
        fun slotCountFor(photoCount: Int): Int = photoCount.coerceIn(MIN_PHOTO_COUNT, PHOTO_COUNT)
    }
}
