package com.example.rankingfilter.ui.intro

import com.example.rankingfilter.ui.common.navigateSafe
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.rankingfilter.R
import com.example.rankingfilter.data.AppPrefs
import com.example.rankingfilter.databinding.FragmentIntroBinding
import com.example.rankingfilter.databinding.ItemIntroPageBinding

/** Giới thiệu 3 trang (Figma 3:4, 3:5, 3:6): ảnh minh họa, câu mô tả, 3 chấm và Next. */
class IntroFragment : Fragment() {

    private var _binding: FragmentIntroBinding? = null
    private val binding get() = _binding!!

    private val pages = listOf(
        IntroPage(R.drawable.intro_art_1, R.string.intro_1),
        IntroPage(R.drawable.intro_art_2, R.string.intro_2),
        IntroPage(R.drawable.intro_art_3, R.string.intro_3),
    )
    private val dots = mutableListOf<View>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIntroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pager.adapter = PageAdapter(pages)
        binding.pager.offscreenPageLimit = 1
        buildDots()
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = renderPage(position)
        })
        renderPage(binding.pager.currentItem)

        binding.btnNext.setOnClickListener {
            val next = binding.pager.currentItem + 1
            if (next < pages.size) binding.pager.setCurrentItem(next, true) else finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dots.clear()
        _binding = null
    }

    private fun buildDots() {
        val width = resources.getDimensionPixelSize(R.dimen.intro_dot_width)
        val height = resources.getDimensionPixelSize(R.dimen.intro_dot_height)
        val gap = resources.getDimensionPixelSize(R.dimen.intro_dot_gap)
        pages.indices.forEach { index ->
            val dot = View(requireContext())
            dot.layoutParams = ViewGroup.MarginLayoutParams(width, height).apply {
                if (index > 0) marginStart = gap
            }
            dot.setBackgroundResource(R.drawable.bg_intro_dot)
            binding.dots.addView(dot)
            dots += dot
        }
    }

    private fun renderPage(position: Int) {
        dots.forEachIndexed { i, dot -> dot.isSelected = i == position }
    }

    private fun finish() {
        val firstTime = !AppPrefs.isIntroSeen(requireContext())
        AppPrefs.setIntroSeen(requireContext())
        findNavController().navigateSafe(if (firstTime) R.id.action_intro_to_permission else R.id.action_intro_to_home)
    }

    private class IntroPage(val imageRes: Int, val textRes: Int)

    private class PageAdapter(private val pages: List<IntroPage>) : RecyclerView.Adapter<PageAdapter.Holder>() {
        class Holder(val binding: ItemIntroPageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemIntroPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.ivArt.setImageResource(pages[position].imageRes)
            holder.binding.tvText.setText(pages[position].textRes)
        }
    }
}
