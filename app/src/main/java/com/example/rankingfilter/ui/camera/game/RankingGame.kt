package com.example.rankingfilter.ui.camera.game

/**
 * Logic trò chơi xếp hạng, không phụ thuộc Android.
 *
 * Ảnh trên đầu đổi ngẫu nhiên ([spin]); người chơi chạm một ô trống để chốt ảnh đang hiện vào ô đó ([place]).
 * Ảnh đã chốt bị loại khỏi danh sách chờ. Chốt đủ [slotCount] ô thì [isFinished].
 *
 * Viết theo lớp `RankingGame` trong phụ lục sổ tay; chỉ đổi `Bitmap` thành kiểu tham số [T]
 * (trong app dùng `RankingGame<Bitmap>`) để chạy được bằng JUnit.
 *
 * Không an toàn đa luồng: gọi từ luồng chính.
 */
class RankingGame<T : Any>(photos: List<T>, val slotCount: Int = 12) {

    init {
        require(photos.size >= slotCount) { "Cần đủ $slotCount ảnh" }
    }

    /** Ảnh còn chờ được chốt. Lấy ngẫu nhiên [slotCount] ảnh nếu filter có nhiều hơn. */
    private val pool = photos.shuffled().take(slotCount).toMutableList()

    private val _slots = MutableList<T?>(slotCount) { null }

    /** Ảnh đã chốt theo từng ô (null = ô trống). Danh sách này đổi theo trò chơi; muốn giữ bản chụp thì `toList()`. */
    val slots: List<T?> get() = _slots

    /** Ảnh đang hiện trên đầu; null khi đã hết ảnh. */
    var current: T? = pool.firstOrNull()
        private set

    /** Gọi bằng Handler/coroutine mỗi ~100ms để ảnh trên đầu đổi ngẫu nhiên. */
    fun spin() {
        if (pool.isNotEmpty()) current = pool.random()
    }

    /** Gọi khi chạm ô. Trả về true nếu đặt được ảnh vào ô. */
    fun place(index: Int): Boolean {
        if (index !in _slots.indices || _slots[index] != null) return false
        val pick = current ?: return false
        _slots[index] = pick
        pool.remove(pick)
        current = pool.randomOrNull()
        return true
    }

    val isFinished: Boolean get() = _slots.all { it != null }
}
