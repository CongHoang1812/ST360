package com.example.rankingfilter.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/**
 * Cache video trên đĩa cho ExoPlayer: video đã xem một lần sẽ phát lại không cần tải.
 * SimpleCache chỉ được tạo MỘT lần cho mỗi thư mục, nên giữ ở dạng singleton.
 */
@OptIn(UnstableApi::class)
object VideoCache {

    private const val MAX_BYTES = 200L * 1024 * 1024 // 200 MB
    private const val PRELOAD_BYTES = 1_500_000L // ~1,5 MB đầu video

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "video_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    private fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val preloadExecutor = Executors.newSingleThreadExecutor()
    private val preloading = ConcurrentHashMap<String, CacheWriter>()

    /**
     * Tải trước [bytes] byte đầu của [url] vào cache (nền, một luồng), để khi người dùng vuốt tới thì
     * ExoPlayer đọc từ đĩa và phát gần như ngay. Gọi lại với cùng url thì bỏ qua; phần đã có trong cache không tải lại.
     */
    fun preload(context: Context, url: String, bytes: Long = PRELOAD_BYTES) {
        if (preloading.containsKey(url)) return
        val appContext = context.applicationContext
        preloadExecutor.execute {
            val writer = CacheWriter(
                dataSourceFactory(appContext).createDataSource(),
                DataSpec.Builder().setUri(url).setLength(bytes).build(),
                null,
                null,
            )
            preloading[url] = writer
            try {
                writer.cache()
            } catch (_: Exception) {
                // Mất mạng hoặc bị hủy: bỏ qua, lúc phát ExoPlayer tự tải.
            } finally {
                preloading.remove(url)
            }
        }
    }

    /** Hủy các lần tải trước đang chạy (khi rời màn). Phần đã tải xong vẫn nằm trong cache. */
    fun cancelPreload() {
        preloading.values.forEach { it.cancel() }
    }

    /** Tạo ExoPlayer đọc qua cache. Nhớ gọi release() khi xong. */
    fun buildPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory(context)))
            .build()
}
