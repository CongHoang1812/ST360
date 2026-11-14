package com.example.rankingfilter.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.example.rankingfilter.R
import com.example.rankingfilter.data.trending.TrendingVideo
import com.example.rankingfilter.databinding.FragmentHomeBinding
import com.example.rankingfilter.ui.common.BottomTab
import com.example.rankingfilter.ui.common.GridSpacingDecoration
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.example.rankingfilter.ui.common.setup
import com.example.rankingfilter.ui.preview.PreviewVideoFragment
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var trendingAdapter: TrendingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()
        setupHeaderAndBanner()
        setupTrending()
        setupBottomNav()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
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

    private fun setupHeaderAndBanner() {
        val nav = findNavController()
        binding.btnSettings.setOnClickListener { nav.navigate(R.id.action_home_to_setting) }
        binding.banner.root.setOnClickListener { nav.navigate(R.id.action_home_to_newFilter) }
        binding.banner.btnExplore.setOnClickListener { nav.navigate(R.id.action_home_to_newFilter) }
    }

    private fun setupTrending() {
        trendingAdapter = TrendingAdapter(::openPreview)
        binding.rvTrending.apply {
            layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
            adapter = trendingAdapter
            addItemDecoration(GridSpacingDecoration(SPAN_COUNT, resources.getDimensionPixelSize(R.dimen.space_m)))
        }
        binding.btnRetry.setOnClickListener { viewModel.load() }
    }

    private fun setupBottomNav() {
        val nav = findNavController()
        binding.bottomNav.setup(
            selected = BottomTab.HOME,
            onHome = { binding.scroll.smoothScrollTo(0, 0) },
            onGallery = { nav.navigate(R.id.action_home_to_gallery) },
            onCamera = { nav.navigate(R.id.action_home_to_chooseFilter) },
        )
    }

    private fun render(state: HomeUiState) {
        // Banner: avatar của 3 filter đầu trong pack "New Filter".
        val cards = listOf(binding.banner.card1, binding.banner.card2, binding.banner.card3)
        cards.forEachIndexed { index, card ->
            val filter = state.newPack.getOrNull(index)
            if (filter != null) {
                card.load("file:///android_asset/${filter.avatar}") {
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
        findNavController().navigate(R.id.action_home_to_preview, PreviewVideoFragment.args(item))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SPAN_COUNT = 2
    }
}
