package com.example.rankingfilter.ui.placeholder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.rankingfilter.data.loadBuiltInFilters
import com.example.rankingfilter.databinding.FragmentPlaceholderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Màn tạm để kiểm tra theme, font và dữ liệu; thay bằng Home ở giai đoạn 2. */
class PlaceholderFragment : Fragment() {

    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Kiểm tra giai đoạn 1: Logcat lọc theo tag "Phase1" phải thấy 30 filter.
        viewLifecycleOwner.lifecycleScope.launch {
            val filters = withContext(Dispatchers.IO) { loadBuiltInFilters(requireContext()) }
            Log.d("Phase1", "loadBuiltInFilters: ${filters.size} filter")
            filters.forEach { Log.d("Phase1", "${it.id} | ${it.title} | ${it.pack} | ${it.photos.size} ảnh") }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
