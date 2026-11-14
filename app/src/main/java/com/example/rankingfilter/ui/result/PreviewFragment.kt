package com.example.rankingfilter.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentPreviewBinding
import com.example.rankingfilter.ui.common.ConfirmDialogFragment
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import kotlinx.coroutines.launch
import java.io.File

/**
 * Preview (Figma 8:334): phát lặp video vừa quay. Nếu đã chọn nhạc thì tắt tiếng gốc và phát nhạc kèm theo
 * (đúng như video sẽ được lưu). Save → trộn nhạc vào video (Media3 Transformer) và lưu vào thư viện của app →
 * màn Successful. Nút vòng tròn bên trái = bỏ video, quay lại Camera làm lại. Nút ngôi nhà / Back = hỏi trước khi bỏ.
 */
class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PreviewViewModel by viewModels()

    private var videoPlayer: ExoPlayer? = null
    private var musicPlayer: ExoPlayer? = null

    /** Rời màn mà chưa lưu thì hỏi trước (video sẽ bị bỏ). */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = askLeave(REQUEST_LEAVE_BACK)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.btnRetake.setOnClickListener { retake() }
        binding.btnHome.setOnClickListener { askLeave(REQUEST_LEAVE_HOME) }
        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.playerView.setOnClickListener { togglePlayback() }

        ConfirmDialogFragment.listen(this, REQUEST_LEAVE_BACK) { retake() }
        ConfirmDialogFragment.listen(this, REQUEST_LEAVE_HOME) { goHome() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startPlayers(viewModel.state.value)
    }

    override fun onStop() {
        super.onStop()
        releasePlayers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(ui: PreviewUi) {
        binding.busyOverlay.isVisible = ui.isSaving
        binding.tvSaving.text = ui.saveProgress?.let { getString(R.string.preview_saving_progress, it) }
            ?: getString(R.string.preview_saving)
        binding.btnSave.isEnabled = !ui.isSaving && !ui.isMusicLoading
        binding.btnSave.alpha = if (binding.btnSave.isEnabled) 1f else DISABLED_ALPHA
        // Nhạc vừa tải xong khi màn đang hiện: phát lại từ đầu cho khớp.
        if (ui.music != null && musicPlayer == null && videoPlayer != null) startPlayers(ui)
    }

    private fun onEvent(event: PreviewEvent) {
        when (event) {
            is PreviewEvent.Saved -> {
                backCallback.isEnabled = false
                findNavController().navigate(
                    R.id.action_preview_to_success,
                    bundleOf(SuccessViewModel.ARG_VIDEO_PATH to event.file.absolutePath),
                )
            }
            is PreviewEvent.Message -> Toast.makeText(requireContext(), event.textRes, Toast.LENGTH_SHORT).show()
            PreviewEvent.DraftMissing -> {
                Toast.makeText(requireContext(), R.string.preview_draft_missing, Toast.LENGTH_SHORT).show()
                leaveTo(R.id.cameraFragment)
            }
        }
    }

    // ---------------------------------------------------------------- Phát video + nhạc

    /** Video lặp lại; có nhạc thì tắt tiếng video và phát nhạc từ đầu mỗi vòng, cho giống video sẽ lưu. */
    @OptIn(UnstableApi::class)
    private fun startPlayers(ui: PreviewUi) {
        releasePlayers()
        val draft = ui.draft?.takeIf(File::exists) ?: return
        val context = requireContext()
        val music = ui.music
        musicPlayer = music?.let {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(Uri.fromFile(it)))
                prepare()
            }
        }
        videoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (music != null) 0f else 1f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(draft)))
            addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    // Video quay lại từ đầu: nhạc cũng về đầu (video lưu ra cũng bắt đầu nhạc từ giây 0).
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) musicPlayer?.seekTo(0)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    musicPlayer?.playWhenReady = isPlaying
                }
            })
            prepare()
            play()
        }
        binding.playerView.player = videoPlayer
    }

    private fun togglePlayback() {
        val p = videoPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun releasePlayers() {
        _binding?.playerView?.player = null
        videoPlayer?.release()
        videoPlayer = null
        musicPlayer?.release()
        musicPlayer = null
    }

    // ---------------------------------------------------------------- Điều hướng

    private fun askLeave(requestKey: String) {
        if (viewModel.state.value.isSaving) return
        ConfirmDialogFragment.show(
            host = this,
            requestKey = requestKey,
            title = R.string.leave_title,
            message = R.string.preview_leave_message,
            negative = R.string.leave_keep_editing,
            positive = R.string.leave_confirm,
        )
    }

    /** Bỏ video vừa quay, về Camera quay lại (cùng filter, cùng nhạc). */
    private fun retake() {
        if (viewModel.state.value.isSaving) return
        viewModel.discard()
        leaveTo(R.id.cameraFragment)
    }

    private fun goHome() {
        viewModel.discard()
        leaveTo(R.id.homeFragment)
    }

    private fun leaveTo(destination: Int) {
        backCallback.isEnabled = false
        val nav = findNavController()
        if (!nav.popBackStack(destination, false)) nav.navigateUp()
    }

    private companion object {
        const val REQUEST_LEAVE_BACK = "preview_leave_back"
        const val REQUEST_LEAVE_HOME = "preview_leave_home"
        const val DISABLED_ALPHA = 0.5f
    }
}
