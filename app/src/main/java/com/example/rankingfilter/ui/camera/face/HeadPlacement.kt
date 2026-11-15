package com.example.rankingfilter.ui.camera.face

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vị trí ảnh vuông trên đầu, tính bằng pixel của PreviewView.
 * [rotation] tính bằng độ, chiều kim đồng hồ dương (cùng quy ước View.rotation / Canvas.rotate).
 */
data class HeadPlacement(
    val centerX: Float,
    val centerY: Float,
    /** Cạnh của ảnh vuông. */
    val size: Float,
    val rotation: Float,
)

/**
 * Toán đặt ảnh lên đầu. Chỉ dùng số thực (không dùng kiểu của Android) để chạy được bằng JUnit.
 * Mọi tọa độ ở đây đã ở hệ tọa độ của PreviewView (trục y hướng xuống).
 */
object HeadPlacements {

    /** Cạnh ảnh = 0,9 × chiều rộng khuôn mặt. */
    const val SIZE_RATIO = 0.65f

    /**
     * Khe hở giữa mép trên khung mặt và mép dưới ảnh, tính theo chiều cao khuôn mặt.
     * Khung của ML Kit chỉ bao từ trán tới cằm, tóc nằm phía trên, nên cần chừa một khoảng.
     */
    const val GAP_RATIO = 0.12f

    /**
     * Ảnh nằm trên đầu: từ tâm khuôn mặt đi theo hướng "đỉnh đầu" một đoạn
     * nửa chiều cao mặt + khe hở + nửa cạnh ảnh. Đầu nghiêng thì ảnh cũng đi theo hướng nghiêng,
     * giống như đội trên đầu, chứ không chỉ xoay tại chỗ.
     *
     * @param left,top,right,bottom khung mặt trong hệ tọa độ của view
     * @param rollDegrees góc nghiêng đầu trong view, chiều kim đồng hồ dương (xem [viewRollDegrees])
     */
    fun place(left: Float, top: Float, right: Float, bottom: Float, rollDegrees: Float): HeadPlacement {
        val boxW = right - left
        val boxH = bottom - top
        // Khung của ML Kit là hình chữ nhật thẳng trục nên phình ra khi đầu nghiêng.
        // Cạnh ngắn của khung ≈ bề rộng thật của mặt kể cả khi đầu nghiêng 90° (lúc đó w và h đổi chỗ).
        val faceWidth = min(boxW, boxH)
        val faceHeight = max(boxW, boxH)
        val size = faceWidth * SIZE_RATIO
        val distance = faceHeight / 2f + faceHeight * GAP_RATIO + size / 2f

        val rad = rollDegrees * PI.toFloat() / 180f
        val upX = sin(rad)
        val upY = -cos(rad)
        return HeadPlacement(
            centerX = (left + right) / 2f + upX * distance,
            centerY = (top + bottom) / 2f + upY * distance,
            size = size,
            rotation = rollDegrees,
        )
    }

    /**
     * Góc nghiêng đầu ML Kit (headEulerAngleZ: dương = mặt xoay NGƯỢC chiều kim đồng hồ trong ảnh phân tích)
     * đổi sang hệ trục chiều-kim-đồng-hồ-dương: φ = −θ.
     */
    fun clockwiseDegreesFromEulerZ(eulerZ: Float): Float = -eulerZ

    /**
     * Vector "đỉnh đầu" (đơn vị) trong ảnh phân tích ứng với góc nghiêng [clockwiseDegrees]:
     * (sin φ, −cos φ). φ = 0 là thẳng lên trên, φ = 90 là chỉ sang phải.
     */
    fun upVector(clockwiseDegrees: Float): Pair<Float, Float> {
        val rad = clockwiseDegrees * PI.toFloat() / 180f
        return sin(rad) to -cos(rad)
    }

    /**
     * Sau khi biến đổi vector "đỉnh đầu" từ ảnh phân tích sang view (xoay, lật gương, co giãn),
     * góc nghiêng trong view là góc của vector đó so với hướng thẳng lên, chiều kim đồng hồ dương.
     * Cách này không cần biết đang dùng camera trước hay sau: nếu phép biến đổi có lật gương,
     * vector đã bị lật nên dấu của góc tự đảo theo.
     */
    fun viewRollDegrees(mappedUpX: Float, mappedUpY: Float): Float {
        if (hypot(mappedUpX, mappedUpY) < 1e-6f) return 0f
        return (atan2(mappedUpX, -mappedUpY) * 180f / PI.toFloat())
    }
}
