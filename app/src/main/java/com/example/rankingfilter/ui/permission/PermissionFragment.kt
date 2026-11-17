package com.example.rankingfilter.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.rankingfilter.R
import com.example.rankingfilter.databinding.FragmentPermissionBinding
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Xin quyền (Figma 3:7): công tắc Camera, Microphone, và Storage (chỉ Android 9-). Chạm vào = xin quyền; đã bị từ chối hẳn
 * hoặc đã cấp thì mở trang cài đặt của app (Android không cho app tự thu hồi quyền).
 * Continue luôn đi tiếp: từng màn (Camera, Add Photo) sẽ xin lại khi cần.
 */
class PermissionFragment : Fragment() {

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    /**
     * Chỉ Android 9 trở xuống cần quyền bộ nhớ (nút Download chép video ra Movies). Từ Android 10 lưu qua
     * MediaStore không cần quyền; chọn ảnh dùng Photo Picker nên không bao giờ cần quyền Ảnh.
     */
    private val needsStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    private val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE

    /** Quyền đã từng được hỏi trong màn này: bị từ chối mà không còn giải thích được = từ chối hẳn. */
    private val askedOnce = mutableSetOf<String>()

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        renderSwitches()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)
        bindRow(binding.rowCamera, binding.switchCamera, Manifest.permission.CAMERA)
        binding.rowStorage.visibility = if (needsStorage) View.VISIBLE else View.GONE
        if (needsStorage) bindRow(binding.rowStorage, binding.switchStorage, storagePermission)
        bindRow(binding.rowMicrophone, binding.switchMicrophone, Manifest.permission.RECORD_AUDIO)
        binding.btnContinue.setOnClickListener { findNavController().navigate(R.id.action_permission_to_home) }
    }

    override fun onStart() {
        super.onStart()
        renderSwitches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindRow(row: View, toggle: MaterialSwitch, permission: String) {
        // Công tắc chỉ phản ánh trạng thái thật: chạm hàng hay công tắc đều đi qua cùng một đường.
        val click = View.OnClickListener {
            renderSwitches()
            if (granted(permission)) openAppSettings() else request(permission)
        }
        row.setOnClickListener(click)
        toggle.setOnClickListener(click)
    }

    private fun request(permission: String) {
        if (permission in askedOnce && !shouldShowRequestPermissionRationale(permission)) {
            openAppSettings()
        } else {
            askedOnce += permission
            requestPermission.launch(permission)
        }
    }

    private fun renderSwitches() {
        val b = _binding ?: return
        b.switchCamera.isChecked = granted(Manifest.permission.CAMERA)
        if (needsStorage) b.switchStorage.isChecked = granted(storagePermission)
        b.switchMicrophone.isChecked = granted(Manifest.permission.RECORD_AUDIO)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)),
        )
    }
}
