package com.example.rankingfilter.data

import android.content.Context
import androidx.core.content.edit

/** Cờ nhỏ của app (SharedPreferences). Ngôn ngữ do AppCompatDelegate tự lưu. */
object AppPrefs {
    private const val FILE = "app_prefs"
    private const val KEY_INTRO_SEEN = "intro_seen"
    private const val KEY_RATED = "rated"
    private const val KEY_HOME_BACK_COUNT = "home_back_count"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isIntroSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_INTRO_SEEN, false)

    fun isRated(context: Context): Boolean = prefs(context).getBoolean(KEY_RATED, false)

    fun setRated(context: Context) = prefs(context).edit { putBoolean(KEY_RATED, true) }

    /** Tăng và trả về số lần bấm Back ở Home (giữ qua các lần mở app; dùng để hỏi Rate xen kẽ). */
    fun nextHomeBackCount(context: Context): Int {
        val next = prefs(context).getInt(KEY_HOME_BACK_COUNT, 0) + 1
        prefs(context).edit { putInt(KEY_HOME_BACK_COUNT, next) }
        return next
    }

    fun setIntroSeen(context: Context) = prefs(context).edit { putBoolean(KEY_INTRO_SEEN, true) }
}
