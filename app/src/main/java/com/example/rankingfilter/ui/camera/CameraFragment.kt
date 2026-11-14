package com.example.rankingfilter.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.rankingfilter.R
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.databinding.FragmentCameraBinding
import com.example.rankingfilter.databinding.ItemRankSlotBinding
import com.example.rankingfilter.ui.camera.face.FaceAnalyzer
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Camera (Figma 5:9).
 * - 5a: giao diện, preview CameraX, lật camera, flash, xin quyền, tải ảnh filter.
 * - 5b: nhận diện mặt (ImageAnalysis + ML Kit) và vẽ ảnh vuông trên đầu ([FaceOverlayView]).
 * Trò chơi xếp hạng (5c) và quay video (5d) làm ở các bước sau.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var torchState: LiveData<Int>? = null
    private var cameraStarted = false

    // Nhận diện mặt: luồng riêng cho ImageAnalysis, tạo lại mỗi lần tạo view.
    private var analysisExecutor: ExecutorService? = null
    private var faceAnalyzer: FaceAnalyzer? = null

    /** 12 ô hạng ở cột trái, theo thứ tự hạng 1..12. */
    private val rankSlots = mutableListOf<ItemRankSlotBinding>()

    /** Đã tự hỏi quyền một lần trong lần mở màn này (không hỏi lặp lại mỗi lần onStart). */
    private var permissionAsked = false

    /** Người dùng đã chọn "Không hỏi lại": chỉ còn cách mở Settings. */
    private var permanentlyDenied = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (_binding == null) return@registerForActivityResult
            if (hasAllPermissions()) {
                showCamera()
            } else {
                permanentlyDenied = missingPermissions().any { !shouldShowRequestPermissionRationale(it) }
                showPermissionPanel()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionAsked = savedInstanceState?.getBoolean(KEY_PERMISSION_ASKED) ?: false
        permanentlyDenied = savedInstanceState?.getBoolean(KEY_PERMANENTLY_DENIED) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        buildRankSlots()
        setUpDurationStrip()
        setUpFaceDetection()

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnFlip.setOnClickListener { flipCamera() }
        binding.btnFlash.setOnClickListener { toggleTorch() }
        binding.btnChooseFilter.setOnClickListener { openChooseFilter() }
        // Add Sound làm ở giai đoạn 6, quay video ở bước 5d.
        binding.btnAddSound.setOnClickListener { toast(R.string.coming_soon) }
        binding.btnRecord.setOnClickListener { toast(R.string.coming_soon) }
        binding.btnPermission.setOnClickListener { onPermissionButtonClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLightStatusBarIcons(false)
        // Kiểm tra lại mỗi lần quay về màn (ví dụ sau khi cấp quyền trong Settings).
        when {
            hasAllPermissions() -> showCamera()
            !permissionAsked -> {
                permissionAsked = true
                requestPermissions.launch(REQUIRED_PERMISSIONS)
            }
            else -> showPermissionPanel()
        }
    }

    override fun onStop() {
        super.onStop()
        setLightStatusBarIcons(true)
        // Camera đã dừng nên không còn khung hình nào về nữa; ẩn ảnh để lần sau không hiện vị trí cũ.
        _binding?.faceOverlay?.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_ASKED, permissionAsked)
        outState.putBoolean(KEY_PERMANENTLY_DENIED, permanentlyDenied)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Camera gắn với viewLifecycleOwner nên CameraX tự đóng khi view bị hủy.
        faceAnalyzer?.close()
        faceAnalyzer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        rankSlots.clear()
        torchState = null
        camera = null
        cameraProvider = null
        cameraStarted = false
        _binding = null
    }

    // ---------------------------------------------------------------- Giao diện

    private fun render(state: CameraUiState) {
        durationViews().forEach { (duration, view) -> view.isSelected = duration == state.duration }
    }

    private fun onEvent(event: CameraEvent) {
        when (event) {
            CameraEvent.FilterMissing -> {
                toast(R.string.camera_filter_missing)
                findNavController().navigateUp()
            }
        }
    }

    /** Tạo 12 ô số bằng vòng lặp; kích thước lấy từ dimens và được tính lại khi biết chiều cao cột. */
    private fun buildRankSlots() {
        val column = binding.rankColumn
        column.removeAllViews()
        rankSlots.clear()
        repeat(FilterDto.PHOTO_COUNT) { index ->
            val slot = ItemRankSlotBinding.inflate(layoutInflater, column, false)
            slot.tvNumber.text = (index + 1).toString()
            slot.root.contentDescription = getString(R.string.cd_rank_slot, index + 1)
            column.addView(slot.root)
            rankSlots += slot
        }
        sizeRankSlots(column.height)
        column.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            // Đổi LayoutParams trong lúc đang layout thì bị bỏ qua, nên dời sang frame sau.
            if (bottom - top != oldBottom - oldTop) v.post { if (_binding != null) sizeRankSlots(bottom - top) }
        }
    }

    /**
     * Ô 36dp cách nhau 6dp (cột cao 498dp). Màn thấp không đủ chỗ thì thu nhỏ ô
     * để 12 ô không đè lên dải thời lượng.
     */
    private fun sizeRankSlots(availableHeight: Int) {
        val maxSize = resources.getDimensionPixelSize(R.dimen.rank_slot_size)
        val gap = resources.getDimensionPixelSize(R.dimen.rank_slot_gap)
        val maxText = resources.getDimension(R.dimen.rank_slot_text)
        val count = rankSlots.size
        val size = if (availableHeight <= 0) {
            maxSize
        } else {
            min(maxSize, (availableHeight - gap * (count - 1)) / count).coerceAtLeast(1)
        }
        rankSlots.forEachIndexed { index, slot ->
            slot.root.updateLayoutParams<LinearLayout.LayoutParams> {
                width = size
                height = size
                topMargin = if (index == 0) 0 else gap
            }
            slot.tvNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxText * size / maxSize)
        }
    }

    private fun durationViews(): Map<RecordDuration, TextView> = mapOf(
        RecordDuration.SEC_60 to binding.duration60,
        RecordDuration.SEC_30 to binding.duration30,
        RecordDuration.FREE to binding.durationFree,
        RecordDuration.SEC_15 to binding.duration15,
    )

    private fun setUpDurationStrip() {
        durationViews().forEach { (duration, view) ->
            duration.seconds?.let { view.text = getString(R.string.camera_duration_seconds, it) }
            view.setOnClickListener { viewModel.setDuration(duration) }
        }
    }

    /** Chữ và icon thanh trạng thái màu trắng khi ở màn Camera (nền là hình camera), trả lại khi rời màn. */
    private fun setLightStatusBarIcons(darkIcons: Boolean) {
        val window = requireActivity().window
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkIcons
    }

    private fun openChooseFilter() {
        val nav = findNavController()
        // Thường vào Camera từ Choose a Filter: quay lại đúng màn đó. Nếu vào từ đường khác thì mở mới.
        if (!nav.popBackStack(R.id.chooseFilterFragment, false)) {
            nav.navigate(
                R.id.chooseFilterFragment,
                null,
                navOptions { popUpTo(R.id.cameraFragment) { inclusive = true } },
            )
        }
    }

    // ---------------------------------------------------------------- Quyền

    private fun missingPermissions(): List<String> = REQUIRED_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllPermissions(): Boolean = missingPermissions().isEmpty()

    private fun showPermissionPanel() {
        binding.faceOverlay.clear()
        binding.cameraControls.isVisible = false
        binding.permissionPanel.isVisible = true
        binding.btnPermission.setText(
            if (permanentlyDenied) R.string.camera_permission_settings else R.string.camera_permission_allow
        )
    }

    private fun onPermissionButtonClicked() {
        if (permanentlyDenied) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null),
                )
            )
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }
    }

    // ---------------------------------------------------------------- CameraX

    private fun showCamera() {
        binding.permissionPanel.isVisible = false
        binding.cameraControls.isVisible = true
        if (cameraStarted) return
        cameraStarted = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                cameraProvider = ProcessCameraProvider.awaitInstance(requireContext())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Không khởi tạo được CameraX", e)
                cameraStarted = false
                toast(R.string.camera_open_error)
                return@launch
            }
            // ViewPort chỉ có sau khi PreviewView đã đo xong.
            _binding?.previewView?.doOnLayout { bindCamera() }
        }
    }

    /** Gắn Preview + ImageAnalysis với camera đang chọn; gọi lại mỗi khi lật camera. */
    private fun bindCamera() {
        val binding = _binding ?: return
        val provider = cameraProvider ?: return
        val wanted = viewModel.state.value.lensFacing
        val available = listOf(wanted, otherLens(wanted)).filter { provider.hasLens(it) }
        val lensFacing = available.firstOrNull() ?: run {
            toast(R.string.camera_open_error)
            return
        }
        if (lensFacing != wanted) viewModel.setLensFacing(lensFacing)

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            // Khung hình đến khi ML Kit còn bận thì bỏ, chỉ giữ khung mới nhất: ảnh trên đầu không bị trễ.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { use ->
                val executor = analysisExecutor
                val analyzer = faceAnalyzer
                if (executor != null && analyzer != null) use.setAnalyzer(executor, analyzer)
            }

        // Preview và ImageAnalysis phải dùng chung ViewPort: cùng nhìn một vùng cảm biến,
        // nếu không khung mặt đổi sang tọa độ view sẽ bị lệch (xem FaceAnalyzer).
        val viewPort = binding.previewView.viewPort
        if (viewPort == null) Log.w(TAG, "PreviewView chưa có ViewPort: ảnh trên đầu có thể bị lệch")
        val useCases = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analysis)
            .apply { viewPort?.let(::setViewPort) }
            .build()

        binding.faceOverlay.clear()
        val bound = try {
            provider.unbindAll()
            provider.bindToLifecycle(viewLifecycleOwner, selectorFor(lensFacing), useCases)
        } catch (e: Exception) {
            Log.e(TAG, "Không mở được camera $lensFacing", e)
            toast(R.string.camera_open_error)
            return
        }
        camera = bound

        // Chỉ lật được khi máy có cả camera trước và sau.
        setButtonEnabled(binding.btnFlip, available.size > 1)

        // Flash ở màn quay video = đèn pin (torch). Camera trước thường không có đèn.
        setButtonEnabled(binding.btnFlash, bound.cameraInfo.hasFlashUnit())
        torchState?.removeObservers(viewLifecycleOwner)
        torchState = bound.cameraInfo.torchState.also { live ->
            live.observe(viewLifecycleOwner) { binding.btnFlash.isSelected = it == TorchState.ON }
        }
    }

    /** Tạo bộ nhận diện mặt và đặt ảnh chờ ("?" theo design) lên đầu; 5c sẽ thay bằng ảnh của filter. */
    private fun setUpFaceDetection() {
        binding.faceOverlay.photo = BitmapFactory.decodeResource(resources, R.drawable.head_placeholder)
        analysisExecutor = Executors.newSingleThreadExecutor()
        faceAnalyzer = FaceAnalyzer(
            // outputTransform chỉ gọi được trên luồng chính; FaceAnalyzer chỉ gọi lambda này ở luồng chính.
            previewTransform = { _binding?.previewView?.outputTransform },
            onFace = { face -> _binding?.faceOverlay?.onFace(face) },
        )
    }

    private fun flipCamera() {
        viewModel.setLensFacing(otherLens(viewModel.state.value.lensFacing))
        bindCamera()
    }

    private fun toggleTorch() {
        val current = camera ?: return
        if (!current.cameraInfo.hasFlashUnit()) return
        current.cameraControl.enableTorch(!binding.btnFlash.isSelected)
    }

    private fun ProcessCameraProvider.hasLens(lensFacing: Int): Boolean = try {
        hasCamera(selectorFor(lensFacing))
    } catch (e: Exception) {
        false
    }

    private fun selectorFor(lensFacing: Int): CameraSelector =
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

    private fun otherLens(lensFacing: Int): Int =
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

    private fun setButtonEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    private fun toast(textRes: Int) = Toast.makeText(requireContext(), textRes, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "CameraFragment"
        private const val KEY_PERMISSION_ASKED = "permission_asked"
        private const val KEY_PERMANENTLY_DENIED = "permanently_denied"
        private const val DISABLED_ALPHA = 0.4f
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
