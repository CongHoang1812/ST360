package com.example.rankingfilter.data

import android.content.Context
import androidx.core.content.edit

/** Cờ nhỏ của app (SharedPreferences). Ngôn ngữ do AppCompatDelegate tự lưu. */
object AppPrefs {
    private const val FILE = "app_prefs"
    private const val KEY_INTRO_SEEN = "intro_seen"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isIntroSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_INTRO_SEEN, false)

    fun setIntroSeen(context: Context) = prefs(context).edit { putBoolean(KEY_INTRO_SEEN, true) }
}
