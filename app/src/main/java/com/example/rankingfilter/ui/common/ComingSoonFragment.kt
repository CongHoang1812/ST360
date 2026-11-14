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

        // Màn Camera tạm: hiện filterId nhận được để kiểm tra luồng chọn filter.
        val filterId = arguments?.getString(ARG_FILTER_ID)
        // Màn Preview tạm (giai đoạn 6): hiện link video vừa quay để kiểm tra luồng quay.
        val videoUri = arguments?.getString(ARG_VIDEO_URI)
        binding.message.text = when {
            videoUri != null -> getString(R.string.coming_soon) + "\n" + getString(R.string.coming_soon_video, videoUri)
            filterId != null -> getString(R.string.coming_soon) + "\n" + getString(R.string.coming_soon_filter, filterId)
            else -> getString(R.string.coming_soon)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_FILTER_ID = "filter_id"
        const val ARG_VIDEO_URI = "video_uri"
    }
}
