package com.funny.rank.filters.ui.camera.record

import android.annotation.SuppressLint
import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.camera.core.MirrorMode
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

/**
 * Quay video bằng CameraX `VideoCapture<Recorder>`, có tiếng, ghi vào một file bản nháp trong bộ nhớ của app
 * (`VideoLibrary.newDraft`). Video chỉ ra thư viện ảnh của máy khi người dùng bấm Download ở màn Successful.
 * Hình ảnh trên đầu và cột hạng do `CameraOverlay` vẽ vào khung hình trước khi tới đây,
 * nên lớp này không biết gì về overlay.
 *
 * Mỗi lần chỉ có một bản ghi. Vòng đời một bản ghi:
 *
 *     start() ──► onStarted ──► onProgress… ──► stop(keep) hoặc hết giờ ──► onSaved / onDiscarded / onFailed
 *
 * Mọi callback của [Listener] chạy trên luồng chính và có thể đến SAU khi màn hình đã rời đi,
 * vì CameraX đóng file không đồng bộ: người nghe phải tự kiểm tra mình còn sống không.
 */
class VideoRecorder(context: Context) {

    enum class Failure {
        /** Bộ nhớ gần đầy (kiểm trước khi quay) hoặc đầy giữa chừng. */
        NO_STORAGE,
        /** Camera ngừng gửi hình giữa chừng (app khác chiếm camera, camera bị đóng). */
        CAMERA_INTERRUPTED,
        /** Dừng quá nhanh, chưa có khung hình nào được ghi. Không cần báo người dùng. */
        NO_DATA,
        /** Mất quyền micro lúc bắt đầu quay. */
        MIC_PERMISSION,
        /** Lỗi mã hóa, lỗi Recorder, không tạo được file… */
        UNKNOWN,
    }

    interface Listener {
        /** Đã bắt đầu ghi khung hình đầu tiên. */
        fun onStarted()
        /** Thời lượng đã ghi, gọi nhiều lần mỗi giây. */
        fun onProgress(elapsedMs: Long)
        /** Video đã đóng file xong và dùng được (dừng bằng tay, xếp xong, hoặc hết giờ). */
        fun onSaved(file: File)
        /** Bản ghi bị bỏ theo yêu cầu ([stop] với keep = false); file đã bị xóa. */
        fun onDiscarded()
        /** Bản ghi hỏng; file dở (nếu có) đã bị xóa. */
        fun onFailed(failure: Failure)
    }

    /** null = không ai nghe (màn hình đã rời đi): video vừa xong sẽ bị xóa. */
    var listener: Listener? = null

    private val appContext = context.applicationContext

