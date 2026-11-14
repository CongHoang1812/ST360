package com.example.rankingfilter.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.data.video.VideoLibrary
import com.example.rankingfilter.databinding.FragmentVideoViewerBinding
import com.example.rankingfilter.ui.common.ConfirmDialogFragment
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Xem một video trong Gallery (Figma 8:278 đang phát, 21:368 đang dừng).
 * Chạm video để dừng / phát; khi dừng hiện nút phát ở giữa và thanh thời gian kéo được.
 * Share (bảng chia sẻ của Android), Download (chép ra thư viện ảnh), nút xóa (hỏi trước).
 */
class VideoViewerFragment : Fragment() {

    private var _binding: FragmentVideoViewerBinding? = null
    private val binding get() = _binding!!

    private val video: File? by lazy { arguments?.getString(ARG_VIDEO_PATH)?.let(::File)?.takeIf(File::exists) }

    private var player: ExoPlayer? = null
    private var resumePositionMs = 0L
    private var playWhenStarted = true
    private var userSeeking = false
    private var downloading = false

    /** Android 9 trở xuống: ghi vào thư viện ảnh cần quyền bộ nhớ. */
    private val requestStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) download() else toast(R.string.success_storage_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumePositionMs = savedInstanceState?.getLong(KEY_POSITION) ?: 0L
        playWhenStarted = savedInstanceState?.getBoolean(KEY_PLAYING) ?: true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)
        if (video == null) {
            toast(R.string.preview_draft_missing)
            findNavController().navigateUp()
            return
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnShare.setOnClickListener { share() }
        binding.btnDownload.setOnClickListener { onDownloadClicked() }
        binding.touchLayer.setOnClickListener { togglePlayback() }
        binding.seekBar.setOnSeekBarChangeListener(seekListener)
        ConfirmDialogFragment.listen(this, REQUEST_DELETE) { delete() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateTime()
                    delay(TIME_UPDATE_MS)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val file = video ?: return
        player = ExoPlayer.Builder(requireContext()).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            seekTo(resumePositionMs)
            playWhenReady = playWhenStarted
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateControls()
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateControls()
            })
            prepare()
        }
        binding.playerView.player = player
        updateControls()
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            resumePositionMs = it.currentPosition
            playWhenStarted = it.playWhenReady
            it.release()
        }
        player = null
        _binding?.playerView?.player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_POSITION, player?.currentPosition ?: resumePositionMs)
        outState.putBoolean(KEY_PLAYING, player?.playWhenReady ?: playWhenStarted)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------------------------------------------------------------- Phát

    private fun togglePlayback() {
        val p = player ?: return
        if (p.playWhenReady) p.pause() else p.play()
    }

    /** Đang dừng thì hiện nút phát và thanh thời gian (Figma 21:368); đang phát thì ẩn (8:278). */
    private fun updateControls() {
        val paused = player?.playWhenReady != true
        val b = _binding ?: return
        b.ivPlay.isVisible = paused
        b.timeBar.isVisible = paused
        updateTime()
    }

    private fun updateTime() {
        val b = _binding ?: return
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        val position = p.currentPosition.coerceIn(0, duration)
        b.tvTime.text = getString(R.string.viewer_time, formatTime(position), formatTime(duration))
        if (!userSeeking) {
            b.seekBar.max = duration.toInt()
            b.seekBar.progress = position.toInt()
        }
    }

    private val seekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) player?.seekTo(progress.toLong())
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userSeeking = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userSeeking = false
        }
    }

    // ---------------------------------------------------------------- Nút

    private fun share() {
        val file = video ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, VideoLibrary.shareUri(requireContext(), file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.success_share_title)))
    }

    private fun onDownloadClicked() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else download()
    }

    private fun download() {
        val file = video ?: return
        if (downloading) return
        downloading = true
        binding.btnDownload.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                VideoLibrary.exportToGallery(requireContext(), file)
                toast(R.string.success_downloaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chép video ra thư viện ảnh lỗi", e)
                toast(R.string.success_download_error)
            } finally {
                downloading = false
                _binding?.btnDownload?.isEnabled = true
            }
        }
    }

    private fun confirmDelete() {
        ConfirmDialogFragment.show(
            host = this,
            requestKey = REQUEST_DELETE,
            title = R.string.delete_video_title,
            message = R.string.delete_video_message,
            negative = R.string.cancel,
            positive = R.string.delete,
            style = ConfirmDialogFragment.Style.DESTRUCTIVE,
        )
    }

    private fun delete() {
        val file = video ?: return
        player?.release()
        player = null
        VideoLibrary.deleteSaved(file)
        findNavController().navigateUp()
    }

    private fun toast(textRes: Int) = Toast.makeText(requireContext(), textRes, Toast.LENGTH_SHORT).show()

    companion object {
        const val ARG_VIDEO_PATH = "video_path"
        private const val TAG = "VideoViewerFragment"
        private const val KEY_POSITION = "position"
        private const val KEY_PLAYING = "playing"
        private const val REQUEST_DELETE = "viewer_delete_video"
        private const val TIME_UPDATE_MS = 250L

        /** 12000 → "00:12" (Figma 57:216: "00:12/00:50"). */
        private fun formatTime(ms: Long): String {
            val s = ms / 1000
            return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
        }
    }
}
