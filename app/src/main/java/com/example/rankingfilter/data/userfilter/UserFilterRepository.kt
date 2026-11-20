package com.example.rankingfilter.data.userfilter

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Lưu filter tự tạo vào Room. Ảnh được COPY vào filesDir/user_filters/<filterId>/
 * và chỉ lưu đường dẫn file đó; không giữ URI tạm của Photo Picker hay camera
 * (URI đó mất quyền đọc sau khi app đóng).
 */
class UserFilterRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).userFilterDao()
    private val rootDir = File(appContext.filesDir, ROOT_DIR)

    fun observeAll(): Flow<List<UserFilterWithPhotos>> = dao.observeAll()

    suspend fun getById(id: String): UserFilterWithPhotos? = dao.getById(id)

    /**
     * Thêm mới ([id] = null) hoặc sửa filter.
     * [photos]: URI theo đúng thứ tự ô. URI file đã nằm trong thư mục của filter thì giữ nguyên,
     * URI khác (content://, file tạm của camera) được copy vào thư mục filter.
     * [cover]: ảnh bìa, null thì dùng ảnh đầu tiên.
     * Trả về id của filter. Ném IOException nếu không đọc được ảnh.
     */
    suspend fun save(id: String?, name: String, photos: List<Uri>, cover: Uri?): String =
        withContext(Dispatchers.IO) {
            val existing = id?.let { dao.getById(it) }
            val filterId = existing?.filter?.id ?: UUID.randomUUID().toString()
            val dir = filterDir(filterId).apply { mkdirs() }
            val newFiles = mutableListOf<File>()
            try {
                val photoPaths = photos.map { importInto(dir, it, newFiles) }
                val coverPath = cover?.let { importInto(dir, it, newFiles) }
                val filter = UserFilter(
                    id = filterId,
                    name = name.trim(),
                    coverPath = coverPath,
                    createdAt = existing?.filter?.createdAt ?: System.currentTimeMillis(),
                )
                dao.save(
                    filter,
                    photoPaths.mapIndexed { index, path ->
                        UserFilterPhoto(filterId = filterId, position = index, path = path)
                    },
                )
                // Xóa file cũ không còn dùng (ảnh đã bị thay hoặc bỏ khi sửa).
                val keep = (photoPaths + listOfNotNull(coverPath)).toSet()
                dir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
                filterId
            } catch (e: Exception) {
                newFiles.forEach { it.delete() }
                if (existing == null) dir.deleteRecursively()
                throw e
            }
        }

    /** Xóa filter và toàn bộ ảnh của nó. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        filterDir(id).deleteRecursively()
    }

    private fun filterDir(id: String) = File(rootDir, id)

    /** Trả về đường dẫn tuyệt đối của ảnh trong [dir], copy nếu cần. */
    private fun importInto(dir: File, uri: Uri, created: MutableList<File>): String {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            if (file.parentFile?.canonicalPath == dir.canonicalPath && file.exists()) {
                return file.absolutePath
            }
        }
        val target = File(dir, "${UUID.randomUUID()}.${extensionOf(uri)}")
        created += target
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Không mở được ảnh $uri")
        input.use { src -> target.outputStream().use { src.copyTo(it) } }
        return target.absolutePath
    }

    private fun extensionOf(uri: Uri): String {
        val mime = appContext.contentResolver.getType(uri)
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 3..4 }
            ?: "jpg"
    }

    private companion object {
        const val ROOT_DIR = "user_filters"
    }
}
