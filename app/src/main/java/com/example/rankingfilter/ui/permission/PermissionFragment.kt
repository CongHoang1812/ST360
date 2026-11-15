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
 * Xin quyền (Figma 3:7): công tắc Camera, Photos, Microphone. Chạm vào = xin quyền; đã bị từ chối hẳn
 * hoặc đã cấp thì mở trang cài đặt của app (Android không cho app tự thu hồi quyền).
 * Continue luôn đi tiếp: từng màn (Camera, Add Photo) sẽ xin lại khi cần.
 */
class PermissionFragment : Fragment() {

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    private val photosPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

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
        bindRow(binding.rowPhotos, binding.switchPhotos, photosPermission)
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
        b.switchPhotos.isChecked = granted(photosPermission)
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
