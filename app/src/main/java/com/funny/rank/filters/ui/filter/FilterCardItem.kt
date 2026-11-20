package com.funny.rank.filters.ui.filter

/**
 * Một ô trong lưới filter. Dùng chung cho filter có sẵn (assets) và filter tự tạo (Room, giai đoạn 4).
 */
data class FilterCardItem(
    val id: String,
    val title: String,
    /** URI ảnh bìa cho Coil: file:///android_asset/... hoặc đường dẫn file trong filesDir. */
    val coverUri: String,
    val isUserFilter: Boolean,
)

/** Chế độ của màn ChooseFilterFragment, truyền qua argument "mode" trong nav_graph. */
enum class FilterMode {
    /** "Choose a Filter": hai tab Discover (30 filter) và My Filters. */
    ALL,
    /** "New filter": không có tab, chỉ filter có pack == "New Filter". */
    NEW_PACK,
}

enum class FilterTab { DISCOVER, MY_FILTERS }
