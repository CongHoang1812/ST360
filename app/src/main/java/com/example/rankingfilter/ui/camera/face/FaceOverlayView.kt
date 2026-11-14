package com.example.rankingfilter.ui.camera.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Lớp phủ nằm ngay trên PreviewView (cùng kích thước, cùng gốc tọa độ) vẽ một ảnh vuông ở phía trên đầu,
 * xoay theo độ nghiêng đầu. Khi mất mặt thì ẩn, có mặt lại thì hiện lại.
 *
 * Chỉ vẽ; việc tìm mặt và đổi tọa độ do [FaceAnalyzer] làm, vị trí do [HeadPlacements] tính.
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Ảnh vẽ trên đầu; ảnh không vuông thì lấy hình vuông ở giữa (center crop). */
    var photo: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    private var shown: HeadPlacement? = null
    private var lastSeenAt = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Nhận kết quả mỗi khung hình (gọi trên luồng chính). null = không thấy mặt. */
    fun onFace(face: TrackedFace?) {
        val now = SystemClock.uptimeMillis()
        if (face == null) {
            // Chờ một chút trước khi ẩn: ML Kit thỉnh thoảng bỏ sót một hai khung hình, ẩn ngay sẽ nhấp nháy.
            if (shown != null && now - lastSeenAt > HIDE_DELAY_MS) clear()
            return
        }
        lastSeenAt = now
        val target = HeadPlacements.place(
            face.box.left, face.box.top, face.box.right, face.box.bottom, face.rollDegrees,
        )
        shown = smooth(shown, target)
        invalidate()
    }

    /** Ẩn ảnh ngay (đổi camera, rời màn, mất quyền). */
    fun clear() {
        if (shown != null) {
            shown = null
            invalidate()
        }
    }

    /** Làm mượt: kết quả ML Kit rung nhẹ giữa các khung hình, đi một phần quãng đường tới vị trí mới. */
    private fun smooth(previous: HeadPlacement?, next: HeadPlacement): HeadPlacement {
        if (previous == null) return next
        val a = SMOOTHING
        return HeadPlacement(
            centerX = previous.centerX + (next.centerX - previous.centerX) * a,
            centerY = previous.centerY + (next.centerY - previous.centerY) * a,
            size = previous.size + (next.size - previous.size) * a,
            rotation = previous.rotation + shortestDelta(previous.rotation, next.rotation) * a,
        )
    }

    /** Hiệu hai góc đi đường ngắn nhất, tránh xoay vòng lớn khi góc nhảy qua ±180. */
    private fun shortestDelta(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f

    override fun onDraw(canvas: Canvas) {
        val placement = shown ?: return
        val bitmap = photo ?: return
        val side = min(bitmap.width, bitmap.height)
        src.set(
            (bitmap.width - side) / 2, (bitmap.height - side) / 2,
            (bitmap.width + side) / 2, (bitmap.height + side) / 2,
        )
        val half = placement.size / 2f
        dst.set(-half, -half, half, half)

        canvas.save()
        canvas.translate(placement.centerX, placement.centerY)
        canvas.rotate(placement.rotation)
        canvas.drawBitmap(bitmap, src, dst, paint)
        canvas.restore()
    }

    private companion object {
        const val HIDE_DELAY_MS = 150L

        /** 0 = đứng yên, 1 = bám sát từng khung hình (rung). */
        const val SMOOTHING = 0.5f
    }
}
