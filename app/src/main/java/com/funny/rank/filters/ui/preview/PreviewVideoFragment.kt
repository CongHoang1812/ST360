package com.funny.rank.filters.ui.preview

import com.funny.rank.filters.ui.common.navigateSafe
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import com.funny.rank.filters.R
import com.funny.rank.filters.data.SelectedFilterStore
import com.funny.rank.filters.data.trending.TrendingVideo
import com.funny.rank.filters.databinding.FragmentPreviewVideoBinding
import com.funny.rank.filters.ui.common.applySystemBarsPadding
import com.funny.rank.filters.ui.filter.ChooseFilterFragment
import com.funny.rank.filters.video.VideoCache

/**
 * Màn Preview video (Figma node 25:562): phát video Trending toàn màn, lặp lại,
 * nút Back, tên filter và nút "Try now" mở màn chọn filter.
 */
class PreviewVideoFragment : Fragment() {

    private var _binding: FragmentPreviewVideoBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    /** Vị trí phát, giữ lại khi app vào nền rồi quay lại. */
    private var resumePositionMs = 0L

    private val videoUrl get() = arguments?.getString(ARG_VIDEO_URL)
    private val filterId get() = arguments?.getString(ARG_FILTER_ID)

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            _binding?.thumb?.isVisible = false
        }

        override fun onPlayerError(error: PlaybackException) {
            _binding?.let {
                it.thumb.isVisible = true
                it.errorText.isVisible = true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resumePositionMs = savedInstanceState?.getLong(STATE_POSITION) ?: 0L
        binding.overlay.applySystemBarsPadding(top = true, bottom = true)

        val args = requireArguments()
        binding.thumb.load(args.getString(ARG_THUMB_URL)) { crossfade(true) }
        val title = args.getString(ARG_TITLE)
        binding.title.text = title
        binding.title.isVisible = !title.isNullOrBlank()

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnTryNow.setOnClickListener {
            val id = filterId
            if (id != null) {
                // Video gắn với một filter có sẵn: lưu lựa chọn rồi mở thẳng Camera.
                SelectedFilterStore(requireContext()).save(id, isUserFilter = false)
                findNavController().navigateSafe(
                    R.id.action_preview_to_camera,
                    bundleOf(ARG_FILTER_ID to id, ChooseFilterFragment.ARG_IS_USER_FILTER to false),
                )
            } else {
                findNavController().navigateSafe(R.id.action_preview_to_chooseFilter)
            }
        }
    }

    // Trong ViewPager2, trang bên cạnh ở STARTED còn trang đang hiện ở RESUMED. Nên player được tạo và
    // prepare() ngay ở STARTED (đã có khung hình đầu, chưa phát) → vuốt tới chỉ cần bấm play.
    override fun onStart() {
        super.onStart()
        val url = videoUrl ?: return // Dữ liệu dự phòng: chỉ có ảnh, không có video.
        binding.errorText.isVisible = false
        player = VideoCache.buildPlayer(requireContext()).also {
            it.addListener(playerListener)
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.setMediaItem(MediaItem.fromUri(url))
            it.seekTo(resumePositionMs)
            it.playWhenReady = false
            it.prepare()
        }
        binding.playerView.player = player
    }

    override fun onResume() {
        super.onResume()
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
            it.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        // Rời màn (Back / vuốt sang trang khác): chỉ dừng, giữ khung hình cuối cho animation chuyển màn,
        // release ở onDestroyView (sau animation). release() chặn luồng chính tới vài trăm ms, mà pager giữ
        // 2–3 player cùng lúc → nếu release ở đây thì animation Back về Home bị giật và đen màn.
        // Xuống nền thì release ngay để trả decoder.
        if (isLeaving()) player?.playWhenReady = false else releasePlayer()
    }

    /** Fragment này (hoặc PreviewPagerFragment chứa nó) đang bị gỡ khỏi màn hình. */
    private fun isLeaving(): Boolean {
        var f: Fragment? = this
        while (f != null) {
            if (f.isRemoving) return true
            f = f.parentFragment
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_POSITION, player?.currentPosition ?: resumePositionMs)
    }

    private fun releasePlayer() {
        player?.let {
            resumePositionMs = it.currentPosition
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        _binding?.playerView?.player = null
    }

    override fun onDestroyView() {
        releasePlayer()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_THUMB_URL = "thumb_url"
        const val ARG_VIDEO_URL = "video_url"
        const val ARG_TITLE = "title"
        const val ARG_FILTER_ID = "filter_id"
        private const val STATE_POSITION = "position"

        fun args(item: TrendingVideo): Bundle = bundleOf(
            ARG_THUMB_URL to item.thumbUrl,
            ARG_VIDEO_URL to item.videoUrl,
            ARG_TITLE to item.title,
            ARG_FILTER_ID to item.filterId,
        )
    }
}
