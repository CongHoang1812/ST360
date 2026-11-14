package com.example.rankingfilter.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentGalleryBinding
import com.example.rankingfilter.ui.camera.CameraViewModel
import com.example.rankingfilter.ui.common.BottomTab
import com.example.rankingfilter.ui.common.ConfirmDialogFragment
import com.example.rankingfilter.ui.common.GridSpacingDecoration
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.example.rankingfilter.ui.common.setup
import com.example.rankingfilter.ui.filter.FilterAdapter
import com.example.rankingfilter.ui.filter.FilterCardItem
import com.example.rankingfilter.ui.filter.FilterMenuPopup
import com.example.rankingfilter.ui.makefilter.MakeFilterViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gallery (Figma 21:281, 21:423, 28:1565, 21:440, 21:470, 29:2008): hai tab My Videos và My Filters.
 *
 * My Videos đọc thư viện riêng của app (`VideoLibrary.listSaved`, các video đã bấm Save ở màn Preview),
 * không đọc MediaStore: màn xem video có nút Download để chép ra thư viện ảnh của máy, nên video ở đây
 * chưa nằm trong thư viện ảnh. Vì vậy cũng không cần quyền đọc media.
 */
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels()

    private lateinit var videoAdapter: GalleryVideoAdapter
    private lateinit var tileAdapter: CreateFilterTileAdapter
    private lateinit var filterAdapter: FilterAdapter
    private var shownTab: GalleryTab? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.applySystemBarsPadding(top = true)
        binding.bottomNav.root.applySystemBarsPadding(bottom = true)
        // Thuộc tính đặt trên thẻ <include> (trừ layout_* và id) bị bỏ qua: đặt độ nổi ở đây để bottom nav
        // nằm trên khung nội dung và lưới (cùng có translationZ trong layout).
        binding.bottomNav.root.translationZ = resources.getDimension(R.dimen.gallery_nav_z)

        val nav = findNavController()
        binding.bottomNav.setup(
            selected = BottomTab.GALLERY,
            onHome = { if (!nav.popBackStack(R.id.homeFragment, false)) nav.navigateUp() },
            onGallery = { binding.rvVideos.smoothScrollToPosition(0) },
            onCamera = { nav.navigate(R.id.action_gallery_to_chooseFilter) },
        )
        binding.btnSettings.setOnClickListener { nav.navigate(R.id.action_gallery_to_setting) }
        binding.tabVideos.setOnClickListener { viewModel.selectTab(GalleryTab.VIDEOS) }
        binding.tabFilters.setOnClickListener { viewModel.selectTab(GalleryTab.FILTERS) }
        binding.btnCreateFilter.setOnClickListener { openMakeFilter(null) }

        setUpVideoGrid()
        setUpFilterGrid()

        ConfirmDialogFragment.listen(this, REQUEST_DELETE_VIDEO) { path -> path?.let(viewModel::deleteVideo) }
        ConfirmDialogFragment.listen(this, REQUEST_DELETE_FILTER) { id -> id?.let(viewModel::deleteFilter) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshVideos()
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownTab = null
        _binding = null
    }

    private fun setUpVideoGrid() {
        videoAdapter = GalleryVideoAdapter(onOpen = ::openVideo, onDelete = ::confirmDeleteVideo)
        binding.rvVideos.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
        binding.rvVideos.adapter = videoAdapter
        binding.rvVideos.addItemDecoration(GridSpacingDecoration(SPAN_COUNT, resources.getDimensionPixelSize(R.dimen.space_m)))
    }

    private fun setUpFilterGrid() {
        tileAdapter = CreateFilterTileAdapter { openMakeFilter(null) }
        filterAdapter = FilterAdapter(onClick = ::openCamera, onMoreClick = ::showFilterMenu)
        binding.rvFilters.layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
        binding.rvFilters.adapter = ConcatAdapter(tileAdapter, filterAdapter)
        binding.rvFilters.addItemDecoration(GridSpacingDecoration(SPAN_COUNT, resources.getDimensionPixelSize(R.dimen.space_m)))
    }

    private fun render(ui: GalleryUi) {
        val onVideos = ui.tab == GalleryTab.VIDEOS
        // Tab đang chọn nền xanh nhạt liền với khung, nằm trên; tab kia xám, thấp hơn 8dp (Figma 21:435/21:437).
        binding.tabVideos.isSelected = onVideos
        binding.tabFilters.isSelected = !onVideos
        val unselectedDrop = resources.getDimension(R.dimen.gallery_tab_drop)
        binding.tabVideos.translationZ = if (onVideos) 1f else 0f
        binding.tabFilters.translationZ = if (onVideos) 0f else 1f
        binding.tabVideos.translationY = if (onVideos) 0f else unselectedDrop
        binding.tabFilters.translationY = if (onVideos) unselectedDrop else 0f

        val videos = ui.videos
        binding.rvVideos.isVisible = onVideos && !videos.isNullOrEmpty()
        binding.emptyVideos.isVisible = onVideos && videos != null && videos.isEmpty()
        videoAdapter.submitList(videos.orEmpty())

        val filters = ui.filters
        binding.rvFilters.isVisible = !onVideos && !filters.isNullOrEmpty()
        binding.emptyFilters.isVisible = !onVideos && filters != null && filters.isEmpty()
        tileAdapter.visible = !filters.isNullOrEmpty()
        filterAdapter.submitList(filters.orEmpty())

        binding.progress.isVisible = if (onVideos) videos == null else filters == null
        if (shownTab != null && shownTab != ui.tab) {
            (if (onVideos) binding.rvVideos else binding.rvFilters).scrollToPosition(0)
        }
        shownTab = ui.tab
    }

    // ---------------------------------------------------------------- My Videos

    private fun openVideo(file: File) {
        findNavController().navigate(
            R.id.action_gallery_to_viewer,
            bundleOf(VideoViewerFragment.ARG_VIDEO_PATH to file.absolutePath),
        )
    }

    private fun confirmDeleteVideo(file: File) {
        ConfirmDialogFragment.show(
            host = this,
            requestKey = REQUEST_DELETE_VIDEO,
            title = R.string.delete_video_title,
            message = R.string.delete_video_message,
            negative = R.string.cancel,
            positive = R.string.delete,
            style = ConfirmDialogFragment.Style.DESTRUCTIVE,
            payload = file.absolutePath,
        )
    }

    // ---------------------------------------------------------------- My Filters

    /** Chạm một filter tự tạo: quay luôn với filter đó. */
    private fun openCamera(item: FilterCardItem) {
        findNavController().navigate(
            R.id.action_gallery_to_camera,
            bundleOf(
                CameraViewModel.ARG_FILTER_ID to item.id,
                CameraViewModel.ARG_IS_USER_FILTER to item.isUserFilter,
            ),
        )
    }

    private fun openMakeFilter(filterId: String?) {
        findNavController().navigate(
            R.id.action_gallery_to_makeFilter,
            bundleOf(MakeFilterViewModel.ARG_FILTER_ID to filterId),
        )
    }

    private fun showFilterMenu(item: FilterCardItem, anchor: View) {
        FilterMenuPopup.show(
            anchor = anchor,
            onEdit = { openMakeFilter(item.id) },
            onDelete = {
                ConfirmDialogFragment.show(
                    host = this,
                    requestKey = REQUEST_DELETE_FILTER,
                    title = R.string.delete_filter_title,
                    message = R.string.delete_filter_message,
                    negative = R.string.cancel,
                    positive = R.string.delete,
                    style = ConfirmDialogFragment.Style.DESTRUCTIVE,
                    payload = item.id,
                )
            },
        )
    }

    private companion object {
        const val SPAN_COUNT = 2
        const val REQUEST_DELETE_VIDEO = "gallery_delete_video"
        const val REQUEST_DELETE_FILTER = "gallery_delete_filter"
    }
}
