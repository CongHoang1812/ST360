package com.funny.rank.filters.ui.filter

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.funny.rank.filters.R
import com.funny.rank.filters.databinding.ItemFilterBinding

class FilterAdapter(
    private val onClick: (FilterCardItem) -> Unit,
    /** Bấm nút "…" trên filter tự tạo (null = không hiện nút). [anchor] là chính nút đó. */
    private val onMoreClick: ((item: FilterCardItem, anchor: View) -> Unit)? = null,
    /** Chiều rộng cố định của ô (danh sách cuộn ngang, ví dụ "Try More Filters"); null = theo lưới. */
    private val itemWidthPx: Int? = null,
) : ListAdapter<FilterCardItem, FilterAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        itemWidthPx?.let { width -> binding.root.layoutParams = binding.root.layoutParams.apply { this.width = width } }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FilterCardItem) {
            binding.name.text = item.title
            binding.root.contentDescription = item.title
            binding.cover.load(item.coverUri) {
                crossfade(true)
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
            }
            binding.root.setOnClickListener { onClick(item) }
            binding.btnMore.isVisible = item.isUserFilter && onMoreClick != null
            binding.btnMore.setOnClickListener { onMoreClick?.invoke(item, it) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<FilterCardItem>() {
        override fun areItemsTheSame(oldItem: FilterCardItem, newItem: FilterCardItem) =
            oldItem.id == newItem.id && oldItem.isUserFilter == newItem.isUserFilter

        override fun areContentsTheSame(oldItem: FilterCardItem, newItem: FilterCardItem) = oldItem == newItem
    }
}
