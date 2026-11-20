package com.funny.rank.filters.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.funny.rank.filters.R
import com.funny.rank.filters.data.FilterDto
import com.funny.rank.filters.databinding.FragmentCameraBinding
import com.funny.rank.filters.databinding.ItemRankSlotBinding
import com.funny.rank.filters.ui.camera.face.FaceAnalyzer
import com.funny.rank.filters.ui.camera.face.HeadPlacements
import com.funny.rank.filters.ui.camera.face.HeadSmoother
import com.funny.rank.filters.ui.camera.face.TrackedFace
import com.funny.rank.filters.ui.camera.overlay.CameraOverlay
import com.funny.rank.filters.ui.camera.overlay.OverlayRenderer
import com.funny.rank.filters.ui.camera.overlay.OverlayScene
import com.funny.rank.filters.ui.camera.record.VideoRecorder
import com.funny.rank.filters.data.music.MusicRepository
import com.funny.rank.filters.data.video.VideoLibrary
import com.funny.rank.filters.ui.filter.ChooseFilterFragment
import com.funny.rank.filters.ui.result.PreviewViewModel
import com.funny.rank.filters.ui.sound.AddSoundFragment
import com.funny.rank.filters.ui.sound.AddSoundViewModel
import com.funny.rank.filters.ui.common.applySystemBarsPadding
import com.funny.rank.filters.ui.common.navigateSafe
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Camera (Figma 5:9, đang quay 8:102 / 8:192 / 8:235).
 * - 5a: giao diện, preview CameraX, lật camera, flash, xin quyền, tải ảnh filter.
 * - 5b: nhận diện mặt (ImageAnalysis + ML Kit), tính vị trí ảnh trên đầu.
 * - 5c: trò chơi xếp hạng: ảnh trên đầu đổi ngẫu nhiên, chạm ô trống để chốt.
 * - 5d: quay video ([VideoRecorder]). Ảnh trên đầu và cột hạng được [CameraOverlay] vẽ thẳng lên khung hình
 *   camera nên có trong cả preview lẫn video lưu ra. Một ván chơi = một lần quay:
 *   bấm nút quay → video bắt đầu ghi → ván bắt đầu; dừng bằng tay hoặc hết giờ (xếp đủ 12 ô thì video vẫn quay tiếp) → lưu video → màn Preview.
 */
class CameraFragment : Fragment() {

    /** Vòng đời của một lần quay. Chuyển trạng thái chỉ qua [setRecPhase]. */
    private enum class RecPhase {
        IDLE,
        /** Đã gọi start, chờ CameraX báo khung hình đầu tiên. */
        STARTING,
        RECORDING,
        /** Đã gọi stop, chờ CameraX đóng file. */
        STOPPING,
    }

    private class SlotGeometry(val box: RectF, val rankCenterX: Float)

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var torchState: LiveData<Int>? = null
    private var cameraState: LiveData<CameraState>? = null
    private var lastCameraError: Int? = null
    private var cameraStarted = false

    // Nhận diện mặt: luồng riêng cho ImageAnalysis, tạo lại mỗi lần tạo view.
    private var analysisExecutor: ExecutorService? = null
    private var faceAnalyzer: FaceAnalyzer? = null
    private val headSmoother = HeadSmoother()

    // Vẽ lên khung hình camera và quay video; tạo lại mỗi lần tạo view.
    private var overlay: CameraOverlay? = null
    private var recorder: VideoRecorder? = null
    private var recPhase = RecPhase.IDLE

    /**
     * Nhạc đã chọn, phát trong lúc quay để người chơi nghe nhịp (video lưu ra sẽ thay tiếng micro bằng chính bài này
     * ở màn Preview). Tạo ở onStart khi bài đã có trên máy, giải phóng ở onStop.
     */
    private var musicPlayer: ExoPlayer? = null
    private var musicPlayerFile: File? = null


    /** 12 dòng hạng ở cột trái, theo thứ tự hạng 1..12. Chỉ là vùng chạm; hình do [CameraOverlay] vẽ. */
    private val rankSlots = mutableListOf<ItemRankSlotBinding>()
    private val slotNumbers = List(FilterDto.PHOTO_COUNT) { (it + 1).toString() }

