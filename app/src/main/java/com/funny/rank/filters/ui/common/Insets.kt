package com.funny.rank.filters.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Cộng thêm chiều cao thanh trạng thái (top) và/hoặc thanh điều hướng (bottom)
 * vào padding ban đầu của view. Dùng vì app vẽ tràn viền (enableEdgeToEdge).
 */
fun View.applySystemBarsPadding(top: Boolean = false, bottom: Boolean = false) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.updatePadding(
            top = if (top) initialTop + bars.top else initialTop,
            bottom = if (bottom) initialBottom + bars.bottom else initialBottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
