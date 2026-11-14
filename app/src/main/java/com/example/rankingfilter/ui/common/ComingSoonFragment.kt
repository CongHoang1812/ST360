package com.example.rankingfilter.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentComingSoonBinding

/**
 * Màn tạm cho các màn chưa làm (New filter, Choose a Filter, Gallery, Setting).
 * Tiêu đề lấy từ argument "title" (string resource) khai báo trong nav_graph.xml.
 */
class ComingSoonFragment : Fragment() {

    private var _binding: FragmentComingSoonBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComingSoonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarsPadding(top = true, bottom = true)
        val titleRes = arguments?.getInt(ARG_TITLE, 0) ?: 0
        binding.title.setText(if (titleRes != 0) titleRes else R.string.app_name)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_TITLE = "title"
    }
}
