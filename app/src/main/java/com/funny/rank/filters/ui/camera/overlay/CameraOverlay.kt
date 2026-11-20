package com.funny.rank.filters.ui.camera.overlay

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.CameraEffect
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect

/**
 * Vẽ [OverlayScene] lên TỪNG KHUNG HÌNH camera bằng `OverlayEffect` của CameraX (module camera-effects).
 * Hiệu ứng gắn vào cả Preview và VideoCapture, nên hình trên màn hình và hình trong video lưu ra là MỘT:
 * không có chuyện màn hình thấy ảnh trên đầu mà video thì không.
 *
 * ### Hệ tọa độ (phần dễ sai nhất)
 * OverlayEffect cho một Canvas phủ lên bộ đệm khung hình, và ma trận `frame.sensorToBufferTransform`
 * (tọa độ cảm biến → tọa độ bộ đệm). Bộ đệm này KHÔNG trùng với màn hình: nó nằm ngang theo cảm biến,
 * chưa cắt theo PreviewView, và với camera trước thì chưa lật gương.
 * Ta muốn vẽ bằng tọa độ PreviewView (đã có sẵn từ bước 5b và từ vị trí các ô hạng), nên ghép:
 *
 *     view  →(viewToSensor)→  cảm biến  →(sensorToBufferTransform)→  bộ đệm
 *
 * `viewToSensor` là nghịch đảo của `PreviewView.sensorToViewTransform`, do luồng chính tính và gói trong scene.
 * Ma trận đó đã gồm phép lật gương của camera trước, nên chữ và ảnh vẽ ra được lật sẵn trong bộ đệm;
 * khi PreviewView (và VideoCapture với MIRROR_MODE_ON_FRONT_ONLY) lật gương hình camera thì chúng trở lại đúng chiều.
 *
 * ### Luồng
 * CameraX gọi listener trên [thread] (không phải luồng chính) cho mỗi khung hình.
 * Luồng chính chỉ gán [scene] (biến @Volatile, đối tượng bất biến), không khóa gì.
 */
class CameraOverlay(
    private val renderer: OverlayRenderer,
    /** Gọi trên luồng vẽ khi OverlayEffect báo lỗi. */
    onError: (Throwable) -> Unit,
) : AutoCloseable {

    private val thread = HandlerThread("CameraOverlay").apply { start() }

    /** Thứ cần vẽ; null = không vẽ gì (xóa lớp phủ). Gán từ luồng chính. */
    @Volatile
    var scene: OverlayScene? = null

    val effect = OverlayEffect(
        CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
        // 0 = vẽ ngay lên khung hình mới nhất, không xếp hàng (không cần khớp theo timestamp).
        0,
        Handler(thread.looper),
    ) { error ->
        Log.e(TAG, "OverlayEffect lỗi", error)
        onError(error)
    }

    init {
        effect.setOnDrawListener { frame ->
            drawFrame(frame)
            // Luôn trả true: false nghĩa là BỎ khung hình này (preview và video sẽ giật).
            true
        }
    }

    private fun drawFrame(frame: Frame) {
        val canvas = frame.overlayCanvas
        // Lớp phủ giữ nguyên nội dung giữa các khung hình: phải xóa trước khi vẽ, nếu không ảnh trên đầu để lại vệt.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val current = scene ?: return
        canvas.setMatrix(frame.sensorToBufferTransform)
        canvas.concat(current.viewToSensor)
        renderer.draw(canvas, current)
    }

    override fun close() {
        scene = null
        effect.clearOnDrawListener()
        effect.close()
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "CameraOverlay"
    }
}
