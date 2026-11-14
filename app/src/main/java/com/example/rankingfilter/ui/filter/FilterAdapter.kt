package com.example.rankingfilter.ui.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.ItemFilterBinding

class FilterAdapter(
    private val onClick: (FilterCardItem) -> Unit,
) : ListAdapter<FilterCardItem, FilterAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

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
        }
    }

    private object Diff : DiffUtil.ItemCallback<FilterCardItem>() {
        override fun areItemsTheSame(oldItem: FilterCardItem, newItem: FilterCardItem) =
            oldItem.id == newItem.id && oldItem.isUserFilter == newItem.isUserFilter

        override fun areContentsTheSame(oldItem: FilterCardItem, newItem: FilterCardItem) = oldItem == newItem
    }
}
