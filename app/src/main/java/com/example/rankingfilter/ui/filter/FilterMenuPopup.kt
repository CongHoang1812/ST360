package com.example.rankingfilter.ui.filter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.example.rankingfilter.databinding.PopupFilterMenuBinding

/**
 * Menu Edit / Delete khi bấm nút "…" trên ô filter tự tạo (Figma 22:557).
 * Design: menu đặt tại (25, 43) trong ô 156dp, nút "…" tại (119, 7) cỡ 28dp
 * → lệch so với nút: x = 25 - 119 = -94dp, y = 43 - (7 + 28) = 8dp dưới đáy nút.
 * Dùng lại ở Gallery → My Filters (giai đoạn 7).
 */
object FilterMenuPopup {

    private const val OFFSET_X_DP = -94
    private const val OFFSET_Y_DP = 8

    fun show(anchor: View, onEdit: () -> Unit, onDelete: () -> Unit) {
        val binding = PopupFilterMenuBinding.inflate(LayoutInflater.from(anchor.context))
        val popup = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
        }
        binding.menuEdit.setOnClickListener { popup.dismiss(); onEdit() }
        binding.menuDelete.setOnClickListener { popup.dismiss(); onDelete() }

        val density = anchor.resources.displayMetrics.density
        popup.showAsDropDown(anchor, (OFFSET_X_DP * density).toInt(), (OFFSET_Y_DP * density).toInt())
    }
}
