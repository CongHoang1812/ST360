package com.example.rankingfilter.ui.makefilter

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.rankingfilter.R
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.userfilter.UserFilterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class MakeFilterUiState(
    val name: String = "",
    /** URI theo thứ tự ô: content:// (Photo Picker), file:// (ảnh chụp tạm hoặc ảnh đã lưu khi sửa). */
    val photos: List<Uri> = emptyList(),
    /** Ảnh bìa người dùng chọn; null thì dùng ảnh đầu tiên. */
    val cover: Uri? = null,
    /** Ô ảnh đang chọn (hiện viền xanh và nút xóa). */
    val selected: Int? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Tên vừa đọc từ Room khi mở chế độ sửa; Fragment đặt vào ô nhập một lần. */
    val loadedName: String? = null,
) {
    val isFull: Boolean get() = photos.size >= FilterDto.PHOTO_COUNT
    val remaining: Int get() = (FilterDto.PHOTO_COUNT - photos.size).coerceAtLeast(0)
    val coverToShow: Uri? get() = cover ?: photos.firstOrNull()
    val canSave: Boolean
        get() = name.isNotBlank() && photos.size in FilterDto.MIN_PHOTO_COUNT..FilterDto.PHOTO_COUNT && !isSaving && !isLoading
}

sealed interface MakeFilterEvent {
    data class Saved(val filterId: String) : MakeFilterEvent
    data class Message(val textRes: Int) : MakeFilterEvent
    /** Chọn nhiều ảnh hơn số ô còn trống: chỉ thêm [added] ảnh. */
    data class TooManyPhotos(val added: Int) : MakeFilterEvent
    data object LoadFailed : MakeFilterEvent
}

class MakeFilterViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    /** null = tạo mới; khác null = sửa filter (argument "filter_id" của nav_graph). */
    val filterId: String? = savedState.get<String>(ARG_FILTER_ID)

    private val repository = UserFilterRepository(application)
    private val capturesDir = File(application.cacheDir, CAPTURES_DIR)

    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<MakeFilterUiState> = _state.asStateFlow()

    private val _events = Channel<MakeFilterEvent>(Channel.BUFFERED)
    val events: Flow<MakeFilterEvent> = _events.receiveAsFlow()

    /** Ảnh chụp lúc mở filter để sửa (hoặc rỗng khi tạo mới), dùng để biết có thay đổi chưa. */
    private var initial: Snapshot = savedState.get<Bundle>(KEY_INITIAL)?.let(Snapshot::from) ?: Snapshot()

    init {
        if (filterId != null && savedState.get<Boolean>(KEY_LOADED) != true) loadForEdit(filterId)
    }

    val hasChanges: Boolean
        get() = _state.value.let { Snapshot(it.name.trim(), it.photos, it.cover) } != initial

    fun setName(name: String) {
        if (name == _state.value.name) return
        update { it.copy(name = name) }
    }

    fun onNameApplied() = _state.update { it.copy(loadedName = null) }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = _state.value.remaining
        val accepted = uris.distinct().filterNot { it in _state.value.photos }.take(remaining)
        if (accepted.isNotEmpty()) update { it.copy(photos = it.photos + accepted, selected = null) }
        if (uris.size > accepted.size && uris.size > remaining) {
            _events.trySend(MakeFilterEvent.TooManyPhotos(accepted.size))
        }
    }

    fun setCover(uri: Uri?) {
        if (uri != null) update { it.copy(cover = uri) }
    }

    /** Chạm ảnh: chọn (hiện nút xóa), chạm lại thì bỏ chọn. */
    fun togglePhoto(index: Int) = update { it.copy(selected = if (it.selected == index) null else index) }

    fun removePhoto(index: Int) = update { state ->
        if (index !in state.photos.indices) return@update state
        val removed = state.photos[index]
        deleteIfCapture(removed)
        state.copy(photos = state.photos.filterIndexed { i, _ -> i != index }, selected = null)
    }

    /** Tạo file tạm trong cache cho camera hệ thống, trả về content:// URI qua FileProvider. */
    fun newCaptureUri(): Uri {
        capturesDir.mkdirs()
        val file = File(capturesDir, "${UUID.randomUUID()}.jpg")
        savedState[KEY_PENDING_CAPTURE] = file.absolutePath
        val app = getApplication<Application>()
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    fun onCaptureResult(success: Boolean) {
        val path = savedState.get<String>(KEY_PENDING_CAPTURE) ?: return
        savedState[KEY_PENDING_CAPTURE] = null
        val file = File(path)
        if (success && file.length() > 0) addPhotos(listOf(file.toUri())) else file.delete()
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val id = repository.save(filterId, current.name, current.photos, current.cover)
                initial = Snapshot(current.name.trim(), current.photos, current.cover)
                clearCaptures()
                _events.send(MakeFilterEvent.Saved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lưu filter lỗi", e)
                _events.send(MakeFilterEvent.Message(R.string.make_filter_save_error))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun loadForEdit(id: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val data = try {
                repository.getById(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Đọc filter $id lỗi", e)
                null
            }
            if (data == null) {
                _state.update { it.copy(isLoading = false) }
                _events.send(MakeFilterEvent.LoadFailed)
                return@launch
            }
            val photos = data.photos.map { File(it.path).toUri() }
            val cover = data.filter.coverPath?.let { File(it).toUri() }
            initial = Snapshot(data.filter.name, photos, cover).also { savedState[KEY_INITIAL] = it.toBundle() }
            savedState[KEY_LOADED] = true
            update {
                it.copy(name = data.filter.name, photos = photos, cover = cover, isLoading = false, loadedName = data.filter.name)
            }
        }
    }

    /** Cập nhật state và lưu phần người dùng nhập vào SavedStateHandle (giữ được khi hệ thống đóng app). */
    private fun update(transform: (MakeFilterUiState) -> MakeFilterUiState) {
        _state.update(transform)
        val s = _state.value
        savedState[KEY_NAME] = s.name
        savedState[KEY_PHOTOS] = ArrayList(s.photos.map(Uri::toString))
        savedState[KEY_COVER] = s.cover?.toString()
    }

    private fun restoreState(): MakeFilterUiState {
        val photos = savedState.get<ArrayList<String>>(KEY_PHOTOS)?.map(Uri::parse) ?: return MakeFilterUiState()
        val name = savedState.get<String>(KEY_NAME).orEmpty()
        return MakeFilterUiState(
            name = name,
            photos = photos,
            cover = savedState.get<String>(KEY_COVER)?.let(Uri::parse),
            loadedName = name,
        )
    }

    private fun deleteIfCapture(uri: Uri) {
        if (uri.scheme != "file") return
        val file = File(uri.path ?: return)
        if (file.parentFile?.absolutePath == capturesDir.absolutePath) file.delete()
    }

    private fun clearCaptures() {
        capturesDir.listFiles()?.forEach { it.delete() }
    }

    override fun onCleared() {
        // Rời màn (lưu xong hoặc bỏ): ảnh chụp tạm không còn cần.
        clearCaptures()
    }

    private data class Snapshot(
        val name: String = "",
        val photos: List<Uri> = emptyList(),
        val cover: Uri? = null,
    ) {
        fun toBundle() = Bundle().apply {
            putString("name", name)
            putStringArrayList("photos", ArrayList(photos.map(Uri::toString)))
            putString("cover", cover?.toString())
        }

        companion object {
            fun from(b: Bundle) = Snapshot(
                name = b.getString("name").orEmpty(),
                photos = b.getStringArrayList("photos").orEmpty().map(Uri::parse),
                cover = b.getString("cover")?.let(Uri::parse),
            )
        }
    }

    companion object {
        const val ARG_FILTER_ID = "filter_id"
        private const val TAG = "MakeFilterViewModel"
        private const val CAPTURES_DIR = "captures"
        private const val KEY_NAME = "name"
        private const val KEY_PHOTOS = "photos"
        private const val KEY_COVER = "cover"
        private const val KEY_LOADED = "loaded"
        private const val KEY_INITIAL = "initial"
        private const val KEY_PENDING_CAPTURE = "pending_capture"
    }
}
