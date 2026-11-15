package com.example.rankingfilter.ui.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentLanguageBinding
import com.example.rankingfilter.databinding.ItemLanguageBinding
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import java.util.Locale

/** Một ngôn ngữ của app. `tag` là mã BCP-47 cho AppCompatDelegate (Indonesia là "id"). */
data class AppLanguage(val tag: String, val nameRes: Int, val flagRes: Int)

val APP_LANGUAGES = listOf(
    AppLanguage("en", R.string.lang_english, R.drawable.flag_en),
    AppLanguage("es", R.string.lang_spanish, R.drawable.flag_es),
    AppLanguage("fr", R.string.lang_french, R.drawable.flag_fr),
    AppLanguage("hi", R.string.lang_hindi, R.drawable.flag_hi),
    AppLanguage("pt", R.string.lang_portuguese, R.drawable.flag_pt),
    AppLanguage("id", R.string.lang_indonesian, R.drawable.flag_id),
)

/**
 * Chọn ngôn ngữ (Figma 22:944 lúc mở app lần đầu, 22:878 trong Setting, có nút Back).
 * Nút tick lưu lựa chọn bằng AppCompatDelegate.setApplicationLocales (Android 13+ do hệ thống lưu,
 * thấp hơn do AppLocalesMetadataHolderService).
 */
class LanguageFragment : Fragment() {

    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    private val fromSettings get() = arguments?.getBoolean(ARG_FROM_SETTINGS) ?: false
    private var selectedTag = DEFAULT_TAG
    private val rows = mutableListOf<Pair<AppLanguage, ItemLanguageBinding>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)
        selectedTag = savedInstanceState?.getString(KEY_SELECTED) ?: currentTag()

        binding.btnBack.visibility = if (fromSettings) View.VISIBLE else View.GONE
        if (fromSettings) {
            // Trong Setting: tiêu đề nằm giữa như các màn con khác (Figma 22:878).
            val inset = resources.getDimensionPixelSize(R.dimen.language_title_inset)
            binding.title.layoutParams = (binding.title.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = 0.5f
                marginStart = inset
                marginEnd = inset
            }
        }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirm.setOnClickListener { confirm() }

        APP_LANGUAGES.forEach { language ->
            val row = ItemLanguageBinding.inflate(layoutInflater, binding.list, false)
            row.ivFlag.setImageResource(language.flagRes)
            row.tvName.setText(language.nameRes)
            row.root.setOnClickListener {
                selectedTag = language.tag
                renderSelection()
            }
            binding.list.addView(row.root)
            rows += language to row
        }
        renderSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED, selectedTag)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rows.clear()
        _binding = null
    }

    private fun renderSelection() {
        rows.forEach { (language, row) -> row.root.isSelected = language.tag == selectedTag }
    }

    private fun confirm() {
        val nav = findNavController()
        // Đi tiếp trước rồi mới đổi ngôn ngữ: setApplicationLocales tạo lại Activity,
        // back stack của Navigation được khôi phục theo màn đích.
        if (fromSettings) nav.navigateUp() else nav.navigate(R.id.action_language_to_intro)
        val wanted = LocaleListCompat.forLanguageTags(selectedTag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }

    /** Ngôn ngữ đang dùng: lựa chọn đã lưu, không có thì ngôn ngữ máy nếu app hỗ trợ, không thì English. */
    private fun currentTag(): String {
        val saved = AppCompatDelegate.getApplicationLocales()
        val language = if (!saved.isEmpty) saved[0]?.language else Locale.getDefault().language
        val tag = if (language == "in") "id" else language
        return APP_LANGUAGES.firstOrNull { it.tag == tag }?.tag ?: DEFAULT_TAG
    }

    companion object {
        const val ARG_FROM_SETTINGS = "from_settings"
        private const val KEY_SELECTED = "selected_language"
        private const val DEFAULT_TAG = "en"
    }
}
