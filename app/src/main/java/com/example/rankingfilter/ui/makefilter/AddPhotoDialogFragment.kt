package com.example.rankingfilter.ui.makefilter

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.rankingfilter.databinding.DialogAddPhotoBinding
import com.example.rankingfilter.ui.common.ConfirmDialogFragment

/** Popup "Add Photo" (Figma 23:1135): Take a Photo / Upload a Photo, nút đóng tròn bên dưới. */
class AddPhotoDialogFragment : DialogFragment() {

    enum class Choice { CAMERA, GALLERY }

    private var _binding: DialogAddPhotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnTakePhoto.setOnClickListener { choose(Choice.CAMERA) }
        binding.btnUploadPhoto.setOnClickListener { choose(Choice.GALLERY) }
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun choose(choice: Choice) {
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_CHOICE to choice.name))
        dismiss()
    }

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
        _binding = null
    }

    companion object {
        const val TAG = "add_photo"
        const val REQUEST_KEY = "add_photo_choice"
        const val KEY_CHOICE = "choice"

        fun choiceFrom(bundle: Bundle): Choice? =
            bundle.getString(KEY_CHOICE)?.let { runCatching { Choice.valueOf(it) }.getOrNull() }
    }
}
