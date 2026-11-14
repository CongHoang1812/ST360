package com.example.rankingfilter.ui.camera.face

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/** Khuôn mặt đang theo dõi, đã đổi sang tọa độ pixel của PreviewView. */
data class TrackedFace(
    /** Khung mặt trong hệ tọa độ của view (đã xoay, lật gương, co giãn theo PreviewView). */
    val box: RectF,
    /** Góc nghiêng đầu trong view, độ, chiều kim đồng hồ dương. */
    val rollDegrees: Float,
    val trackingId: Int?,
)

/**
 * Nhận từng khung hình từ ImageAnalysis, tìm mặt bằng ML Kit (chế độ nhanh, có theo dõi mặt)
 * và đổi khung mặt sang tọa độ của PreviewView.
 *
 * ### Đổi tọa độ (ảnh phân tích → PreviewView)
 * Đi qua TỌA ĐỘ CẢM BIẾN, hệ chung tuyệt đối mà mọi luồng hình của CameraX đều báo:
 *
 *     ảnh thẳng đứng (ML Kit) →[xoay ngược rotationDegrees]→ bộ đệm phân tích
 *       →[nghịch đảo ImageInfo.sensorToBufferTransformMatrix]→ cảm biến
 *       →[PreviewView.sensorToViewTransform]→ view
 *
 * - ML Kit trả tọa độ trong ảnh đã xoay thẳng đứng (`InputImage.fromMediaImage(image, rotationDegrees)`),
 *   nên bước đầu phải xoay ngược lại về bộ đệm gốc.
 * - `sensorToBufferTransformMatrix` của ImageProxy mô tả bộ đệm phân tích nằm ở đâu trên cảm biến.
 * - `PreviewView.sensorToViewTransform` mô tả cảm biến hiện lên màn hình ra sao (gồm xoay, cắt giữa, phóng to
 *   và lật gương của camera trước). Đây cũng là ma trận mà CameraOverlay dùng để vẽ, nên mặt và hình vẽ luôn khớp.
 *
 * KHÔNG dùng `CoordinateTransform(ImageProxyTransformFactory, PreviewView.outputTransform)` như bước 5b:
 * khi có OverlayEffect, CameraX cho preview và video dùng chung một luồng hình đã qua hiệu ứng
 * (stream sharing) và `outputTransform` không còn khớp với ảnh phân tích. Ảnh trên đầu bị đẩy lệch hẳn
 * ra góc màn hình (đã gặp trên Xiaomi, 15/11/2026).
 *
 * ### Góc nghiêng đầu
 * `headEulerAngleZ` dương = mặt xoay ngược chiều kim đồng hồ trong ảnh phân tích. Ta đổi sang chiều kim
 * đồng hồ, dựng vector "đỉnh đầu" rồi biến đổi vector đó bằng chính ma trận của khung mặt:
 * lật gương tự đảo dấu góc, không cần phân biệt camera trước/sau. Xem [HeadPlacements].
 *
 * Chạy trên một luồng riêng; kết quả [onFace] được gọi trên luồng chính (ML Kit trả Task về luồng chính),
 * nơi PreviewView.sensorToViewTransform được phép gọi.
 */
class FaceAnalyzer(
    /** Gọi trên luồng chính. Trả null khi preview chưa chạy. */
    private val sensorToView: () -> Matrix?,
    /** Gọi trên luồng chính. null = không thấy mặt trong khung hình này. */
    private val onFace: (TrackedFace?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build()
    )

    @Volatile
    private var closed = false

    /** Chỉ dùng trên luồng chính. */
    private var trackedId: Int? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }
        // Tính ma trận ảnh thẳng đứng → cảm biến ngay đây, khi ImageProxy chắc chắn còn mở.
        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightToSensor = uprightToSensor(
            imageProxy.width, imageProxy.height, rotation, imageProxy.imageInfo.sensorToBufferTransformMatrix,
        )
        if (uprightToSensor == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        try {
            detector.process(input)
                .addOnSuccessListener { faces -> if (!closed) deliver(faces, uprightToSensor) }
                .addOnFailureListener { e -> if (!closed) Log.w(TAG, "ML Kit lỗi", e) }
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            // Detector đã đóng giữa chừng.
            imageProxy.close()
        }
    }

    private fun deliver(faces: List<Face>, uprightToSensor: Matrix) {
        val sensorToViewMatrix = sensorToView()
        val face = if (sensorToViewMatrix == null) null else pick(faces)
        if (face == null || sensorToViewMatrix == null) {
            trackedId = null
            onFace(null)
            return
        }
        trackedId = face.trackingId

        val toView = Matrix(uprightToSensor).apply { postConcat(sensorToViewMatrix) }

        val box = RectF(face.boundingBox)
        toView.mapRect(box)

        // Vector "đỉnh đầu" trong ảnh phân tích (dài 100 đơn vị cho đỡ sai số), biến đổi như khung mặt.
        val phi = HeadPlacements.clockwiseDegreesFromEulerZ(face.headEulerAngleZ)
        val (ux, uy) = HeadPlacements.upVector(phi)
        val points = floatArrayOf(0f, 0f, ux * UP_LENGTH, uy * UP_LENGTH)
        toView.mapPoints(points)
        val roll = HeadPlacements.viewRollDegrees(points[2] - points[0], points[3] - points[1])

        onFace(TrackedFace(box, roll, face.trackingId))
    }

    /** Giữ mặt đang theo dõi nếu còn; không thì lấy mặt lớn nhất (gần camera nhất). */
    private fun pick(faces: List<Face>): Face? {
        if (faces.isEmpty()) return null
        trackedId?.let { id -> faces.firstOrNull { it.trackingId == id }?.let { return it } }
        return faces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }
    }

    /** Gọi khi hủy view. Sau đó mọi khung hình đến muộn bị bỏ qua. */
    fun close() {
        closed = true
        detector.close()
    }

    private companion object {
        const val TAG = "FaceAnalyzer"

        /** Bỏ qua mặt nhỏ hơn 15% chiều rộng khung hình: nhanh hơn và đỡ nhận nhầm. */
        const val MIN_FACE_SIZE = 0.15f
        const val UP_LENGTH = 100f

        /**
         * Ma trận từ tọa độ ảnh thẳng đứng (ML Kit) sang tọa độ cảm biến; null nếu ma trận không nghịch đảo được.
         *
         * Bộ đệm gốc rộng [width] × cao [height]. ML Kit xoay nó [rotation] độ theo chiều kim đồng hồ cho thẳng đứng:
         *  - 90°:  (x, y) → (height − y, x)
         *  - 180°: (x, y) → (width − x, height − y)
         *  - 270°: (x, y) → (y, width − x)
         * Ta dựng phép xoay đó (bộ đệm → thẳng đứng) rồi nghịch đảo, sau đó nối với bộ đệm → cảm biến.
         */
        fun uprightToSensor(width: Int, height: Int, rotation: Int, sensorToBuffer: Matrix): Matrix? {
            val bufferToUpright = Matrix().apply {
                postRotate(rotation.toFloat())
                when (rotation) {
                    90 -> postTranslate(height.toFloat(), 0f)
                    180 -> postTranslate(width.toFloat(), height.toFloat())
                    270 -> postTranslate(0f, width.toFloat())
                }
            }
            val uprightToBuffer = Matrix()
            val bufferToSensor = Matrix()
            if (!bufferToUpright.invert(uprightToBuffer) || !sensorToBuffer.invert(bufferToSensor)) return null
            return uprightToBuffer.apply { postConcat(bufferToSensor) }
        }
    }
}
