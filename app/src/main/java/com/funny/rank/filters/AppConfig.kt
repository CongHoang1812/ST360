package com.funny.rank.filters

import com.funny.rank.filters.data.trending.TrendingApi

object AppConfig {
    /**
     * Danh sách thư mục video Trending trên server (mảng chuỗi JSON).
     * Lỗi mạng thì Home dùng ảnh avatar của các filter làm dữ liệu dự phòng.
     */
    const val TRENDING_FOLDERS_URL = TrendingApi.FOLDERS_URL

    /** Trang chính sách bảo mật (mục Privacy & Policy trong Setting). TODO: điền link thật. */
    const val PRIVACY_POLICY_URL = "https://sites.google.com/view/ranking-filter-funny-game/home"
}
