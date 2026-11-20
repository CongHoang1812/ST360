package com.example.rankingfilter.ui.camera.face

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadPlacementsTest {

    private val delta = 0.01f

    @Test
    fun `dau thang - anh nam ngay tren khung mat, khong xoay`() {
        // Khung mặt 200 x 250 tại (100, 300) → tâm (200, 425).
        val p = HeadPlacements.place(100f, 300f, 300f, 550f, rollDegrees = 0f)

        val size = 200f * HeadPlacements.SIZE_RATIO
        val distance = 250f / 2f + 250f * HeadPlacements.GAP_RATIO + size / 2f
        assertEquals(size, p.size, delta)
        assertEquals(200f, p.centerX, delta)
        assertEquals(425f - distance, p.centerY, delta)
        assertEquals(0f, p.rotation, delta)
    }

    @Test
    fun `dau nghieng 90 do sang phai - anh nam ben phai khung mat`() {
        // Khung bị phình thành 250 x 200 khi đầu nằm ngang; bề rộng thật vẫn là cạnh ngắn (200).
        val p = HeadPlacements.place(75f, 325f, 325f, 525f, rollDegrees = 90f)

        val size = 200f * HeadPlacements.SIZE_RATIO
        val distance = 250f / 2f + 250f * HeadPlacements.GAP_RATIO + size / 2f
        assertEquals(size, p.size, delta)
        assertEquals(200f + distance, p.centerX, delta)
        assertEquals(425f, p.centerY, delta)
        assertEquals(90f, p.rotation, delta)
    }

    @Test
    fun `ML Kit euler Z duong la quay nguoc chieu kim dong ho`() {
        assertEquals(-20f, HeadPlacements.clockwiseDegreesFromEulerZ(20f), delta)
        assertEquals(15f, HeadPlacements.clockwiseDegreesFromEulerZ(-15f), delta)
    }

    @Test
    fun `vector dinh dau theo goc kim dong ho`() {
        val (x0, y0) = HeadPlacements.upVector(0f)
        assertEquals(0f, x0, delta)
        assertEquals(-1f, y0, delta)

        val (x90, y90) = HeadPlacements.upVector(90f)
        assertEquals(1f, x90, delta)
        assertEquals(0f, y90, delta)
    }

    @Test
    fun `goc trong view - thang len la 0, chi sang phai la 90, chi sang trai la -90`() {
        assertEquals(0f, HeadPlacements.viewRollDegrees(0f, -1f), delta)
        assertEquals(90f, HeadPlacements.viewRollDegrees(1f, 0f), delta)
        assertEquals(-90f, HeadPlacements.viewRollDegrees(-1f, 0f), delta)
        assertEquals(180f, Math.abs(HeadPlacements.viewRollDegrees(0f, 1f)), delta)
    }

    @Test
    fun `lat guong ngang dao dau goc nghieng`() {
        // Đầu nghiêng 20° kim đồng hồ trong ảnh phân tích; qua phép lật gương ngang (x → −x)
        // thì trong view nó nghiêng 20° ngược chiều kim đồng hồ.
        val (ux, uy) = HeadPlacements.upVector(20f)
        val inView = HeadPlacements.viewRollDegrees(-ux, uy)
        assertEquals(-20f, inView, delta)
    }

    @Test
    fun `camera truoc - euler Z duong hien thanh nghieng cung chieu kim dong ho trong view`() {
        // θ = +20 (ML Kit) → φ = −20 trong ảnh thô; preview camera trước lật gương nên view thấy +20.
        val phi = HeadPlacements.clockwiseDegreesFromEulerZ(20f)
        val (ux, uy) = HeadPlacements.upVector(phi)
        assertEquals(20f, HeadPlacements.viewRollDegrees(-ux, uy), delta)
    }
}
