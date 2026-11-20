package com.example.rankingfilter.ui.makefilter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.example.rankingfilter.R
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.databinding.ItemPhotoSlotBinding

/** Một ô trong lưới Make a Filter (10–12 ô). [index] là vị trí 0..11. */
sealed interface PhotoSlot {
    val index: Int

    data class Photo(override val index: Int, val uri: Uri, val selected: Boolean) : PhotoSlot
    data class Add(override val index: Int) : PhotoSlot
    data class Empty(override val index: Int) : PhotoSlot

    companion object {
        /**
         * Ảnh đã chọn, rồi ô "Add" (nếu chưa đủ 12), rồi các ô mờ có số cho tới ô thứ 10 (Figma 57:247 / 28:1638 /
         * 29:1796 / 29:1884): 0 ảnh = Add + ô 2..10; 10 ảnh = 10 ảnh + Add; 12 ảnh = 12 ảnh, không còn Add.
         */
        fun build(photos: List<Uri>, selected: Int?): List<PhotoSlot> = buildList {
            photos.forEachIndexed { i, uri -> add(Photo(i, uri, selected == i)) }
            if (photos.size < FilterDto.PHOTO_COUNT) add(Add(photos.size))
            for (i in size until FilterDto.MIN_PHOTO_COUNT) add(Empty(i))
        }
    }
}

class PhotoSlotAdapter(
    private val onPhotoClick: (index: Int) -> Unit,
    private val onRemoveClick: (index: Int) -> Unit,
    private val onAddClick: () -> Unit,
) : ListAdapter<PhotoSlot, PhotoSlotAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPhotoSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemPhotoSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(slot: PhotoSlot) = with(binding) {
            val context = root.context
            val isPhoto = slot is PhotoSlot.Photo
            ivPhoto.isVisible = isPhoto
            addContent.isVisible = slot is PhotoSlot.Add
            tvBadge.isVisible = slot is PhotoSlot.Empty
            btnRemove.isVisible = slot is PhotoSlot.Photo && slot.selected

            // Ô có ảnh: viền navy 1dp (hoặc xanh 4dp khi đang chọn) vẽ đè lên ảnh.
            cell.foreground = when {
                slot is PhotoSlot.Photo && slot.selected -> context.getDrawable(R.drawable.fg_photo_cell_selected)
                isPhoto -> context.getDrawable(R.drawable.fg_photo_cell)
                else -> null
            }

            cell.isClickable = true
            when (slot) {
                is PhotoSlot.Photo -> {
                    ivPhoto.load(slot.uri.toString()) { crossfade(true) }
                    cell.contentDescription = context.getString(R.string.cd_photo, slot.index + 1)
                    cell.setOnClickListener { onPhotoClick(slot.index) }
                    btnRemove.setOnClickListener { onRemoveClick(slot.index) }
                }
                is PhotoSlot.Add -> {
                    ivPhoto.setImageDrawable(null)
                    cell.contentDescription = context.getString(R.string.make_filter_add)
                    cell.setOnClickListener { onAddClick() }
                }
                is PhotoSlot.Empty -> {
                    ivPhoto.setImageDrawable(null)
                    tvBadge.text = (slot.index + 1).toString()
                    cell.contentDescription = context.getString(R.string.cd_photo_empty, slot.index + 1)
                    // Ô trống có số: không phản hồi chạm (chỉ ô "Add" và nút Add Photos mới mở dialog).
                    // Gỡ listener và tắt clickable vì ViewHolder được tái sử dụng giữa các loại ô.
                    cell.setOnClickListener(null)
                    cell.isClickable = false
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PhotoSlot>() {
        override fun areItemsTheSame(oldItem: PhotoSlot, newItem: PhotoSlot) = oldItem.index == newItem.index
        override fun areContentsTheSame(oldItem: PhotoSlot, newItem: PhotoSlot) = oldItem == newItem
    }
}
