package com.funny.rank.filters.ui.camera.overlay

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.IdentityHashMap
import kotlin.math.min

/**
 * Vẽ một [OverlayScene] lên Canvas. Canvas phải đã được đặt ma trận sao cho đơn vị vẽ là pixel của PreviewView
 * (xem [CameraOverlay]); lớp này không biết gì về camera hay cảm biến.
 *
 * Hình vẽ lặp lại đúng kiểu của `item_rank_slot.xml` (ô đen 50% bo góc, số Londrina trắng, ảnh nhỏ bo góc,
 * số hạng có bóng đứng bên trái) vì đây mới là thứ người dùng thấy và được ghi vào video;
 * các View trong cột hạng chỉ còn là vùng chạm trong suốt.
 *
 * Chỉ dùng trên MỘT luồng (luồng vẽ của OverlayEffect): các Paint và bộ đệm ở đây không an toàn đa luồng.
 */
class OverlayRenderer(
    /** Font Londrina Solid cho số; null thì dùng font mặc định. */
    typeface: Typeface?,
    /** Bán kính bo góc của ô / cạnh ô (10dp / 36dp). */
    private val slotCornerRatio: Float,
    /** Cỡ chữ số / cạnh ô (20sp / 36dp). */
    private val slotTextRatio: Float,
    /** Độ dày viền ô khi đang quay, pixel của view (1dp). */
    slotStrokePx: Float,
) {
    private val slotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLOT_FILL }
    private val slotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = slotStrokePx
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
    }
    private val rankPaint = Paint(numberPaint).apply {
        // Số hạng đứng ngoài ô, trên nền hình camera: thêm bóng để đọc được trên nền sáng.
        setShadowLayer(3f, 0f, 1f, RANK_SHADOW)
    }
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val shaders = IdentityHashMap<Bitmap, BitmapShader>()
    private val shaderMatrix = Matrix()
    private val srcRect = RectF()
    private val dstRect = RectF()
    private val strokeRect = RectF()

    fun draw(canvas: Canvas, scene: OverlayScene) {
        // Ảnh trên đầu vẽ trước, cột hạng vẽ sau: khi đầu nghiêng, ảnh có thể trùm sang cột hạng,
        // mà người chơi cần thấy các ô để chạm (đã gặp trên máy thật).
        drawHead(canvas, scene)
        scene.slots.forEach { drawSlot(canvas, it, scene.slotsOutlined) }
    }

    private fun drawSlot(canvas: Canvas, slot: OverlayScene.Slot, outlined: Boolean) {
        val box = slot.box
        val radius = box.height() * slotCornerRatio
        val textSize = box.height() * slotTextRatio

        val photo = slot.photo
        if (photo == null) {
            // Ô trống: nền đen 50%, số nằm giữa ô.
            canvas.drawRoundRect(box, radius, radius, slotFill)
            numberPaint.textSize = textSize
            canvas.drawText(slot.number, box.centerX(), baselineFor(numberPaint, box.centerY()), numberPaint)
        } else {
            // Ô đã chốt: ảnh nhỏ lấp ô, số hạng đứng bên trái.
            drawRoundedBitmap(canvas, photo, box, radius)
            rankPaint.textSize = textSize
            canvas.drawText(slot.number, slot.rankCenterX, baselineFor(rankPaint, box.centerY()), rankPaint)
        }

        if (outlined) {
            // Viền nằm trọn trong ô (lùi vào nửa độ dày nét), như <stroke> của shape drawable.
            val inset = slotStroke.strokeWidth / 2f
            strokeRect.set(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
            canvas.drawRoundRect(strokeRect, radius - inset, radius - inset, slotStroke)
        }
    }

    private fun drawHead(canvas: Canvas, scene: OverlayScene) {
        val head = scene.head ?: return
        val photo = scene.headPhoto ?: return
        val half = head.size / 2f
        dstRect.set(-half, -half, half, half)
        canvas.save()
        canvas.translate(head.centerX, head.centerY)
        canvas.rotate(head.rotation)
        drawRoundedBitmap(canvas, photo, dstRect, head.size * scene.headCornerRatio)
        canvas.restore()
    }

    /**
     * Vẽ hình vuông ở giữa của [bitmap] (center crop) vào [dst] với góc bo [radius].
     * Dùng BitmapShader + drawRoundRect để mép bo mịn (clipPath không khử răng cưa).
     */
    private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, dst: RectF, radius: Float) {
        if (bitmap.isRecycled) return
        val side = min(bitmap.width, bitmap.height).toFloat()
        val left = (bitmap.width - side) / 2f
        val top = (bitmap.height - side) / 2f
        srcRect.set(left, top, left + side, top + side)

        val shader = shaders[bitmap] ?: run {
            // Ảnh chỉ đổi khi đổi filter; giữ số shader nhỏ để không giữ ảnh của filter cũ.
            if (shaders.size >= MAX_SHADERS) shaders.clear()
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shaders[bitmap] = it }
        }
        shaderMatrix.setRectToRect(srcRect, dst, Matrix.ScaleToFit.FILL)
        shader.setLocalMatrix(shaderMatrix)
        photoPaint.shader = shader
        canvas.drawRoundRect(dst, radius, radius, photoPaint)
        photoPaint.shader = null
    }

    /** Đường gốc của chữ để chữ nằm giữa theo chiều dọc tại [centerY]. */
    private fun baselineFor(paint: Paint, centerY: Float): Float = centerY - (paint.ascent() + paint.descent()) / 2f

    private companion object {
        /** @color/overlay_50 */
        const val SLOT_FILL = 0x80000000.toInt()
        const val RANK_SHADOW = 0x99000000.toInt()

        /** 12 ảnh của filter + ảnh chờ, dư một ít. */
        const val MAX_SHADERS = 32
    }
}
