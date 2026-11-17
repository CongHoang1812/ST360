package com.example.rankingfilter.ui.setting

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.rankingfilter.R
import com.example.rankingfilter.data.AppPrefs
import com.example.rankingfilter.databinding.DialogRateBinding
import com.example.rankingfilter.ui.common.ConfirmDialogFragment

/**
 * Popup Rate us (Figma 4034:33): 5 sao, emoji + lời nhắn đổi theo số sao.
 * 0 sao: "Do you like the app?"; 1–3 sao: "Oh, no!"; 4–5 sao: "We love you too!".
 * Bấm Rate: 4–5 sao → gửi kết quả cho màn chủ để mở trang app trên Google Play;
 * 1–3 sao → cảm ơn góp ý rồi đóng (không đẩy người dùng chê lên Play Store).
 */
class RateDialogFragment : DialogFragment() {

    private var _binding: DialogRateBinding? = null
    private val binding get() = _binding!!

    private var rating = 0
    private val stars = mutableListOf<ImageView>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogRateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rating = savedInstanceState?.getInt(STATE_RATING) ?: 0
        buildStars()
        render()

        binding.btnExit.setOnClickListener { dismiss() }
        binding.btnRate.setOnClickListener {
            if (rating > 0) {
                AppPrefs.setRated(requireContext())
                Toast.makeText(requireContext().applicationContext, R.string.rate_thanks, Toast.LENGTH_SHORT).show()
                setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
                dismiss()
            }
        }
    }

    private fun buildStars() {
        val size = resources.getDimensionPixelSize(R.dimen.rate_star_size)
        val gap = resources.getDimensionPixelSize(R.dimen.rate_star_gap)
        for (i in 1..STAR_COUNT) {
            val star = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (i > 1) marginStart = gap
                }
            }
            stars += star
            binding.starRow.addView(star)
        }
        // Chạm hoặc lướt ngón tay trên hàng sao: sao nào dưới ngón tay thì chấm tới sao đó.
        binding.starRow.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    val value = (event.x / (size + gap)).toInt().plus(1).coerceIn(1, STAR_COUNT)
                    if (value != rating) {
                        rating = value
                        render()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun render() {
        stars.forEachIndexed { index, star ->
            star.setImageResource(if (index < rating) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
        }
        val emoji = EMOJIS[rating]
        val (title, subtitle) = when {
            rating == 0 -> R.string.rate_title_initial to R.string.rate_subtitle_initial
            rating <= 3 -> R.string.rate_title_low to R.string.rate_subtitle_low
            else -> R.string.rate_title_high to R.string.rate_subtitle_high
        }
        binding.ivEmoji.setImageResource(emoji)
        binding.tvTitle.setText(title)
        binding.tvSubtitle.setText(subtitle)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_RATING, rating)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply { setCanceledOnTouchOutside(true) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(ConfirmDialogFragment.DIM_AMOUNT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stars.clear()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "rate_dialog"
        private const val STATE_RATING = "rating"
        private const val STAR_COUNT = 5

        /** Ảnh emoji theo số sao 0..5 (Figma 4034:36 / 56 / 76 / 96 / 116 / 136). */
        private val EMOJIS = intArrayOf(
            R.drawable.rate_emoji_0, R.drawable.rate_emoji_1, R.drawable.rate_emoji_2,
            R.drawable.rate_emoji_3, R.drawable.rate_emoji_4, R.drawable.rate_emoji_5,
        )

        fun show(host: Fragment) {
            val fm = host.childFragmentManager
            if (fm.findFragmentByTag(REQUEST_KEY) != null) return
            RateDialogFragment().show(fm, REQUEST_KEY)
        }

        /** Gọi khi người dùng chấm sao và bấm Rate (gọi trong onViewCreated của Fragment chủ). */
        fun listen(host: Fragment, onRated: () -> Unit) {
            host.childFragmentManager.setFragmentResultListener(REQUEST_KEY, host.viewLifecycleOwner) { _, _ ->
                onRated()
            }
        }
    }
}
