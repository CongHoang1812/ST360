package com.example.rankingfilter.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.data.AppPrefs
import com.example.rankingfilter.data.music.MusicRepository
import com.example.rankingfilter.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash (Figma 3:2): ảnh nền đầy đủ + vòng tròn tải, khoảng 2 giây rồi sang màn tiếp.
 * Lần đầu: Language → Intro → Permission → Home. Các lần sau (đã xem intro): thẳng Home.
 */
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Nạp nhạc từ Room và đọc nền thời lượng các bài còn thiếu, để màn Add Sound mở ra là có đủ.
        MusicRepository.warmUp(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(SPLASH_MS)
                val seen = AppPrefs.isIntroSeen(requireContext())
                findNavController().navigate(if (seen) R.id.action_splash_to_home else R.id.action_splash_to_language)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SPLASH_MS = 2_000L
    }
}
