package com.funny.rank.filters.ui.common

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.funny.rank.filters.R
import com.funny.rank.filters.databinding.DialogConfirmBinding

/**
 * Popup xác nhận theo design: "Leave without saving?" (Figma 31:2470) và "Delete Filter?" (31:2492).
 * Kết quả trả qua Fragment Result API (giữ được khi xoay màn hình):
 * chỉ gửi khi bấm nút chính, kèm [KEY_PAYLOAD] nếu có.
 */
class ConfirmDialogFragment : DialogFragment() {

    enum class Style { PRIMARY, DESTRUCTIVE }

    private var _binding: DialogConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        binding.tvTitle.setText(args.getInt(ARG_TITLE))
        binding.tvMessage.setText(args.getInt(ARG_MESSAGE))
        binding.btnNegative.setText(args.getInt(ARG_NEGATIVE))
        binding.btnPositive.setText(args.getInt(ARG_POSITIVE))

        if (args.getString(ARG_STYLE) == Style.DESTRUCTIVE.name) {
            binding.btnPositive.setBackgroundResource(R.drawable.bg_dialog_button_red)
            binding.btnPositive.setTextColor(Color.RED)
        }

        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener {
            setFragmentResult(
                args.getString(ARG_REQUEST_KEY)!!,
                bundleOf(KEY_PAYLOAD to args.getString(ARG_PAYLOAD)),
            )
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply { setCanceledOnTouchOutside(true) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(DIM_AMOUNT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val DIM_AMOUNT = 0.3f

        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_NEGATIVE = "negative"
        private const val ARG_POSITIVE = "positive"
        private const val ARG_STYLE = "style"
        private const val ARG_PAYLOAD = "payload"

        fun show(
            host: Fragment,
            requestKey: String,
            @StringRes title: Int,
            @StringRes message: Int,
            @StringRes negative: Int,
            @StringRes positive: Int,
            style: Style = Style.PRIMARY,
            payload: String? = null,
        ) {
            val fm = host.childFragmentManager
            if (fm.findFragmentByTag(requestKey) != null) return
            ConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_REQUEST_KEY to requestKey,
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_NEGATIVE to negative,
                    ARG_POSITIVE to positive,
                    ARG_STYLE to style.name,
                    ARG_PAYLOAD to payload,
                )
            }.show(fm, requestKey)
        }

        /** Đăng ký nhận kết quả (gọi trong onViewCreated của Fragment chủ). */
        fun listen(host: Fragment, requestKey: String, onConfirmed: (payload: String?) -> Unit) {
            host.childFragmentManager.setFragmentResultListener(requestKey, host.viewLifecycleOwner) { _, bundle ->
                onConfirmed(bundle.getString(KEY_PAYLOAD))
            }
        }
    }
}
