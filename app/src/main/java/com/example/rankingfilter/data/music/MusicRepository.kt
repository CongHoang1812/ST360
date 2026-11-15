package com.example.rankingfilter.data.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.rankingfilter.data.userfilter.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Một bài nhạc nền. [durationMs] null = chưa biết (đọc khi cần bằng [MusicRepository.duration]). */
data class MusicTrack(
    val id: Int,
    val title: String,
    /** Link mp3 trên server. */
    val url: String,
    val durationMs: Long? = null,
)

/**
 * 74 bài nhạc trên server (cấu hình `config.json` của ST314: base_url `music/`, tiền tố `music_`, đuôi `.mp3`,
 * đánh số 1..74). Không đóng gói nhạc trong app.
 *
 * - Thời lượng lưu vào Room (bảng `music_meta`): lần sau mở app là có luôn, không phải chờ mạng.
 *   [ensureLoaded] nạp Room vào bộ nhớ; [warmUp] (gọi ở Splash) nạp rồi đọc nền các bài còn thiếu.
 * - [duration]: đọc thời lượng của MỘT bài (dòng hiện ra trên màn hình hoặc [warmUp]), tối đa
 *   [MAX_PARALLEL_PROBES] bài cùng lúc, nhớ kết quả trong bộ nhớ và Room.
 * - [localFile]: tải bài về cache (`cacheDir/music/`) để phát lúc quay và trộn vào video không cần mạng.
 */
object MusicRepository {

    private const val TAG = "MusicRepository"

    const val BASE_URL = "https://lvtglobal.site/public/app/ST314_FunnyFaceFilters/"
    private const val FOLDER = "music/"
    private const val PREFIX = "music_"
    private const val EXTENSION = ".mp3"
    const val COUNT = 74

    private const val MAX_PARALLEL_PROBES = 2
    private const val TIMEOUT_MS = 15_000

    val tracks: List<MusicTrack> = (1..COUNT).map { i ->
        MusicTrack(id = i, title = "Music $i", url = BASE_URL + FOLDER + PREFIX + i + EXTENSION)
    }

    fun track(id: Int): MusicTrack? = tracks.getOrNull(id - 1)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var loading: Deferred<Unit>? = null
    private var warmedUp = false

    private val durations = ConcurrentHashMap<Int, Long>()
    private val failedDurations = ConcurrentHashMap.newKeySet<Int>()
    private val probeLimit = Semaphore(MAX_PARALLEL_PROBES)
    private val downloadLock = Mutex()

    /** Nạp thời lượng đã lưu trong Room vào bộ nhớ (một lần; gọi lại thì chờ lần đang chạy). Rất nhanh, không dùng mạng. */
    suspend fun ensureLoaded(context: Context) {
        val job = synchronized(this) {
            appContext = context.applicationContext
            loading ?: scope.async<Unit> {
                try {
                    AppDatabase.get(context.applicationContext).musicMetaDao().getAll()
                        .forEach { durations.putIfAbsent(it.id, it.durationMs) }
                } catch (e: Exception) {
                    Log.w(TAG, "Không đọc được thời lượng nhạc đã lưu", e)
                }
            }.also { loading = it }
        }
        job.await()
    }

    /**
     * Gọi lúc mở app (Splash): nạp Room, rồi đọc nền thời lượng các bài còn thiếu (tối đa [MAX_PARALLEL_PROBES] bài cùng lúc)
     * để lần mở Add Sound sau có đủ số liệu. Mỗi lần chạy app chỉ làm một lần; bài đọc lỗi (mất mạng) để lần sau.
     */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (warmedUp) return
            warmedUp = true
        }
        scope.launch {
            ensureLoaded(app)
            tracks.filter { durations[it.id] == null }.forEach { track ->
                launch { duration(app, track) }
            }
        }
    }

    /** Thời lượng đã biết (không chờ). */
    fun knownDuration(id: Int): Long? = durations[id]

    /** Ghi lại thời lượng biết được từ chỗ khác (ví dụ ExoPlayer khi nghe thử). */
    fun rememberDuration(id: Int, durationMs: Long) {
        if (durationMs <= 0 || durations[id] == durationMs) return
        durations[id] = durationMs
        persist(id, durationMs)
    }

    private fun persist(id: Int, durationMs: Long) {
        val app = appContext ?: return
        scope.launch {
            try {
                AppDatabase.get(app).musicMetaDao().upsert(MusicMeta(id, durationMs))
            } catch (e: Exception) {
                Log.w(TAG, "Không lưu được thời lượng nhạc $id", e)
            }
        }
    }

    /**
     * Thời lượng của bài [track] (ms), null nếu không đọc được (mất mạng, file không tồn tại).
     * Ưu tiên file đã tải; không có thì đọc phần đầu file trên server bằng MediaMetadataRetriever.
     */
    suspend fun duration(context: Context, track: MusicTrack): Long? {
        track.durationMs?.let { return it }
        appContext = appContext ?: context.applicationContext
        durations[track.id]?.let { return it }
        if (track.id in failedDurations) return null
        return probeLimit.withPermit {
            durations[track.id] ?: withContext(Dispatchers.IO) {
                val cached = cachedFile(context, track)
                val retriever = MediaMetadataRetriever()
                try {
                    if (cached.exists()) retriever.setDataSource(cached.absolutePath)
                    else retriever.setDataSource(track.url, emptyMap())
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.also { ms ->
                            durations[track.id] = ms
                            persist(track.id, ms)
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "Không đọc được thời lượng ${track.url}", e)
                    failedDurations += track.id
                    null
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /** Nơi lưu bài nhạc đã tải (có thể chưa tồn tại). */
    fun cachedFile(context: Context, track: MusicTrack): File =
        File(File(context.applicationContext.cacheDir, "music"), "$PREFIX${track.id}$EXTENSION")

    /**
     * File nhạc trên máy; chưa có thì tải về (ghi vào file tạm rồi đổi tên, nên không bao giờ để lại file dở).
     * Ném IOException nếu không tải được.
     */
    suspend fun localFile(context: Context, track: MusicTrack): File = downloadLock.withLock {
        val target = cachedFile(context, track)
        if (target.exists() && target.length() > 0) return@withLock target
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".part")
            val connection = URL(track.url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code khi tải ${track.url}")
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                if (temp.length() == 0L) throw IOException("File rỗng: ${track.url}")
                if (!temp.renameTo(target)) throw IOException("Không đổi tên được ${temp.path}")
                target
            } catch (e: Exception) {
                temp.delete()
                throw if (e is IOException) e else IOException(e)
            } finally {
                connection.disconnect()
            }
        }
    }
}
