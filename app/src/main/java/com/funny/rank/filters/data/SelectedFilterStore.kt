package com.funny.rank.filters.data

import android.content.Context
import androidx.core.content.edit

/**
 * Lưu filter người dùng chọn gần nhất (để Camera mở lại đúng filter,
 * kể cả khi app bị hệ thống đóng rồi mở lại).
 */
class SelectedFilterStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(filterId: String, isUserFilter: Boolean) = prefs.edit {
        putString(KEY_ID, filterId)
        putBoolean(KEY_IS_USER, isUserFilter)
    }

    val lastFilterId: String? get() = prefs.getString(KEY_ID, null)
    val lastIsUserFilter: Boolean get() = prefs.getBoolean(KEY_IS_USER, false)

    private companion object {
        const val PREFS = "selected_filter"
        const val KEY_ID = "filter_id"
        const val KEY_IS_USER = "is_user_filter"
    }
}
