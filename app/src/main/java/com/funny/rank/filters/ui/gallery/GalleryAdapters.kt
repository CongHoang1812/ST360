package com.funny.rank.filters.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import com.funny.rank.filters.R
import com.funny.rank.filters.databinding.ItemCreateFilterTileBinding
import com.funny.rank.filters.databinding.ItemGalleryVideoBinding
import java.io.File

/**
 * Lưới My Videos (Figma 21:281): ô 156 x 270 bo 8dp viền trắng, ảnh thu nhỏ lấy từ khung hình ở giây 1
 * (coil-video, có cache), nút xóa tròn hồng ở góc phải trên.
 */
class GalleryVideoAdapter(
    private val onOpen: (File) -> Unit,
    private val onDelete: (File) -> Unit,
) : ListAdapter<File, GalleryVideoAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGalleryVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemGalleryVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.ivThumbnail.load(file) {
                decoderFactory(VideoFrameDecoder.Factory())
                // Giây 0 thường còn tối vì camera đang tự chỉnh sáng.
                videoFrameMillis(THUMBNAIL_FRAME_MS)
                crossfade(true)
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
            }
            // Card là View nhận chạm (clickable trong layout), không phải root.
            binding.card.setOnClickListener { onOpen(file) }
            binding.btnDelete.setOnClickListener { onDelete(file) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: File, newItem: File) =
            oldItem.path == newItem.path && oldItem.lastModified() == newItem.lastModified()
    }

    private companion object {
        const val THUMBNAIL_FRAME_MS = 1_000L
    }
}

/** Ô "Create Filter" vàng đứng đầu lưới My Filters (Figma 30:2208). */
class CreateFilterTileAdapter(
    private val onClick: () -> Unit,
) : RecyclerView.Adapter<CreateFilterTileAdapter.Holder>() {

    /** Ẩn ô khi danh sách trống (lúc đó hiện trạng thái trống có nút Create Filter riêng). */
    var visible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun getItemCount() = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCreateFilterTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        // Ô vuông bên trong là view clickable nên nó nhận chạm; gắn vào root thì không bao giờ được gọi.
        holder.binding.tile.setOnClickListener { onClick() }
    }

    class Holder(val binding: ItemCreateFilterTileBinding) : RecyclerView.ViewHolder(binding.root)
}
