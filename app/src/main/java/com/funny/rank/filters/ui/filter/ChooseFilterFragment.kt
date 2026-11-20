package com.funny.rank.filters.ui.filter

import com.funny.rank.filters.ui.common.navigateSafe
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.funny.rank.filters.R
import com.funny.rank.filters.databinding.FragmentChooseFilterBinding
import com.funny.rank.filters.ui.common.ConfirmDialogFragment
import com.funny.rank.filters.ui.common.GridSpacingDecoration
import com.funny.rank.filters.ui.makefilter.MakeFilterFragment
import com.funny.rank.filters.ui.makefilter.MakeFilterViewModel
import com.funny.rank.filters.ui.common.applySystemBarsPadding
import kotlinx.coroutines.launch

/**
 * Màn chọn filter, dùng chung cho 2 chế độ (argument "mode" trong nav_graph):
 * - ALL: "Choose a Filter" (Figma 25:27, 25:202, 57:68) với tab Discover / My Filters.
 * - NEW_PACK: "New filter" (Figma 25:532), không có tab, chỉ filter thuộc pack "New Filter".
 */
class ChooseFilterFragment : Fragment() {

    private var _binding: FragmentChooseFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChooseFilterViewModel by viewModels()
    private lateinit var adapter: FilterAdapter
    private var lastRenderedTab: FilterTab? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChooseFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val spanCount get() = resources.getInteger(R.integer.span_filters)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isNewPack = viewModel.mode == FilterMode.NEW_PACK

        binding.content.applySystemBarsPadding(top = true)
        binding.rvFilters.applySystemBarsPadding(bottom = true)

        // NEW_PACK: tiêu đề "New filter", nền trang trí như Home, không có tab.
        binding.title.setText(if (isNewPack) R.string.title_new_filter else R.string.title_choose_filter)
        binding.bgDecor.isVisible = isNewPack
        binding.tabs.isVisible = !isNewPack

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tabDiscover.setOnClickListener { onTabClicked(FilterTab.DISCOVER) }
        binding.tabMyFilters.setOnClickListener { onTabClicked(FilterTab.MY_FILTERS) }
        binding.btnCreateFilter.setOnClickListener {
            findNavController().navigateSafe(R.id.action_chooseFilter_to_makeFilter)
        }

        adapter = FilterAdapter(::openCamera, onMoreClick = ::showFilterMenu)
        binding.rvFilters.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvFilters.adapter = adapter
        binding.rvFilters.addItemDecoration(
            GridSpacingDecoration(spanCount, resources.getDimensionPixelSize(R.dimen.filter_cell_gap))
        )

        ConfirmDialogFragment.listen(this, REQUEST_DELETE) { id ->
            val appContext = requireContext().applicationContext
            id?.let { viewModel.deleteUserFilter(it) { Toast.makeText(appContext, R.string.filter_deleted, Toast.LENGTH_SHORT).show() } }
        }

        // Make a Filter lưu xong → mở tab My Filters để thấy filter vừa tạo / vừa sửa.
        val entry = findNavController().currentBackStackEntry
        entry?.savedStateHandle?.getLiveData<String>(MakeFilterFragment.RESULT_FILTER_SAVED)
            ?.observe(viewLifecycleOwner) { id ->
                if (id == null) return@observe
                entry.savedStateHandle.remove<String>(MakeFilterFragment.RESULT_FILTER_SAVED)
                if (viewModel.mode == FilterMode.ALL) onTabClicked(FilterTab.MY_FILTERS)
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    /** Menu Edit / Delete trên ô filter tự tạo (Figma 22:557). */
    private fun showFilterMenu(item: FilterCardItem, anchor: View) {
        FilterMenuPopup.show(
            anchor = anchor,
            onEdit = {
                findNavController().navigateSafe(
                    R.id.action_chooseFilter_to_makeFilter,
                    bundleOf(MakeFilterViewModel.ARG_FILTER_ID to item.id),
                )
            },
            onDelete = {
                ConfirmDialogFragment.show(
                    host = this,
                    requestKey = REQUEST_DELETE,
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

    /**
     * Tab là MaterialButton checkable: bấm lại tab đang chọn sẽ tự bỏ chọn, mà state không đổi
     * nên render() không chạy lại. Đặt lại trạng thái ngay tại đây để luôn có đúng một tab được chọn.
     */
    private fun onTabClicked(tab: FilterTab) {
        setTabsChecked(tab)
        viewModel.selectTab(tab)
    }

    private fun setTabsChecked(tab: FilterTab) {
        binding.tabDiscover.isChecked = tab == FilterTab.DISCOVER
        binding.tabMyFilters.isChecked = tab == FilterTab.MY_FILTERS
    }

    private fun render(state: ChooseFilterUiState) {
        val onMyFilters = state.mode == FilterMode.ALL && state.tab == FilterTab.MY_FILTERS
        setTabsChecked(state.tab)

        val tabChanged = lastRenderedTab != null && lastRenderedTab != state.tab
        lastRenderedTab = state.tab
        adapter.submitList(state.items) {
            // Chỉ cuộn về đầu khi đổi tab (quay lại từ Camera thì giữ vị trí cuộn).
            if (tabChanged) _binding?.rvFilters?.scrollToPosition(0)
        }

        val empty = !state.isLoading && state.items.isEmpty()
        binding.progress.isVisible = state.isLoading
        binding.emptyMyFilters.isVisible = empty && onMyFilters
        binding.emptyBuiltIn.isVisible = empty && !onMyFilters
    }

    private fun openCamera(item: FilterCardItem) {
        viewModel.onFilterChosen(item)
        val nav = findNavController()
        if (arguments?.getBoolean(ARG_PICK_FOR_CAMERA) == true) {
            // Bấm nhanh hai ô: ô sau chạy khi màn này đã bị pop (đang ở Camera) → bỏ qua, không pop thêm về Home.
            val current = nav.currentDestination?.id
            if (current != R.id.chooseFilterFragment && current != R.id.newFilterFragment) return
            // Mở từ Camera: trả filter về Camera đó rồi quay lại (không mở thêm Camera).
            nav.previousBackStackEntry?.savedStateHandle?.let {
                it[RESULT_IS_USER_FILTER] = item.isUserFilter
                it[RESULT_FILTER_ID] = item.id
            }
            nav.popBackStack()
            return
        }
        nav.navigateSafe(
            R.id.action_chooseFilter_to_camera,
            bundleOf(ARG_FILTER_ID to item.id, ARG_IS_USER_FILTER to item.isUserFilter),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lastRenderedTab = null
    }

    companion object {
        private const val REQUEST_DELETE = "delete_user_filter"
        const val ARG_FILTER_ID = "filter_id"
        const val ARG_IS_USER_FILTER = "is_user_filter"

        const val ARG_PICK_FOR_CAMERA = "pick_for_camera"

        /** Kết quả trả về Camera khi [ARG_PICK_FOR_CAMERA]: id filter vừa chọn (đặt SAU [RESULT_IS_USER_FILTER]). */
        const val RESULT_FILTER_ID = "picked_filter_id"
        const val RESULT_IS_USER_FILTER = "picked_is_user_filter"
    }
}
