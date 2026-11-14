package com.example.rankingfilter.ui.camera.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadSmootherTest {

    private val delta = 0.01f
    private fun at(x: Float, rotation: Float = 0f) = HeadPlacement(x, 100f, 50f, rotation)

    @Test
    fun `lan dau thay mat thi nhay thang toi vi tri`() {
        val smoother = HeadSmoother(smoothing = 0.5f)
        val shown = smoother.update(at(200f), nowMs = 0)
        assertEquals(200f, shown!!.centerX, delta)
        assertTrue(smoother.hasFace)
    }

    @Test
    fun `cac lan sau di mot nua quang duong`() {
        val smoother = HeadSmoother(smoothing = 0.5f)
        smoother.update(at(100f), 0)
        assertEquals(150f, smoother.update(at(200f), 30)!!.centerX, delta)
        assertEquals(175f, smoother.update(at(200f), 60)!!.centerX, delta)
    }

    @Test
    fun `mat mat trong thoi gian ngan thi van giu anh`() {
        val smoother = HeadSmoother(hideDelayMs = 150)
        smoother.update(at(100f), 1_000)
        assertNotNull(smoother.update(null, 1_100))
        assertNotNull(smoother.update(null, 1_150))
        assertTrue(smoother.hasFace)
    }

    @Test
    fun `mat mat qua lau thi an, thay lai thi hien ngay o vi tri moi`() {
        val smoother = HeadSmoother(smoothing = 0.5f, hideDelayMs = 150)
        smoother.update(at(100f), 1_000)

        assertNull(smoother.update(null, 1_151))
        assertFalse(smoother.hasFace)

        // Không làm mượt từ vị trí cũ: ảnh hiện thẳng ở chỗ mới.
        assertEquals(300f, smoother.update(at(300f), 1_200)!!.centerX, delta)
    }

    @Test
    fun `goc xoay di duong ngan nhat qua moc 180`() {
        val smoother = HeadSmoother(smoothing = 0.5f)
        smoother.update(at(0f, rotation = 170f), 0)
        // 170 → -170 chỉ cách 20 độ (qua 180), không phải 340 độ.
        val rotation = smoother.update(at(0f, rotation = -170f), 30)!!.rotation
        assertEquals(180f, rotation, delta)
    }

    @Test
    fun `clear an anh ngay`() {
        val smoother = HeadSmoother()
        smoother.update(at(100f), 0)
        smoother.clear()
        assertNull(smoother.current)
    }
}
