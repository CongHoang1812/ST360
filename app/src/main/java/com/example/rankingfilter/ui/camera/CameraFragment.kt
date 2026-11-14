package com.example.rankingfilter.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
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
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
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
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.rankingfilter.R
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.databinding.FragmentCameraBinding
import com.example.rankingfilter.databinding.ItemRankSlotBinding
import com.example.rankingfilter.ui.camera.face.FaceAnalyzer
import com.example.rankingfilter.ui.common.applySystemBarsPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Camera (Figma 5:9, đang quay 8:102 / 8:192 / 8:235).
 * - 5a: giao diện, preview CameraX, lật camera, flash, xin quyền, tải ảnh filter.
 * - 5b: nhận diện mặt (ImageAnalysis + ML Kit) và vẽ ảnh vuông trên đầu ([com.example.rankingfilter.ui.camera.face.FaceOverlayView]).
 * - 5c: trò chơi xếp hạng. Bấm nút quay → bắt đầu ván: ảnh trên đầu đổi ngẫu nhiên, chạm ô trống để chốt.
 *   Xếp đủ 12 ô thì chuyển sang màn Preview. Quay video thật (5d) sẽ gắn vào đúng các điểm bắt đầu / dừng này.
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

    /** 12 dòng hạng ở cột trái, theo thứ tự hạng 1..12. */
    private val rankSlots = mutableListOf<ItemRankSlotBinding>()

    /** Ảnh "?" hiện trên đầu khi chưa chơi (Figma 5:13). */
    private var placeholder: Bitmap? = null

    /** Camera đang hiện (đã có quyền); false = đang hiện bảng xin quyền. */
    private var cameraVisible = false

    /** Trạng thái trò chơi lần vẽ gần nhất; vòng spin và bấm ô đọc từ đây. */
    private var game = GameUi()

    /** Đang chờ một chút rồi chuyển sang màn Preview. */
    private var finishJob: Job? = null

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
        // Add Sound làm ở giai đoạn 6.
        binding.btnAddSound.setOnClickListener { toast(R.string.coming_soon) }
        binding.btnRecord.setOnClickListener { onRecordClicked() }
        binding.btnRestart.setOnClickListener { viewModel.restartGame() }
        binding.btnPermission.setOnClickListener { onPermissionButtonClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
                launch { spinLoop() }
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
        cameraVisible = false
        finishJob = null
        game = GameUi()
        _binding = null
    }

    // ---------------------------------------------------------------- Trò chơi

    /** Bấm nút quay: chưa chơi thì bắt đầu ván mới, đang chơi thì dừng (bỏ ván). */
    private fun onRecordClicked() {
        if (game.isActive) {
            viewModel.stopGame()
            return
        }
        when (viewModel.startGame()) {
            GameStart.STARTED -> Unit
            GameStart.FILTER_NOT_READY -> Unit // ảnh filter còn đang tải
            GameStart.NOT_ENOUGH_PHOTOS -> toast(R.string.camera_filter_incomplete)
        }
    }

    /**
     * Chạm ô hạng: chốt ảnh đang hiện trên đầu vào ô đó. Chỉ chốt khi đang thấy mặt, vì lúc mất mặt
     * ảnh bị ẩn: người chơi không biết mình đang chốt ảnh nào, mà đã chốt thì không sửa được.
     */
    private fun onSlotClicked(index: Int) {
        if (!game.isPlaying || !binding.faceOverlay.hasFace) return
        if (viewModel.placePhoto(index)) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Đổi ảnh trên đầu mỗi [SPIN_INTERVAL_MS]; chỉ chạy khi màn đang hiện (cùng vòng đời với camera). */
    private suspend fun spinLoop() {
        while (currentCoroutineContext().isActive) {
            delay(SPIN_INTERVAL_MS)
            val overlay = _binding?.faceOverlay ?: return
            if (game.isPlaying && overlay.hasFace) viewModel.spin()
        }
    }

    /** Xếp đủ 12 ô: dừng ván, chờ một chút cho người chơi thấy ô cuối rồi sang màn Preview. */
    private fun onGameFinished() {
        if (finishJob?.isActive == true) return
        finishJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(FINISH_DELAY_MS)
            // Chuyển màn khi Fragment đã ở trạng thái cho phép (tránh lỗi nếu app vừa bị đưa xuống nền).
            viewLifecycleOwner.lifecycle.withStarted {
                // Về lại Camera từ màn Preview thì ván mới ở trạng thái chưa chơi.
                viewModel.stopGame()
                findNavController().navigate(R.id.action_camera_to_preview)
            }
        }
    }

    // ---------------------------------------------------------------- Giao diện

    private fun render(state: CameraUiState) {
        durationViews().forEach { (duration, view) -> view.isSelected = duration == state.duration }
        game = state.game
        renderGame(state.game)
    }

    private fun renderGame(ui: GameUi) {
        updateControls()

        // Ảnh trên đầu: ảnh chờ "?" (vuông) khi chưa chơi, ảnh của filter (bo góc) khi đang chơi.
        binding.faceOverlay.cornerRatio = if (ui.isActive) PHOTO_CORNER_RATIO else 0f
        binding.faceOverlay.photo = if (ui.isActive) ui.head else placeholder

        // Nút quay: chấm tròn → hình vuông bo góc 32dp khi đang chơi (Figma 8:191).
        val dotSize = resources.getDimensionPixelSize(if (ui.isActive) R.dimen.record_stop_inner else R.dimen.record_button_inner)
        binding.recordDot.setBackgroundResource(if (ui.isActive) R.drawable.bg_record_stop else R.drawable.bg_record_dot)
        if (binding.recordDot.layoutParams.width != dotSize) {
            binding.recordDot.updateLayoutParams { width = dotSize; height = dotSize }
        }
        binding.btnRecord.contentDescription = getString(if (ui.isActive) R.string.cd_stop else R.string.cd_record)

        rankSlots.forEachIndexed { index, slot -> renderSlot(index, slot, ui.slots.getOrNull(index), ui.isActive) }

        if (ui.isFinished) onGameFinished()
    }

    /**
     * Ô đã chốt: ảnh nhỏ nằm trong ô, số hạng đứng bên trái ô (Figma 8:192).
     * Ô trống: số nằm trong ô; đang chơi thì có viền trắng (Figma 8:235).
     */
    private fun renderSlot(index: Int, slot: ItemRankSlotBinding, photo: Bitmap?, active: Boolean) {
        val filled = photo != null
        slot.ivPhoto.isVisible = filled
        slot.tvNumber.isVisible = !filled
        slot.tvRank.visibility = if (filled) View.VISIBLE else View.INVISIBLE
        slot.tvRank.text = (index + 1).toString()
        slot.box.setBackgroundResource(if (active) R.drawable.bg_rank_slot_playing else R.drawable.bg_rank_slot)
        if (slot.ivPhoto.tag !== photo) {
            slot.ivPhoto.tag = photo
            slot.ivPhoto.setImageBitmap(photo)
        }
    }

    /**
     * Điều khiển nào hiện lúc nào:
     * - bảng xin quyền: ẩn hết;
     * - camera, chưa chơi: đủ bộ nút;
     * - đang chơi (Figma 8:102): chỉ còn cột hạng, nút dừng và nút làm lại. Nút Back cũng ẩn (vẫn bấm Back hệ thống được).
     */
    private fun updateControls() {
        val b = binding
        val idle = cameraVisible && !game.isActive
        val playing = cameraVisible && game.isActive
        b.btnBack.isVisible = !game.isActive
        b.btnAddSound.isVisible = idle
        b.btnFlip.isVisible = idle
        b.btnFlash.isVisible = idle
        b.durationStrip.isVisible = idle
        b.btnChooseFilter.isVisible = idle
        b.rankColumn.isVisible = cameraVisible
        b.btnRecord.isVisible = cameraVisible
        b.btnRestart.isVisible = playing
    }

    private fun onEvent(event: CameraEvent) {
        when (event) {
            CameraEvent.FilterMissing -> {
                toast(R.string.camera_filter_missing)
                findNavController().navigateUp()
            }
        }
    }

    /** Tạo 12 dòng hạng bằng vòng lặp; kích thước lấy từ dimens và được tính lại khi biết chiều cao cột. */
    private fun buildRankSlots() {
        val column = binding.rankColumn
        column.removeAllViews()
        rankSlots.clear()
        repeat(FilterDto.PHOTO_COUNT) { index ->
            val slot = ItemRankSlotBinding.inflate(layoutInflater, column, false)
            slot.tvNumber.text = (index + 1).toString()
            slot.tvRank.text = (index + 1).toString()
            slot.box.contentDescription = getString(R.string.cd_rank_slot, index + 1)
            slot.box.setOnClickListener { onSlotClicked(index) }
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
        val textPx = maxText * size / maxSize
        rankSlots.forEachIndexed { index, slot ->
            slot.root.updateLayoutParams<LinearLayout.LayoutParams> { topMargin = if (index == 0) 0 else gap }
            slot.box.updateLayoutParams<LinearLayout.LayoutParams> {
                width = size
                height = size
            }
            slot.tvNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            slot.tvRank.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
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
        cameraVisible = false
        updateControls()
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
        cameraVisible = true
        updateControls()
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

    /** Tạo bộ nhận diện mặt và đặt ảnh chờ ("?" theo design) lên đầu. */
    private fun setUpFaceDetection() {
        placeholder = BitmapFactory.decodeResource(resources, R.drawable.head_placeholder)
        binding.faceOverlay.photo = placeholder
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

        /** Ảnh trên đầu đổi mỗi ~150ms (sổ tay). */
        private const val SPIN_INTERVAL_MS = 150L

        /** Chờ sau khi chốt ô cuối để người chơi thấy cột hạng đầy, rồi mới sang màn Preview. */
        private const val FINISH_DELAY_MS = 700L

        /** Ảnh của filter trên đầu bo góc ≈ 12% cạnh (Figma 8:192). */
        private const val PHOTO_CORNER_RATIO = 0.12f
    }
}
