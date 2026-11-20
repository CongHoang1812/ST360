package com.funny.rank.filters.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.funny.rank.filters.R
import com.funny.rank.filters.data.AppPrefs
import com.funny.rank.filters.data.assetUri
import com.funny.rank.filters.data.trending.TrendingVideo
import com.funny.rank.filters.databinding.FragmentHomeBinding
import com.funny.rank.filters.ui.common.BottomTab
import com.funny.rank.filters.ui.common.GridSpacingDecoration
import com.funny.rank.filters.ui.common.applySystemBarsPadding
import com.funny.rank.filters.ui.common.navigateSafe
import com.funny.rank.filters.ui.common.setup
import com.funny.rank.filters.ui.preview.PreviewPagerFragment
import com.funny.rank.filters.ui.preview.PreviewVideoFragment
import com.funny.rank.filters.ui.setting.RateDialogFragment
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    // Adapter + pool sống theo Fragment (không theo view). Mở video thì view của Home bị hủy; Back về tạo lại view,
    // mà rvTrending nằm trong NestedScrollView nên khung hình đầu phải dựng MỌI ô cùng lúc (~160ms trên Xiaomi)
    // → animation Back từ Trending giật. Giữ pool để lần sau chỉ bind lại ô cũ, không inflate.
    private val trendingAdapter = TrendingAdapter(::openPreview)
    private val trendingPool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, TRENDING_POOL_SIZE) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()
        setupHeaderAndBanner()
        setupRateOnBack()
        setupTrending()
        setupBottomNav()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Màn khác (Successful → Home) yêu cầu về đầu màn. NestedScrollView trả lại vị trí cũ trong onLayout,
        // nên cuộn sau lần layout kế tiếp.
        val entry = runCatching { findNavController().getBackStackEntry(R.id.homeFragment) }.getOrNull()
        if (entry?.savedStateHandle?.remove<Boolean>(KEY_SCROLL_TO_TOP) == true) {
            binding.scroll.doOnNextLayout { it.scrollTo(0, 0) }
        }
    }

    private fun setupInsets() {
        // Nội dung bắt đầu dưới thanh trạng thái; nền vẫn tràn lên trên.
        binding.content.applySystemBarsPadding(top = true)
        // Chừa chỗ cho bottom nav (đo sau khi layout) để ô cuối không bị che.
        binding.bottomNav.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val extra = resources.getDimensionPixelSize(R.dimen.space_m)
            val wanted = v.height + extra
            if (binding.scroll.paddingBottom != wanted) binding.scroll.updatePadding(bottom = wanted)
        }
    }

    /**
     * Back ở Home = thoát hẳn app (finish, không chỉ đưa xuống nền như mặc định của Android 12+),
     * để lần mở sau đi lại Splash → Intro.
     * Chưa Rate: lần 1 thoát, lần 2 hiện popup Rate, lần 3 thoát, lần 4 hỏi lại…
     * (đếm qua các lần mở app, [AppPrefs.nextHomeBackCount]). Đã Rate thì luôn thoát.
     * Rate xong trong popup này (chấm sao + Rate) thì thoát app luôn; bấm Exit chỉ đóng popup.
     */
    private fun setupRateOnBack() {
        RateDialogFragment.listen(this) { requireActivity().finish() }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val context = requireContext()
                if (!AppPrefs.isRated(context) && AppPrefs.nextHomeBackCount(context) % 2 == 0) {
                    RateDialogFragment.show(this@HomeFragment)
                } else {
                    requireActivity().finish()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun setupHeaderAndBanner() {
        val nav = findNavController()
        binding.btnSettings.setOnClickListener { nav.navigateSafe(R.id.action_home_to_setting) }
        binding.banner.root.setOnClickListener { nav.navigateSafe(R.id.action_home_to_newFilter) }
        binding.banner.btnExplore.setOnClickListener { nav.navigateSafe(R.id.action_home_to_newFilter) }
    }

    private val spanCount get() = resources.getInteger(R.integer.span_filters)

    private fun setupTrending() {
        binding.rvTrending.apply {
            setRecycledViewPool(trendingPool)
            layoutManager = GridLayoutManager(requireContext(), spanCount)
            // swapAdapter(…, false) chứ không `adapter =`: setAdapter xóa sạch pool khi pool không còn gắn adapter nào.
            swapAdapter(trendingAdapter, false)
            addItemDecoration(GridSpacingDecoration(spanCount, resources.getDimensionPixelSize(R.dimen.space_m)))
        }
        binding.btnRetry.setOnClickListener { viewModel.load() }
    }

    private fun setupBottomNav() {
        val nav = findNavController()
        binding.bottomNav.setup(
            selected = BottomTab.HOME,
            onHome = { binding.scroll.smoothScrollTo(0, 0) },
            onGallery = { nav.navigateSafe(R.id.action_home_to_gallery) },
            onCamera = { nav.navigateSafe(R.id.action_home_to_chooseFilter) },
        )
    }

    private fun render(state: HomeUiState) {
        // Banner: avatar của 3 filter đầu trong pack "New Filter".
        val cards = listOf(binding.banner.card1, binding.banner.card2, binding.banner.card3)
        cards.forEachIndexed { index, card ->
            val filter = state.newPack.getOrNull(index)
            if (filter != null) {
                card.load(assetUri(filter.avatar)) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            } else {
                card.setImageResource(R.drawable.bg_image_placeholder)
            }
        }

        val showGrid = !state.isLoading
        binding.progressTrending.isVisible = state.isLoading && state.trending.isEmpty()
        binding.errorRow.isVisible = showGrid && state.networkError
        binding.emptyTrending.isVisible = showGrid && state.trending.isEmpty() && !state.networkError
        binding.btnRetry.isEnabled = !state.isLoading
        trendingAdapter.submitList(state.trending)
    }

    private fun openPreview(item: TrendingVideo) {
        val list = viewModel.state.value.trending
        val index = list.indexOfFirst { it.id == item.id }
        val args = if (index >= 0) PreviewPagerFragment.args(list, index) else PreviewVideoFragment.args(item)
        findNavController().navigateSafe(R.id.action_home_to_preview, args)
    }

    override fun onDestroyView() {
        // Gỡ adapter, trả mọi ô về trendingPool để view Home lần sau dùng lại (swapAdapter không xóa pool như setAdapter).
        binding.rvTrending.swapAdapter(null, true)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Đặt `true` vào savedStateHandle của Home trước khi quay về để Home cuộn lên đầu. */
        const val KEY_SCROLL_TO_TOP = "home_scroll_to_top"
        private const val TRENDING_POOL_SIZE = 64
    }
}