    private val recorder = Recorder.Builder()
        // Full HD nếu máy quay được, không thì xuống HD rồi SD (hoặc mức gần nhất máy hỗ trợ).
        .setQualitySelector(
            QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
            )
        )
        .build()

    /** Use case để gắn vào camera cùng với Preview (xem CameraFragment.bindCamera). */
    val useCase: VideoCapture<Recorder> = VideoCapture.Builder(recorder)
        // Camera trước: video lật gương giống hệt preview (kiểu selfie). Nhờ vậy overlay đã lật sẵn
        // (xem CameraOverlay) đúng chiều ở cả màn hình lẫn video. Để mặc định (không lật) thì chữ số
        // trong video camera trước sẽ bị ngược.
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    private class Session(val file: File) {
        lateinit var recording: Recording
        var keep = true
        var stopRequested = false
    }

    private var session: Session? = null

    /**
     * Bắt đầu quay vào [output]. [durationLimitMs] = null là không giới hạn (chế độ Free).
     * Trả về null nếu bắt đầu được; nếu không, trả về lý do và KHÔNG gọi callback nào.
     * Phải có quyền RECORD_AUDIO (CameraFragment đã kiểm).
     */
    @SuppressLint("MissingPermission")
    fun start(durationLimitMs: Long?, output: File): Failure? {
        if (session != null) return Failure.UNKNOWN
        if (!hasEnoughStorage(output)) return Failure.NO_STORAGE

        val options = FileOutputOptions.Builder(output)
            // Recorder tự dừng đúng lúc hết giờ và báo Finalize với ERROR_DURATION_LIMIT_REACHED
            // (tên là "error" nhưng video hoàn toàn dùng được).
            .apply { if (durationLimitMs != null) setDurationLimitMillis(durationLimitMs) }
            .build()

        val holder = Session(output)
        return try {
            holder.recording = recorder.prepareRecording(appContext, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(appContext)) { event -> onEvent(holder, event) }
            session = holder
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Không có quyền micro", e)
            Failure.MIC_PERMISSION
        } catch (e: Exception) {
            // IllegalStateException: Recorder đang bận với bản ghi khác; IllegalArgumentException: tùy chọn sai.
            Log.e(TAG, "Không bắt đầu quay được", e)
            Failure.UNKNOWN
        }
    }

    /**
     * Dừng bản ghi hiện tại. [keep] = false: xóa video khi đóng file xong.
     * Kết quả đến sau qua [Listener]. Đã yêu cầu bỏ thì không đổi lại thành giữ được.
     */
    fun stop(keep: Boolean) {
        val current = session ?: return
        current.keep = current.keep && keep
        if (!current.stopRequested) {
            current.stopRequested = true
            current.recording.stop()
        }
    }

    /** Màn hình rời đi: bỏ bản ghi đang quay và không gọi callback nữa. */
    fun release() {
        listener = null
        stop(keep = false)
    }

    private fun onEvent(holder: Session, event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> if (session === holder) listener?.onStarted()
            is VideoRecordEvent.Status -> if (session === holder) {
                listener?.onProgress(event.recordingStats.recordedDurationNanos / 1_000_000)
            }
            is VideoRecordEvent.Finalize -> onFinalize(holder, event)
            else -> Unit // Pause / Resume: không dùng
        }
    }

    private fun onFinalize(holder: Session, event: VideoRecordEvent.Finalize) {
        if (session === holder) session = null
        val file = holder.file
        val error = event.error
        val usable = error == VideoRecordEvent.Finalize.ERROR_NONE ||
            error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
            error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
        if (error != VideoRecordEvent.Finalize.ERROR_NONE) {
            Log.w(TAG, "Finalize: error=$error keep=${holder.keep} file=$file", event.cause)
        }

        val currentListener = listener
        when {
            // Bị bỏ theo yêu cầu, hoặc không còn ai nhận video.
            !holder.keep || currentListener == null -> {
                delete(file)
                currentListener?.onDiscarded()
            }
            usable && file.length() > 0 -> currentListener.onSaved(file)
            else -> {
                // Lỗi thật: file dở có thể vẫn mở được nhưng thiếu đoạn cuối; xóa luôn.
                delete(file)
                currentListener.onFailed(
                    when (error) {
                        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> Failure.NO_STORAGE
                        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> Failure.CAMERA_INTERRUPTED
                        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> Failure.NO_DATA
                        else -> Failure.UNKNOWN
                    }
                )
            }
        }
    }

    /** Còn dưới [MIN_FREE_BYTES] thì không quay: thà báo trước còn hơn hỏng giữa chừng. */
    private fun hasEnoughStorage(output: File): Boolean = try {
        StatFs((output.parentFile ?: appContext.filesDir).path).availableBytes >= MIN_FREE_BYTES
    } catch (e: Exception) {
        true // Không đo được thì cứ thử quay; đầy thật thì Finalize sẽ báo ERROR_INSUFFICIENT_STORAGE.
    }

    private fun delete(file: File) {
        cleanupExecutor.execute {
            if (file.exists() && !file.delete()) Log.w(TAG, "Không xóa được video $file")
        }
    }

    companion object {
        private const val TAG = "VideoRecorder"

        /** 60 giây Full HD khoảng 80–100 MB; dưới 150 MB trống thì không bắt đầu. */
        private const val MIN_FREE_BYTES = 150L * 1024 * 1024

        private val cleanupExecutor = Executors.newSingleThreadExecutor()
    }
}
