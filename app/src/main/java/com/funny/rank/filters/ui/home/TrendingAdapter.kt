package com.funny.rank.filters.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.funny.rank.filters.R
import com.funny.rank.filters.data.trending.TrendingVideo
import com.funny.rank.filters.databinding.ItemTrendingVideoBinding

class TrendingAdapter(
    private val onClick: (TrendingVideo) -> Unit,
) : ListAdapter<TrendingVideo, TrendingAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTrendingVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTrendingVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TrendingVideo) {
            // Coil tự cache ảnh trong bộ nhớ và trên đĩa.
            binding.thumb.load(item.thumbUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
            }
            binding.thumb.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TrendingVideo>() {
        override fun areItemsTheSame(oldItem: TrendingVideo, newItem: TrendingVideo) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TrendingVideo, newItem: TrendingVideo) = oldItem == newItem
    }
}
