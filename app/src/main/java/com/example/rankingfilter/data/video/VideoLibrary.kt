package com.example.rankingfilter.data.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.coroutineScope

/**
 * Nơi chứa video của app:
 * - `filesDir/drafts/`: video vừa quay, chờ người dùng xem ở màn Preview (Save hoặc quay lại làm lại).
 * - `filesDir/videos/`: video đã Save, hiện trong Gallery của app (giai đoạn 7).
 * - Thư viện ảnh của máy (`Movies/RankingFilter`): chỉ khi người dùng bấm Download ở màn Successful.
 *
 * Quay vào bộ nhớ riêng của app thay vì thẳng ra thư viện ảnh, để video bị bỏ (quay lại làm lại)
 * không bao giờ lọt vào thư viện ảnh của người dùng.
 */
object VideoLibrary {

    private const val DRAFTS = "drafts"
    private const val VIDEOS = "videos"
    const val ALBUM = "RankingFilter"

    fun draftsDir(context: Context) = File(context.applicationContext.filesDir, DRAFTS).apply { mkdirs() }
    fun videosDir(context: Context) = File(context.applicationContext.filesDir, VIDEOS).apply { mkdirs() }

    /** File cho bản ghi mới. Xóa bản nháp cũ: mỗi lúc chỉ có một video chờ ở Preview. */
    fun newDraft(context: Context): File {
        val dir = draftsDir(context)
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "draft_${timestamp()}.mp4")
    }

    fun deleteDraft(file: File) {
        if (file.parentFile?.name == DRAFTS) file.delete()
    }

    /**
     * Lưu bản nháp vào thư viện của app. Có [music] thì thay tiếng micro bằng nhạc (lặp lại nếu nhạc ngắn hơn video,
     * cắt ở cuối video) bằng Media3 Transformer; không có thì chỉ chuyển file sang chỗ mới.
     * [onProgress] nhận 0..100 trên luồng chính. Ném IOException nếu lỗi; bản nháp được giữ nguyên để thử lại.
     */
    suspend fun save(context: Context, draft: File, music: File?, onProgress: (Int) -> Unit): File {
        val output = File(videosDir(context), "RankingFilter_${timestamp()}.mp4")
        if (music == null) {
            withContext(Dispatchers.IO) {
                if (!draft.renameTo(output)) {
                    draft.copyTo(output, overwrite = true)
                    draft.delete()
                }
            }
            return output
        }
        try {
            // Thử giữ nguyên hình (không mã hóa lại video, nhanh và không giảm chất lượng);
            // máy nào không cho thì mã hóa lại.
            try {
                mixMusic(context, draft, music, output, transmuxVideo = true, onProgress)
            } catch (e: ExportException) {
                output.delete()
                mixMusic(context, draft, music, output, transmuxVideo = false, onProgress)
            }
        } catch (e: Exception) {
            output.delete()
            throw if (e is IOException) e else IOException("Không trộn được nhạc vào video", e)
        }
        withContext(Dispatchers.IO) { draft.delete() }
        return output
    }

    /**
     * Transformer: chuỗi 1 = video đã quay, bỏ tiếng; chuỗi 2 = nhạc, lặp lại cho đủ dài.
     * Độ dài kết quả theo chuỗi đầu (video). Transformer phải chạy trên luồng có Looper: dùng luồng chính.
     */
    @OptIn(UnstableApi::class)
    private suspend fun mixMusic(
        context: Context,
        draft: File,
        music: File,
        output: File,
        transmuxVideo: Boolean,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val video = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(draft))).setRemoveAudio(true).build()
        val audio = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(music))).build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(video).build(),
            EditedMediaItemSequence.Builder(audio).setIsLooping(true).build(),
        ).setTransmuxVideo(transmuxVideo).build()

        coroutineScope {
            val transformer = Transformer.Builder(context.applicationContext).build()
            val progressJob = launch {
                val holder = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    delay(200)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    cont.invokeOnCancellation { transformer.cancel() }
                    transformer.start(composition, output.absolutePath)
                }
            } finally {
                progressJob.cancel()
            }
        }
    }

    /** Chép video ra thư viện ảnh của máy (Movies/RankingFilter). Trả về content:// của bản chép. */
    suspend fun exportToGallery(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                // Android 9 trở xuống: đường dẫn tuyệt đối, cần quyền WRITE_EXTERNAL_STORAGE.
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), ALBUM)
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(dir, file.name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Không tạo được mục trong thư viện ảnh")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Không mở được $uri để ghi")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is IOException) e else IOException(e)
        }
    }

    /** content:// cho app khác đọc video (chia sẻ, mở bằng trình phát), qua FileProvider. */
    fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
