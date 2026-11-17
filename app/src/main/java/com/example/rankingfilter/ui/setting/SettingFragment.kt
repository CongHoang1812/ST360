package com.example.rankingfilter.ui.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.example.rankingfilter.data.AppPrefs
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.AppConfig
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentSettingBinding
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.example.rankingfilter.ui.language.LanguageFragment

/** Setting (Figma 18:170): Language, Rate us (popup RateDialogFragment, Figma 4034:33), Share app, Privacy & Policy. */
class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private val playStoreWebUrl get() = "https://play.google.com/store/apps/details?id=${requireContext().packageName}"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.rowLanguage.setOnClickListener {
            findNavController().navigate(
                R.id.action_setting_to_language,
                bundleOf(LanguageFragment.ARG_FROM_SETTINGS to true),
            )
        }
        binding.rowRate.isVisible = !AppPrefs.isRated(requireContext())
        RateDialogFragment.listen(this) { binding.rowRate.isVisible = false }
        binding.rowRate.setOnClickListener { RateDialogFragment.show(this) }
        binding.rowShare.setOnClickListener { shareApp() }
        binding.rowPrivacy.setOnClickListener { openUrl(AppConfig.PRIVACY_POLICY_URL) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text, playStoreWebUrl))
        }
        startActivity(Intent.createChooser(send, getString(R.string.setting_share)))
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(requireContext(), R.string.setting_link_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.setting_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