    /** Vị trí các ô trong tọa độ PreviewView; null = cần đo lại (layout vừa đổi). */
    private var slotGeometry: List<SlotGeometry>? = null

    /** Ảnh "?" hiện trên đầu khi chưa chơi (Figma 5:13). */
    private var placeholder: Bitmap? = null

    /** Camera đang hiện (đã có quyền); false = đang hiện bảng xin quyền. */
    private var cameraVisible = false

    /** Trạng thái trò chơi lần vẽ gần nhất; vòng spin và bấm ô đọc từ đây. */
    private var game = GameUi()

    /** Đang chờ một chút (cho video kịp ghi cột hạng đầy) rồi dừng quay. */
    private var finishJob: Job? = null

    private var shownSecond = -1L

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

    private val isBusy: Boolean get() = recPhase != RecPhase.IDLE || game.isActive

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
        setUpOverlay()
        setUpRecorder()
        setUpFaceDetection()

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnFlip.setOnClickListener { flipCamera() }
        binding.btnFlash.setOnClickListener { toggleTorch() }
        binding.btnChooseFilter.setOnClickListener { openChooseFilter() }
        binding.btnAddSound.setOnClickListener { openAddSound() }
        // X trên pill: bỏ nhạc đã chọn, quay lại dùng tiếng micro.
        binding.btnClearMusic.setOnClickListener { viewModel.setMusic(null) }
        listenMusicResult()
        listenFilterResult()
        binding.btnRecord.setOnClickListener { onRecordClicked() }
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
        prepareMusicPlayer()
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
        // Rời màn giữa chừng (về Home, có cuộc gọi…): CameraX sắp đóng camera nên bản ghi không trọn vẹn → bỏ.
        if (recPhase != RecPhase.IDLE) {
            finishJob?.cancel()
            recorder?.stop(keep = false)
        }
        // Camera dừng thì không còn khung hình nào về nữa; ẩn ảnh để lần sau không hiện vị trí cũ.
        headSmoother.clear()
        publishScene()
        releaseMusicPlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_ASKED, permissionAsked)
        outState.putBoolean(KEY_PERMANENTLY_DENIED, permanentlyDenied)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Bản ghi dở bị bỏ; callback đóng file đến sau sẽ không còn ai nghe (xem VideoRecorder.release).
        recorder?.release()
        recorder = null
        recPhase = RecPhase.IDLE
        finishJob = null
        // ViewModel sống qua xoay màn hình, nhưng ván gắn với bản ghi vừa bỏ nên cũng dừng.
        viewModel.stopGame()
        game = GameUi()

        // Camera gắn với viewLifecycleOwner nên CameraX tự đóng khi view bị hủy.
        faceAnalyzer?.close()
        faceAnalyzer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        headSmoother.clear()
        overlay?.close()
        overlay = null

        rankSlots.clear()
        slotGeometry = null
        torchState = null
        cameraState = null
        lastCameraError = null
        camera = null
        cameraProvider = null
        cameraStarted = false
        cameraVisible = false
        shownSecond = -1L
        _binding = null
    }

    // ---------------------------------------------------------------- Quay video + trò chơi

    /** Nút quay: chưa quay thì bắt đầu, đang quay thì dừng và lưu. */
    private fun onRecordClicked() {
        when (recPhase) {
            RecPhase.IDLE -> beginRecording()
            RecPhase.RECORDING -> finishRecording()
            RecPhase.STARTING, RecPhase.STOPPING -> Unit
        }
    }

    private fun beginRecording() {
        val current = recorder ?: return
        if (camera == null) return
        // Quyền có thể bị thu hồi trong Settings khi app ở nền.
        if (!hasAllPermissions()) {
            showPermissionPanel()
            return
        }
        when (viewModel.checkGameReady()) {
            GameStart.STARTED -> Unit
            GameStart.FILTER_NOT_READY -> return // ảnh filter còn đang tải
            GameStart.NOT_ENOUGH_PHOTOS -> {
                toast(R.string.camera_filter_incomplete)
                return
            }
        }
        val limitMs = viewModel.state.value.duration.seconds?.let { it * 1000L }
        val failure = current.start(limitMs, VideoLibrary.newDraft(requireContext()))
        if (failure != null) {
            showRecordFailure(failure)
            return
        }
        showElapsed(0)
        setRecPhase(RecPhase.STARTING)
    }

    /** Dừng quay và GIỮ video (bấm dừng, hoặc xếp xong). Hết giờ thì Recorder tự dừng, không qua đây. */
    private fun finishRecording() {
        if (recPhase != RecPhase.RECORDING && recPhase != RecPhase.STARTING) return
        finishJob?.cancel()
        finishJob = null
        setRecPhase(RecPhase.STOPPING)
        recorder?.stop(keep = true)
    }

    private val recorderListener = object : VideoRecorder.Listener {
        override fun onStarted() {
            if (_binding == null) return
            setRecPhase(RecPhase.RECORDING)
            // Ván bắt đầu đúng lúc video bắt đầu ghi, để video có trọn ván.
            viewModel.startGame()
            // Nhạc cũng bắt đầu từ giây 0, trùng với lúc nhạc bắt đầu trong video lưu ra.
            musicPlayer?.let {
                it.seekTo(0)
                it.play()
            }
        }

        override fun onProgress(elapsedMs: Long) {
            if (_binding != null) showElapsed(elapsedMs)
        }

        override fun onSaved(file: File) {
            if (_binding == null) {
                // Màn đã rời đi: không ai xem bản nháp này nữa.
                VideoLibrary.deleteDraft(file)
                return
            }
            resetAfterRecording()
            val musicId = viewModel.state.value.music?.id ?: -1
            // Chuyển màn khi Fragment ở trạng thái cho phép (callback có thể đến lúc app vừa xuống nền).
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.withStarted {
                    findNavController().navigateSafe(
                        R.id.action_camera_to_preview,
                        bundleOf(
                            PreviewViewModel.ARG_VIDEO_PATH to file.absolutePath,
                            PreviewViewModel.ARG_MUSIC_ID to musicId,
                        ),
                    )
                }
            }
        }

        override fun onDiscarded() {
            if (_binding == null) return
            resetAfterRecording()
        }

        override fun onFailed(failure: VideoRecorder.Failure) {
            if (_binding == null) return
            resetAfterRecording()
            showRecordFailure(failure)
        }
    }

    /** Bản ghi đã kết thúc (lưu, bỏ hoặc lỗi): về trạng thái chưa quay, chưa chơi. */
    private fun resetAfterRecording() {
        musicPlayer?.pause()
        finishJob?.cancel()
        finishJob = null
        viewModel.stopGame()
        setRecPhase(RecPhase.IDLE)
    }

    private fun showRecordFailure(failure: VideoRecorder.Failure) {
        when (failure) {
            VideoRecorder.Failure.NO_STORAGE -> toast(R.string.record_error_storage)
            VideoRecorder.Failure.CAMERA_INTERRUPTED -> toast(R.string.record_error_camera)
            VideoRecorder.Failure.MIC_PERMISSION -> showPermissionPanel()
            VideoRecorder.Failure.UNKNOWN -> toast(R.string.record_error_generic)
            // Bấm dừng ngay khi vừa bắt đầu: chưa có gì để lưu, không cần báo.
            VideoRecorder.Failure.NO_DATA -> Unit
        }
    }

    private fun setRecPhase(phase: RecPhase) {
        recPhase = phase
        updateControls()
        publishScene()
    }

    /** Đồng hồ 00:00:10 (Figma 14:685); chỉ đổi chữ khi sang giây mới. */
    private fun showElapsed(elapsedMs: Long) {
        val seconds = elapsedMs / 1000
        if (seconds == shownSecond) return
        shownSecond = seconds
        binding.tvTimer.text =
            String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    /**
     * Chạm ô hạng: chốt ảnh đang hiện trên đầu vào ô đó. Chỉ chốt khi đang thấy mặt, vì lúc mất mặt
     * ảnh bị ẩn: người chơi không biết mình đang chốt ảnh nào, mà đã chốt thì không sửa được.
     */
    private fun onSlotClicked(index: Int) {
        if (recPhase != RecPhase.RECORDING || !game.isPlaying || !headSmoother.hasFace) return
        // Chỉ chốt được khi ảnh đã dừng; đang chạy thì chạm ô số không có tác dụng.
        if (!spinStopped) return
        if (viewModel.placePhoto(index)) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Đã chốt: ảnh chạy tiếp, lần dừng sau cách một khoảng ngẫu nhiên khác.
            resetSpinCycle()
        }
    }

    /** Ảnh filter đang nháy trên đầu lúc chưa quay (xem trước); null = chưa có ảnh nào. */
    private var previewPhoto: Bitmap? = null
    private var previewElapsedMs = 0L

    /** Lúc chưa quay: mỗi [PREVIEW_INTERVAL_MS] đổi ngẫu nhiên một ảnh của filter trên đầu. */
    private fun previewTick() {
        previewElapsedMs += SPIN_INTERVAL_MS
        if (previewElapsedMs < PREVIEW_INTERVAL_MS) return
        previewElapsedMs = 0L
        if (!headSmoother.hasFace) return
        val photos = viewModel.state.value.filter?.photos.orEmpty()
        if (photos.isEmpty()) return
        previewPhoto = photos.random()
        publishScene()
    }

    /** Ảnh đang dừng (chờ người chơi chọn ô số). Chỉ đọc/ghi trên luồng chính. */
    private var spinStopped = false
    private var spinElapsedMs = 0L
    private var spinTargetMs = nextSpinTarget()

    private fun nextSpinTarget() = Random.nextLong(SPIN_MIN_MS, SPIN_MAX_MS + 1)

    private fun resetSpinCycle() {
        spinStopped = false
        spinElapsedMs = 0L
        spinTargetMs = nextSpinTarget()
    }

    /**
     * Ảnh trên đầu đổi mỗi [SPIN_INTERVAL_MS]; sau một khoảng ngẫu nhiên ([SPIN_MIN_MS]..[SPIN_MAX_MS]) thì dừng ở
     * ảnh đang hiện cho người chơi chạm ô số chốt, chốt xong lại chạy tiếp. Chỉ chạy khi màn đang hiện
     * (cùng vòng đời với camera); thời gian chỉ đếm khi thấy mặt.
     */
    private suspend fun spinLoop() {
        while (currentCoroutineContext().isActive) {
            delay(SPIN_INTERVAL_MS)
            if (_binding == null) return
            if (recPhase != RecPhase.RECORDING || !game.isPlaying) {
                // Chưa quay hoặc hết ván: lần quay sau bắt đầu lại từ đầu.
                if (spinStopped || spinElapsedMs != 0L) resetSpinCycle()
                if (!game.isActive) previewTick()
                continue
            }
            if (!headSmoother.hasFace || spinStopped) continue
            viewModel.spin()
            spinElapsedMs += SPIN_INTERVAL_MS
            if (spinElapsedMs >= spinTargetMs) spinStopped = true
        }
    }

    // ---------------------------------------------------------------- Giao diện

    private fun render(state: CameraUiState) {
        durationViews().forEach { (duration, view) -> view.isSelected = duration == state.duration }
        binding.tvAddSound.text = state.music?.title ?: getString(R.string.camera_add_sound)
        binding.musicDivider.isVisible = state.music != null
        binding.btnClearMusic.isVisible = state.music != null
        if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) prepareMusicPlayer()
        game = state.game
        // Filter tự tạo có thể 10–11 ảnh: số ô hạng = số ảnh (filter có sẵn luôn 12). Không đổi giữa ván.
        val slotCount = state.filter?.let { FilterDto.slotCountFor(it.photos.size) } ?: FilterDto.PHOTO_COUNT
        if (slotCount != rankSlots.size && !state.game.isActive) createRankSlots(slotCount)
        updateControls()
        rankSlots.forEachIndexed { index, slot -> renderSlot(index, slot, state.game.slots.getOrNull(index)) }
        publishScene()
    }

    /**
     * Cập nhật View của ô (đang trong suốt, xem [setUpOverlay]). Vẫn giữ đúng trạng thái để trình đọc màn hình
     * đọc được, và để hiện lại được nếu [CameraOverlay] hỏng.
     */
    private fun renderSlot(index: Int, slot: ItemRankSlotBinding, photo: Bitmap?) {
        val filled = photo != null
        slot.ivPhoto.isVisible = filled
        slot.tvNumber.isVisible = !filled
        slot.tvRank.visibility = if (filled) View.VISIBLE else View.INVISIBLE
        slot.box.setBackgroundResource(if (isBusy) R.drawable.bg_rank_slot_playing else R.drawable.bg_rank_slot)
        if (slot.ivPhoto.tag !== photo) {
            slot.ivPhoto.tag = photo
            slot.ivPhoto.setImageBitmap(photo)
        }
    }

    /**
     * Điều khiển nào hiện lúc nào:
     * - bảng xin quyền: ẩn hết;
     * - camera, chưa quay: đủ bộ nút;
     * - đang quay (Figma 8:102): chỉ còn đồng hồ, cột hạng, nút dừng và nút làm lại.
     *   Nút Back cũng ẩn (vẫn bấm Back hệ thống được: video đang quay bị bỏ).
     */
    private fun updateControls() {
        val b = _binding ?: return
        val busy = isBusy
        val idle = cameraVisible && !busy
        val playing = cameraVisible && busy
        b.btnBack.isVisible = !busy
        b.btnAddSound.isVisible = idle
        b.btnFlip.isVisible = idle
        b.btnFlash.isVisible = idle
        b.durationStrip.isVisible = idle
        b.btnChooseFilter.isVisible = idle
        b.timerPill.isVisible = playing
        b.rankColumn.isVisible = cameraVisible
        b.btnRecord.isVisible = cameraVisible
        // Trong lúc chờ CameraX bắt đầu / đóng file thì chưa bấm được.
        b.btnRecord.isEnabled = recPhase == RecPhase.IDLE || recPhase == RecPhase.RECORDING

        // Nút quay: chấm tròn 56dp → hình vuông bo góc 32dp khi đang quay (Figma 8:191).
        val dotSize = resources.getDimensionPixelSize(if (busy) R.dimen.record_stop_inner else R.dimen.record_button_inner)
        b.recordDot.setBackgroundResource(if (busy) R.drawable.bg_record_stop else R.drawable.bg_record_dot)
        if (b.recordDot.layoutParams.width != dotSize) {
            b.recordDot.updateLayoutParams { width = dotSize; height = dotSize }
        }
        b.btnRecord.contentDescription = getString(if (busy) R.string.cd_stop else R.string.cd_record)
    }

    private fun onEvent(event: CameraEvent) {
        when (event) {
            CameraEvent.FilterMissing -> {
                toast(R.string.camera_filter_missing)
                findNavController().navigateUp()
            }
        }
    }

    /** Tạo cột hạng (mặc định 12 dòng); kích thước lấy từ dimens và được tính lại khi biết chiều cao cột. */
    private fun buildRankSlots() {
        val column = binding.rankColumn
        createRankSlots(FilterDto.PHOTO_COUNT)
        column.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            // Vị trí các ô có thể đã đổi: lần vẽ overlay tới sẽ đo lại.
            slotGeometry = null
            // Đổi LayoutParams trong lúc đang layout thì bị bỏ qua, nên dời sang frame sau.
            if (bottom - top != oldBottom - oldTop) v.post { if (_binding != null) sizeRankSlots(bottom - top) }
        }
    }

    /** Dựng lại [count] dòng hạng (10–12, theo số ảnh của filter). */
    private fun createRankSlots(count: Int) {
        val column = binding.rankColumn
        column.removeAllViews()
        rankSlots.clear()
        slotGeometry = null
        repeat(count) { index ->
            val slot = ItemRankSlotBinding.inflate(layoutInflater, column, false)
            slot.tvNumber.text = slotNumbers[index]
            slot.tvRank.text = slotNumbers[index]
            slot.box.contentDescription = getString(R.string.cd_rank_slot, index + 1)
            slot.box.setOnClickListener { onSlotClicked(index) }
            column.addView(slot.root)
            rankSlots += slot
        }
        sizeRankSlots(column.height)
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
        slotGeometry = null
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
        // Màn chọn filter là "bộ chọn" của Camera này: Back = về đây giữ filter cũ, chọn filter = về đây và đổi filter
        // (listenFilterResult). Vào Camera từ New filter thì mở New filter, còn lại mở Choose a Filter.
        val fromNewPack = nav.previousBackStackEntry?.destination?.id == R.id.newFilterFragment
        nav.navigateSafe(
            if (fromNewPack) R.id.action_camera_to_newFilter else R.id.action_camera_to_chooseFilter,
            bundleOf(ChooseFilterFragment.ARG_PICK_FOR_CAMERA to true),
        )
    }

    /** Màn chọn filter (mở bằng nút trên Camera) trả về filter vừa chọn qua savedStateHandle của màn này. */
    private fun listenFilterResult() {
        val entry = findNavController().currentBackStackEntry ?: return
        val handle = entry.savedStateHandle
        handle.getLiveData<String>(ChooseFilterFragment.RESULT_FILTER_ID).observe(viewLifecycleOwner) { id ->
            if (id == null) return@observe
            val isUser = handle.remove<Boolean>(ChooseFilterFragment.RESULT_IS_USER_FILTER) == true
            handle.remove<String>(ChooseFilterFragment.RESULT_FILTER_ID)
            viewModel.changeFilter(id, isUser)
        }
    }

    // ---------------------------------------------------------------- Nhạc nền

    private fun openAddSound() {
        if (isBusy) return
        findNavController().navigateSafe(
            R.id.action_camera_to_addSound,
            bundleOf(AddSoundViewModel.ARG_SELECTED to (viewModel.state.value.music?.id ?: -1)),
        )
    }

    /** Add Sound trả về id bài đã chọn qua savedStateHandle của màn này. */
    private fun listenMusicResult() {
        val entry = findNavController().currentBackStackEntry ?: return
        entry.savedStateHandle.getLiveData<Int>(AddSoundFragment.RESULT_MUSIC_ID).observe(viewLifecycleOwner) { id ->
            if (id == null) return@observe
            entry.savedStateHandle.remove<Int>(AddSoundFragment.RESULT_MUSIC_ID)
            viewModel.setMusic(id)
        }
    }

    /**
     * Chuẩn bị sẵn trình phát nhạc (đã nạp file, đang dừng) để bấm quay là phát ngay.
     * Chỉ phát file đã tải về máy (Add Sound tải lúc bấm tick); thiếu file thì quay không nhạc,
     * màn Preview sẽ thử tải lại.
     */
    private fun prepareMusicPlayer() {
        val track = viewModel.state.value.music
        val file = track?.let { MusicRepository.cachedFile(requireContext(), it) }?.takeIf(File::exists)
        if (file == musicPlayerFile && (file == null || musicPlayer != null)) return
        releaseMusicPlayer()
        if (file == null) return
        musicPlayerFile = file
        musicPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    private fun releaseMusicPlayer() {
        musicPlayer?.release()
        musicPlayer = null
        musicPlayerFile = null
    }

    // ---------------------------------------------------------------- Overlay trên khung hình camera

    /**
     * Tạo [CameraOverlay] và làm các View của cột hạng trong suốt: từ đây ô số, ảnh đã chốt và ảnh trên đầu
     * do overlay vẽ lên khung hình camera, để màn hình và video lưu ra giống hệt nhau.
     * View trong suốt vẫn nhận chạm và vẫn được trình đọc màn hình đọc.
     */
    private fun setUpOverlay() {
        val slotSize = resources.getDimension(R.dimen.rank_slot_size)
        val typeface = try {
            ResourcesCompat.getFont(requireContext(), R.font.londrina_solid_regular)
        } catch (e: Resources.NotFoundException) {
            null
        }
        val renderer = OverlayRenderer(
            typeface = typeface,
            slotCornerRatio = resources.getDimension(R.dimen.rank_slot_corner) / slotSize,
            slotTextRatio = resources.getDimension(R.dimen.rank_slot_text) / slotSize,
            slotStrokePx = resources.getDimension(R.dimen.stroke_1),
        )
        overlay = CameraOverlay(renderer) {
            // Overlay hỏng (rất hiếm): hiện lại các View để ít nhất còn thấy cột hạng trên màn hình.
            mainHandler.post { _binding?.rankColumn?.alpha = 1f }
        }
        binding.rankColumn.alpha = 0f
    }

    /**
     * Gom mọi thứ cần vẽ thành một [OverlayScene] và giao cho luồng vẽ. Gọi trên luồng chính mỗi khi
     * có kết quả nhận diện mặt mới hoặc trạng thái trò chơi / quay đổi.
     */
    private fun publishScene() {
        val b = _binding ?: return
        val target = overlay ?: return
        // sensorToViewTransform (chỉ gọi được trên luồng chính) là null khi preview chưa chạy.
        val sensorToView = if (cameraVisible) b.previewView.sensorToViewTransform else null
        val viewToSensor = Matrix()
        if (sensorToView == null || !sensorToView.invert(viewToSensor)) {
            target.scene = null
            return
        }
        val geometry = slotGeometry ?: measureSlots()?.also { slotGeometry = it }
        val playingPhoto = game.isActive
        // Chưa chơi: ảnh của filter nháy đổi liên tục trên đầu (xem trước); chưa có ảnh filter thì dùng "?".
        val idlePhoto = if (playingPhoto) null else previewPhoto
        target.scene = OverlayScene(
            viewToSensor = viewToSensor,
            head = headSmoother.current,
            // Đang chơi: ảnh của filter; chưa chơi: ảnh filter nháy đổi (xem trước), chưa có thì "?" vuông.
            // Xếp đủ 12 ô: ẩn ảnh trên đầu, video vẫn quay tiếp tới khi người dùng bấm dừng hoặc hết giờ.
            headPhoto = if (playingPhoto) (if (game.isFinished) null else game.head) else idlePhoto ?: placeholder,
            headCornerRatio = if (playingPhoto || idlePhoto != null) PHOTO_CORNER_RATIO else 0f,
            slots = geometry.orEmpty().mapIndexed { index, g ->
                OverlayScene.Slot(g.box, slotNumbers[index], game.slots.getOrNull(index), g.rankCenterX)
            },
            slotsOutlined = isBusy,
        )
    }

    /** Vị trí các ô hạng trong tọa độ của PreviewView (PreviewView phủ kín view gốc nên dùng chung gốc tọa độ). */
    private fun measureSlots(): List<SlotGeometry>? {
        val root = binding.root
        val rect = Rect()
        return rankSlots.map { slot ->
            if (slot.box.width == 0) return null // chưa layout xong
            slot.box.getDrawingRect(rect)
            root.offsetDescendantRectToMyCoords(slot.box, rect)
            val box = RectF(rect)
            slot.tvRank.getDrawingRect(rect)
            root.offsetDescendantRectToMyCoords(slot.tvRank, rect)
            SlotGeometry(box, rect.exactCenterX())
        }
    }

    private fun setUpRecorder() {
        recorder = VideoRecorder(requireContext()).also { it.listener = recorderListener }
    }

    // ---------------------------------------------------------------- Quyền

    private fun missingPermissions(): List<String> = REQUIRED_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllPermissions(): Boolean = missingPermissions().isEmpty()

    private fun showPermissionPanel() {
        headSmoother.clear()
        cameraVisible = false
        updateControls()
        publishScene()
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

    /**
     * Gắn Preview + ImageAnalysis + VideoCapture (kèm OverlayEffect) với camera đang chọn; gọi lại mỗi khi lật camera.
     *
     * Vì hiệu ứng nhắm vào cả Preview và VideoCapture, CameraX mở MỘT luồng hình từ camera, cho qua hiệu ứng rồi
     * chia cho hai nơi (stream sharing). Cộng với ImageAnalysis là hai luồng, nên máy đời thấp cũng chạy được.
     */
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

        // Preview, ImageAnalysis và VideoCapture phải dùng chung ViewPort: cùng nhìn một vùng cảm biến.
        // Thiếu nó thì (1) khung mặt đổi sang tọa độ view bị lệch, (2) video ghi vùng rộng hơn màn hình
        // nên bố cục overlay trong video khác với lúc quay.
        val viewPort = binding.previewView.viewPort
        if (viewPort == null) Log.w(TAG, "PreviewView chưa có ViewPort: overlay có thể bị lệch")
        val useCases = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analysis)
            .apply {
                recorder?.let { addUseCase(it.useCase) }
                overlay?.let { addEffect(it.effect) }
                viewPort?.let(::setViewPort)
            }
            .build()

        headSmoother.clear()
        publishScene()
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

        cameraState?.removeObservers(viewLifecycleOwner)
        lastCameraError = null
        cameraState = bound.cameraInfo.cameraState.also { live ->
            live.observe(viewLifecycleOwner, ::onCameraState)
        }
    }

    /**
     * Lỗi của camera (ví dụ app khác chiếm camera). Nếu đang quay, bản ghi sẽ tự kết thúc với
     * ERROR_SOURCE_INACTIVE và [recorderListener] lo phần còn lại; ở đây chỉ báo cho người dùng một lần.
     */
    private fun onCameraState(state: CameraState) {
        val code = state.error?.code
        if (code == lastCameraError) return
        lastCameraError = code
        when (code) {
            null -> Unit
            CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> toast(R.string.camera_in_use)
            CameraState.ERROR_CAMERA_DISABLED,
            CameraState.ERROR_CAMERA_FATAL_ERROR,
            CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED,
            -> toast(R.string.camera_open_error)
            // Lỗi tạm thời: CameraX tự thử mở lại.
            else -> Log.w(TAG, "Camera báo lỗi tạm thời $code")
        }
    }

    /** Tạo bộ nhận diện mặt và nạp ảnh chờ ("?" theo design). */
    private fun setUpFaceDetection() {
        placeholder = BitmapFactory.decodeResource(resources, R.drawable.head_placeholder)
        analysisExecutor = Executors.newSingleThreadExecutor()
        faceAnalyzer = FaceAnalyzer(
            // sensorToViewTransform chỉ gọi được trên luồng chính; FaceAnalyzer chỉ gọi lambda này ở luồng chính.
            // Không gọi khi đang hiện bảng xin quyền (preview không chạy).
            sensorToView = { if (cameraVisible) _binding?.previewView?.sensorToViewTransform else null },
            onFace = ::onFaceResult,
        )
    }

    /** Kết quả nhận diện của một khung hình (luồng chính): làm mượt vị trí rồi đưa vào overlay. */
    private fun onFaceResult(face: TrackedFace?) {
        if (_binding == null) return
        val target = face?.let {
            HeadPlacements.place(it.box.left, it.box.top, it.box.right, it.box.bottom, it.rollDegrees)
        }
        headSmoother.update(target, SystemClock.uptimeMillis())
        publishScene()
    }

    private fun flipCamera() {
        if (recPhase != RecPhase.IDLE) return // đổi camera sẽ cắt ngang bản ghi
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

        // Video quay vào bộ nhớ riêng của app nên không cần quyền bộ nhớ (chỉ xin khi Download, xem SuccessFragment).
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        /** Lúc quay ảnh đổi mỗi 50ms (sổ tay ghi 150ms, người dùng yêu cầu nhanh hơn). */
        private const val SPIN_INTERVAL_MS = 50L

        /** Lúc chưa quay (xem trước), ảnh trên đầu nháy đổi mỗi 100ms. */
        private const val PREVIEW_INTERVAL_MS = 100L

        /** Ảnh chạy một khoảng ngẫu nhiên trong đoạn này rồi dừng để người chơi chọn ô số. */
        private const val SPIN_MIN_MS = 600L
        private const val SPIN_MAX_MS = 1_500L

        /** Sau khi chốt ô cuối, quay thêm chừng này để video có cảnh cột hạng đầy rồi mới dừng. */

        /** Ảnh của filter trên đầu bo góc ≈ 12% cạnh (Figma 8:192). */
        private const val PHOTO_CORNER_RATIO = 0.12f
    }
}
