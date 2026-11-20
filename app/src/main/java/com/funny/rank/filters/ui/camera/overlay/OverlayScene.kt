package com.funny.rank.filters.ui.camera.overlay

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import com.funny.rank.filters.ui.camera.face.HeadPlacement

/**
 * Mọi thứ cần vẽ lên khung hình camera tại một thời điểm: ảnh trên đầu và cột 12 ô hạng.
 * Tọa độ tính bằng pixel của PreviewView (cùng hệ với các View trên màn hình).
 *
 * Luồng chính tạo một [OverlayScene] mới mỗi khi có thay đổi; luồng vẽ của [CameraOverlay] chỉ đọc.
 * Vì vậy lớp này BẤT BIẾN: không sửa [viewToSensor], [RectF] hay danh sách sau khi tạo.
 */
class OverlayScene(
    /** Đổi tọa độ PreviewView → tọa độ cảm biến (nghịch đảo của PreviewView.sensorToViewTransform). */
    val viewToSensor: Matrix,
    /** Vị trí ảnh trên đầu; null = không thấy mặt, không vẽ. */
    val head: HeadPlacement?,
    val headPhoto: Bitmap?,
    /** Độ bo góc ảnh trên đầu theo cạnh ảnh: 0 = vuông (ảnh chờ "?"), 0,12 = ảnh của filter. */
    val headCornerRatio: Float,
    val slots: List<Slot>,
    /** Đang quay: ô hạng có viền trắng (Figma 8:235). */
    val slotsOutlined: Boolean,
) {
    class Slot(
        /** Khung của ô trong tọa độ PreviewView. */
        val box: RectF,
        /** Số hạng "1".."12". */
        val number: String,
        /** Ảnh đã chốt; null = ô trống (số nằm trong ô). */
        val photo: Bitmap?,
        /** Tâm ngang của số hạng đứng bên trái ô khi ô đã chốt (Figma 8:192). */
        val rankCenterX: Float,
    )
}
