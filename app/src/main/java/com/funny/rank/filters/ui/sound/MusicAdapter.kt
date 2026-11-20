package com.funny.rank.filters.ui.sound

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.funny.rank.filters.R
import com.funny.rank.filters.databinding.ItemMusicBinding
import java.util.Locale

/**
 * Danh sách 74 bài nhạc (Figma 14:620 / 22:738). Mọi bài dùng chung ảnh bìa `music_cover`.
 * Dòng đang chọn nền xanh nhạt, nút bên phải là phát / tạm dừng.
 */
class MusicAdapter(
    /** Chạm cả dòng hoặc nút phát: chọn bài và nghe thử / tạm dừng. */
    private val onTap: (MusicRow) -> Unit,
    /** Dòng hiện ra mà chưa có thời lượng. */
    private val onNeedDuration: (Int) -> Unit,
) : ListAdapter<MusicRow, MusicAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MusicRow) = with(binding) {
            val context = root.context
            tvTitle.text = row.track.title
            // Bài đang nghe thử: thời gian đã phát đếm từ 00:00; bài khác: tổng thời lượng.
            tvDuration.text = row.positionMs?.let(::formatElapsed)
                ?: row.durationMs?.let(::formatDuration)
                ?: context.getString(R.string.add_sound_duration_unknown)
            if (row.durationMs == null) onNeedDuration(row.track.id)

            root.setBackgroundColor(
                ContextCompat.getColor(context, if (row.selected) R.color.music_selected else R.color.white)
            )
            btnPlay.setImageResource(if (row.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play)
            btnPlay.contentDescription = context.getString(
                if (row.playing) R.string.cd_pause_music else R.string.cd_play_music, row.track.title,
            )
            root.isSelected = row.selected
            root.setOnClickListener { onTap(row) }
            btnPlay.setOnClickListener { onTap(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MusicRow>() {
        override fun areItemsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem.track.id == newItem.track.id
        override fun areContentsTheSame(oldItem: MusicRow, newItem: MusicRow) = oldItem == newItem
    }

    companion object {
        /** Thời gian đã phát, làm tròn xuống (bắt đầu đúng 00:00): 33900 → "00:33". */
        fun formatElapsed(ms: Long): String {
            val totalSeconds = ms.coerceAtLeast(0) / 1000
            return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }

        /** 33000 → "00:33". */
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms + 500) / 1000
            return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
    }
}
