package com.example.rankingfilter.ui.sound

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.data.music.MusicRepository
import com.example.rankingfilter.databinding.FragmentAddSoundBinding
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.example.rankingfilter.video.VideoCache
import kotlinx.coroutines.launch

/**
 * Add Sound (Figma 14:620 chưa chọn, 22:738 đã chọn).
 * Chạm một bài để chọn và nghe thử (chỉ một bài phát một lúc), chạm lại để tạm dừng.
 * Nút tick xám cho đến khi đã chọn; bấm tick thì tải bài về máy rồi trả id về Camera qua
 * `savedStateHandle[RESULT_MUSIC_ID]` của màn trước. Bấm Back thì giữ nguyên lựa chọn cũ.
 */
class AddSoundFragment : Fragment() {

    private var _binding: FragmentAddSoundBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddSoundViewModel by viewModels()
    private lateinit var adapter: MusicAdapter

    /** Nghe thử: tạo ở onStart, giải phóng ở onStop (rời màn là dừng nhạc). */
    private var player: ExoPlayer? = null

    /** Bài đang nạp vào [player]. */
    private var loadedId: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddSoundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.applySystemBarsPadding(top = true)
        binding.rvMusic.applySystemBarsPadding(bottom = true)

        adapter = MusicAdapter(onTap = { onTrackTapped(it) }, onNeedDuration = viewModel::requestDuration)
        binding.rvMusic.adapter = adapter
        binding.rvMusic.setHasFixedSize(true)
        // Dòng chỉ đổi nền / icon: tắt hiệu ứng nháy của DefaultItemAnimator.
        binding.rvMusic.itemAnimator = null

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirm.setOnClickListener { viewModel.confirm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player = VideoCache.buildPlayer(requireContext()).also { it.addListener(playerListener) }
        loadedId = null
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        loadedId = null
        viewModel.setPlaying(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(ui: AddSoundUi) {
        adapter.submitList(ui.rows)
        // Nút tick: xám (không bấm được) cho đến khi đã chọn một bài.
        binding.btnConfirm.isEnabled = ui.selectedId != null && !ui.isDownloading
        binding.busyOverlay.isVisible = ui.isDownloading
    }

    private fun onEvent(event: AddSoundEvent) {
        when (event) {
            is AddSoundEvent.Confirmed -> {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_MUSIC_ID, event.id)
                findNavController().navigateUp()
            }
            is AddSoundEvent.Message -> Toast.makeText(requireContext(), event.textRes, Toast.LENGTH_SHORT).show()
        }
    }

    /** Chạm một bài: bài đang phát thì tạm dừng; bài đang tạm dừng thì phát tiếp; bài khác thì chọn và phát từ đầu. */
    private fun onTrackTapped(row: MusicRow) {
        val p = player ?: return
        val id = row.track.id
        viewModel.select(id)
        when {
            loadedId == id && p.playWhenReady && p.playbackState != Player.STATE_ENDED -> p.pause()
            loadedId == id && p.playbackState != Player.STATE_IDLE -> {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
            else -> {
                loadedId = id
                // Đã tải về máy (từng chọn) thì phát file, không thì phát từ server qua cache đĩa của ExoPlayer.
                val local = MusicRepository.cachedFile(requireContext(), row.track)
                val item = if (local.exists()) MediaItem.fromUri(android.net.Uri.fromFile(local)) else MediaItem.fromUri(row.track.url)
                p.setMediaItem(item)
                p.prepare()
                p.play()
            }
        }
        updatePlaying()
    }

    private fun updatePlaying() {
        val p = player ?: return
        val active = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
        viewModel.setPlaying(if (active) loadedId else null)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updatePlaying()

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaying()
            val p = player ?: return
            val id = loadedId ?: return
            if (playbackState == Player.STATE_READY && p.duration > 0) viewModel.rememberDuration(id, p.duration)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Mất mạng hoặc file trên server không còn: báo nhẹ, không crash, cho chọn bài khác.
            loadedId = null
            viewModel.setPlaying(null)
            if (_binding != null) {
                Toast.makeText(requireContext(), R.string.add_sound_play_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /** Khóa kết quả trong savedStateHandle của màn trước (Camera): id bài nhạc đã chọn. */
        const val RESULT_MUSIC_ID = "selected_music_id_result"
    }
}
