package com.example.rankingfilter.ui.common

import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.ViewBottomNavBinding

enum class BottomTab { HOME, GALLERY }

/**
 * Gắn trạng thái và sự kiện cho bottom nav tự vẽ (layout/view_bottom_nav.xml).
 * Dùng chung cho Home và Gallery (giai đoạn 7).
 */
fun ViewBottomNavBinding.setup(
    selected: BottomTab,
    onHome: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    val context = root.context
    val active = ContextCompat.getColor(context, R.color.primary)
    val inactive = ContextCompat.getColor(context, R.color.nav_inactive)
    val homeColor = if (selected == BottomTab.HOME) active else inactive
    val galleryColor = if (selected == BottomTab.GALLERY) active else inactive

    ImageViewCompat.setImageTintList(iconHome, ColorStateList.valueOf(homeColor))
    labelHome.setTextColor(homeColor)
    ImageViewCompat.setImageTintList(iconGallery, ColorStateList.valueOf(galleryColor))
    labelGallery.setTextColor(galleryColor)

    tabHome.isSelected = selected == BottomTab.HOME
    tabGallery.isSelected = selected == BottomTab.GALLERY
    tabHome.setOnClickListener { onHome() }
    tabGallery.setOnClickListener { onGallery() }
    btnCamera.setOnClickListener { onCamera() }

    // Thanh trắng kéo dài xuống dưới thanh điều hướng của hệ thống.
    navBar.applySystemBarsPadding(bottom = true)
}
