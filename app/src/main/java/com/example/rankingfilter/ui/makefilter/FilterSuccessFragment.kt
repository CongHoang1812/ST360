package com.example.rankingfilter.ui.makefilter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.rankingfilter.R
import com.example.rankingfilter.data.userfilter.UserFilterRepository
import com.example.rankingfilter.databinding.FragmentFilterSuccessBinding
import com.example.rankingfilter.ui.camera.CameraViewModel
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.example.rankingfilter.ui.common.navigateSafe
import com.example.rankingfilter.ui.home.HomeFragment
import kotlinx.coroutines.launch
import java.io.File

/**
 * Successful sau khi TẠO filter mới (Figma 57:137): ảnh bìa + tên filter, My Filters, Try now, nút Home.
 * Make a Filter đã bị gỡ khỏi back stack (popUpTo inclusive), nên Back / My Filters về màn mở Make a Filter
 * (Choose a Filter mở sẵn tab My Filters nhờ RESULT_FILTER_SAVED, hoặc Gallery).
 * Sửa filter KHÔNG qua màn này (chỉ Toast rồi quay lại).
 */
class FilterSuccessFragment : Fragment() {

    private var _binding: FragmentFilterSuccessBinding? = null
    private val binding get() = _binding!!

    private val filterId: String get() = requireArguments().getString(ARG_FILTER_ID).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilterSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.btnHome.setOnClickListener { goHome() }
        binding.btnMyFilters.setOnClickListener { findNavController().navigateUp() }
        binding.btnTryNow.setOnClickListener {
            findNavController().navigateSafe(
                R.id.action_filterSuccess_to_camera,
                bundleOf(CameraViewModel.ARG_FILTER_ID to filterId, CameraViewModel.ARG_IS_USER_FILTER to true),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val filter = UserFilterRepository(requireContext()).getById(filterId) ?: return@launch
            val b = _binding ?: return@launch
            b.tvName.text = filter.filter.name
            filter.coverPath?.let { b.ivCover.load(File(it).toUri().toString()) }
        }
    }

    private fun goHome() {
        val nav = findNavController()
        runCatching { nav.getBackStackEntry(R.id.homeFragment) }.getOrNull()
            ?.savedStateHandle?.set(HomeFragment.KEY_SCROLL_TO_TOP, true)
        if (!nav.popBackStack(R.id.homeFragment, false)) nav.navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_FILTER_ID = "filter_id"
    }
}
