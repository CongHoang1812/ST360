package com.funny.rank.filters.ui.camera.face

/**
 * Làm mượt vị trí ảnh trên đầu giữa các khung hình và quyết định lúc nào ẩn ảnh.
 * Hàm thuần (thời gian truyền từ ngoài vào) để chạy được bằng JUnit.
 *
 * - Kết quả ML Kit rung nhẹ giữa các khung hình: mỗi lần chỉ đi [smoothing] quãng đường tới vị trí mới.
 * - ML Kit thỉnh thoảng bỏ sót một hai khung hình: chỉ ẩn ảnh khi mất mặt lâu hơn [hideDelayMs],
 *   nếu không ảnh sẽ nhấp nháy.
 */
class HeadSmoother(
    /** 0 = đứng yên, 1 = bám sát từng khung hình (rung). */
    private val smoothing: Float = 0.5f,
    private val hideDelayMs: Long = 150L,
) {
    /** Vị trí đang hiện; null = không thấy mặt, ảnh đang ẩn. */
    var current: HeadPlacement? = null
        private set

    private var lastSeenAt = 0L

    val hasFace: Boolean get() = current != null

    /**
     * Nhận kết quả của một khung hình. [target] = null nghĩa là khung hình này không thấy mặt.
     * Trả về vị trí cần vẽ (null = ẩn).
     */
    fun update(target: HeadPlacement?, nowMs: Long): HeadPlacement? {
        if (target == null) {
            if (current != null && nowMs - lastSeenAt > hideDelayMs) current = null
            return current
        }
        lastSeenAt = nowMs
        current = current?.let { blend(it, target) } ?: target
        return current
    }

    /** Ẩn ngay (đổi camera, rời màn, mất quyền). */
    fun clear() {
        current = null
    }

    private fun blend(from: HeadPlacement, to: HeadPlacement): HeadPlacement {
        val a = smoothing
        return HeadPlacement(
            centerX = from.centerX + (to.centerX - from.centerX) * a,
            centerY = from.centerY + (to.centerY - from.centerY) * a,
            size = from.size + (to.size - from.size) * a,
            rotation = from.rotation + shortestDelta(from.rotation, to.rotation) * a,
        )
    }

    /** Hiệu hai góc đi đường ngắn nhất, tránh xoay vòng lớn khi góc nhảy qua ±180. */
    private fun shortestDelta(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f
}
