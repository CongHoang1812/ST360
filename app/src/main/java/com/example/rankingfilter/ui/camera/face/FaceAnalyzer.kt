package com.example.rankingfilter.ui.camera.face

import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
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
 * ML Kit trả khung mặt trong hệ tọa độ của ảnh đã xoay thẳng đứng
 * (`InputImage.fromMediaImage(image, rotationDegrees)`), chưa lật gương, chưa cắt, chưa co giãn.
 * Preview thì khác: xoay theo màn hình, lật gương với camera trước, cắt giữa (fillCenter) và phóng to
 * cho vừa PreviewView. Thay vì tự nhân ma trận, ta dùng `CoordinateTransform` của CameraX:
 *  - `ImageProxyTransformFactory` (đặt `isUsingRotationDegrees = true` vì ML Kit đã xoay ảnh)
 *    mô tả ảnh phân tích: ma trận từ tọa độ ảnh → tọa độ cảm biến;
 *  - `PreviewView.outputTransform` mô tả preview: ma trận từ tọa độ cảm biến → tọa độ view
 *    (đã gồm lật gương của camera trước và phần cắt giữa);
 *  - `CoordinateTransform(nguồn, đích)` ghép hai ma trận đó.
 * Điều kiện: Preview và ImageAnalysis dùng chung một `ViewPort` (xem `CameraFragment.bindCamera`),
 * để hai luồng nhìn cùng một vùng cảm biến, nếu không khung sẽ lệch.
 *
 * ### Góc nghiêng đầu
 * `headEulerAngleZ` dương = mặt xoay ngược chiều kim đồng hồ trong ảnh phân tích. Ta đổi sang chiều kim
 * đồng hồ, dựng vector "đỉnh đầu" rồi biến đổi vector đó bằng chính phép biến đổi của khung mặt:
 * lật gương tự đảo dấu góc, không cần phân biệt camera trước/sau. Xem [HeadPlacements].
 *
 * Chạy trên một luồng riêng; kết quả [onFace] được gọi trên luồng chính (ML Kit trả Task về luồng chính),
 * nơi PreviewView.outputTransform được phép gọi.
 */
class FaceAnalyzer(
    /** Gọi trên luồng chính. Trả null khi preview chưa chạy. */
    private val previewTransform: () -> OutputTransform?,
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

    // ML Kit đã xoay ảnh theo rotationDegrees nên tọa độ nguồn nằm trong ảnh đã xoay.
    // Không bật isUsingCropRect: ta đưa nguyên ảnh (không cắt) cho ML Kit.
    private val sourceFactory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }

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
        // Tính ma trận nguồn ngay đây, khi ImageProxy chắc chắn còn mở.
        val source = sourceFactory.getOutputTransform(imageProxy)
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        try {
            detector.process(input)
                .addOnSuccessListener { faces -> if (!closed) deliver(faces, source) }
                .addOnFailureListener { e -> if (!closed) Log.w(TAG, "ML Kit lỗi", e) }
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            // Detector đã đóng giữa chừng.
            imageProxy.close()
        }
    }

    private fun deliver(faces: List<Face>, source: OutputTransform) {
        val target = previewTransform()
        val face = if (target == null) null else pick(faces)
        if (face == null || target == null) {
            trackedId = null
            onFace(null)
            return
        }
        trackedId = face.trackingId

        val transform = CoordinateTransform(source, target)

        val box = RectF(face.boundingBox)
        transform.mapRect(box)

        // Vector "đỉnh đầu" trong ảnh phân tích (dài 100 đơn vị cho đỡ sai số), biến đổi như khung mặt.
        val phi = HeadPlacements.clockwiseDegreesFromEulerZ(face.headEulerAngleZ)
        val (ux, uy) = HeadPlacements.upVector(phi)
        val points = floatArrayOf(0f, 0f, ux * UP_LENGTH, uy * UP_LENGTH)
        transform.mapPoints(points)
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
    }
}
