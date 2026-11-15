package com.example.rankingfilter.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.rankingfilter.data.trending.TrendingVideo
import com.example.rankingfilter.databinding.FragmentPreviewPagerBinding
import com.example.rankingfilter.video.VideoCache

/**
 * Điểm đến "previewVideoFragment" trong nav_graph: vuốt dọc để chuyển giữa các video Trending
 * (bắt đầu từ video vừa bấm). Mỗi trang là một [PreviewVideoFragment]; ViewPager2 chỉ để trang hiện tại
 * ở RESUMED nên mỗi lúc chỉ một video phát.
 */
class PreviewPagerFragment : Fragment() {

    private var _binding: FragmentPreviewPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val thumbs = args.getStringArray(ARG_THUMBS)
        val videos = args.getStringArray(ARG_VIDEOS)
        val titles = args.getStringArray(ARG_TITLES)
        val filters = args.getStringArray(ARG_FILTERS)

        // Thiếu danh sách (mở từ chỗ khác) thì chỉ có đúng một trang từ các tham số của video hiện tại.
        val pages: List<Bundle> = if (thumbs != null && videos != null && titles != null && filters != null) {
            thumbs.indices.map { i ->
                bundleOf(
                    PreviewVideoFragment.ARG_THUMB_URL to thumbs[i],
                    PreviewVideoFragment.ARG_VIDEO_URL to videos[i].ifEmpty { null },
                    PreviewVideoFragment.ARG_TITLE to titles[i].ifEmpty { null },
                    PreviewVideoFragment.ARG_FILTER_ID to filters[i].ifEmpty { null },
                )
            }
        } else {
            listOf(args)
        }

        binding.pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        // Giữ sẵn trang trước/sau (đã prepare player) để vuốt tới là phát ngay.
        binding.pager.offscreenPageLimit = 1
        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(position: Int) =
                PreviewVideoFragment().apply { arguments = pages[position] }
        }
        // Tải trước đầu các video kế bên (ưu tiên video sau, rồi video trước) để vuốt tới là phát gần như ngay.
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                listOf(position + 1, position + 2, position - 1).forEach { i ->
                    pages.getOrNull(i)?.getString(PreviewVideoFragment.ARG_VIDEO_URL)
                        ?.let { VideoCache.preload(requireContext(), it) }
                }
            }
        })
        // Lần đầu mới nhảy tới video đã bấm; xoay màn hình thì ViewPager2 tự khôi phục vị trí.
        if (savedInstanceState == null) {
            val start = args.getInt(ARG_START_INDEX, 0).coerceIn(0, pages.lastIndex)
            binding.pager.setCurrentItem(start, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        VideoCache.cancelPreload()
        binding.pager.adapter = null
        _binding = null
    }

    companion object {
        private const val ARG_THUMBS = "pager_thumbs"
        private const val ARG_VIDEOS = "pager_videos"
        private const val ARG_TITLES = "pager_titles"
        private const val ARG_FILTERS = "pager_filters"
        private const val ARG_START_INDEX = "pager_start"

        /** Tham số cho cả danh sách [items], mở ở [index]. Các khóa thumb_url… của video hiện tại giữ cho khớp nav_graph. */
        fun args(items: List<TrendingVideo>, index: Int): Bundle =
            PreviewVideoFragment.args(items[index]).apply {
                putStringArray(ARG_THUMBS, items.map { it.thumbUrl }.toTypedArray())
                putStringArray(ARG_VIDEOS, items.map { it.videoUrl.orEmpty() }.toTypedArray())
                putStringArray(ARG_TITLES, items.map { it.title.orEmpty() }.toTypedArray())
                putStringArray(ARG_FILTERS, items.map { it.filterId.orEmpty() }.toTypedArray())
                putInt(ARG_START_INDEX, index)
            }
    }
}
