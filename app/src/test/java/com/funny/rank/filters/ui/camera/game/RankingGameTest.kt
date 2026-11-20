package com.example.rankingfilter.ui.camera.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RankingGameTest {

    private val photos = (1..12).toList()

    @Test
    fun `thieu anh thi khong tao duoc game`() {
        try {
            RankingGame((1..11).toList())
            fail("Phải báo lỗi khi thiếu ảnh")
        } catch (e: IllegalArgumentException) {
            // đúng như mong đợi
        }
    }

    @Test
    fun `luc dau 12 o deu trong va co san anh tren dau`() {
        val game = RankingGame(photos)
        assertEquals(12, game.slots.size)
        assertTrue(game.slots.all { it == null })
        assertNotNull(game.current)
        assertFalse(game.isFinished)
    }

    @Test
    fun `chot anh dang hien vao o trong`() {
        val game = RankingGame(photos)
        val shown = game.current

        assertTrue(game.place(3))

        assertEquals(shown, game.slots[3])
        assertNotNull(game.current)
        assertFalse(game.current == shown)
    }

    @Test
    fun `o da chot khong chot lai duoc`() {
        val game = RankingGame(photos)
        game.place(0)
        val before = game.current

        assertFalse(game.place(0))

        assertEquals(before, game.current)
        assertEquals(1, game.slots.count { it != null })
    }

    @Test
    fun `chi so ngoai pham vi bi tu choi`() {
        val game = RankingGame(photos)
        assertFalse(game.place(-1))
        assertFalse(game.place(12))
        assertTrue(game.slots.all { it == null })
    }

    @Test
    fun `anh da chot bi loai khoi danh sach cho, spin khong lap lai no`() {
        val game = RankingGame(photos)
        val placed = mutableSetOf<Int>()
        repeat(6) { slot ->
            game.place(slot)
            placed += game.slots[slot]!!
        }
        repeat(500) {
            game.spin()
            assertFalse("spin lấy lại ảnh đã chốt", game.current in placed)
        }
    }

    @Test
    fun `chot du 12 o thi ket thuc, moi anh dung mot lan`() {
        val game = RankingGame(photos)
        // Chốt theo thứ tự ô lộn xộn, có spin xen giữa như khi chơi thật.
        listOf(5, 0, 11, 2, 7, 1, 10, 3, 8, 4, 9, 6).forEach { slot ->
            game.spin()
            assertFalse(game.isFinished)
            assertTrue(game.place(slot))
        }

        assertTrue(game.isFinished)
        assertEquals(photos.toSet(), game.slots.filterNotNull().toSet())
        assertEquals(12, game.slots.filterNotNull().size)
        assertNull(game.current)
    }

    @Test
    fun `het anh thi khong chot them va spin khong lam gi`() {
        val game = RankingGame(photos)
        (0..11).forEach { game.place(it) }

        game.spin()

        assertNull(game.current)
        assertFalse(game.place(0))
    }

    @Test
    fun `filter nhieu hon 12 anh chi dung 12 anh`() {
        val game = RankingGame((1..20).toList())
        (0..11).forEach { assertTrue(game.place(it)) }
        assertTrue(game.isFinished)
        assertEquals(12, game.slots.filterNotNull().toSet().size)
    }
}
