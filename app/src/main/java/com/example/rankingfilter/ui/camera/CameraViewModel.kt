package com.example.rankingfilter.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.rankingfilter.data.BuiltInFilterRepository
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.SelectedFilterStore
import com.example.rankingfilter.data.assetUri
import com.example.rankingfilter.data.userfilter.UserFilterRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Thời lượng quay, theo đúng thứ tự trên dải trong design: 60s, 30s, Free, 15s. */
enum class RecordDuration(val seconds: Int?) {
    SEC_60(60),
    SEC_30(30),
    /** Không giới hạn: quay tới khi bấm dừng hoặc xếp xong 12 ô. */
    FREE(null),
    SEC_15(15),
}

/** Filter đang dùng trên Camera, đã giải mã sẵn ảnh để vẽ lên đầu và vào ô hạng (bước 5b–5d). */
class CameraFilter(
    val id: String,
    val title: String,
    val isUserFilter: Boolean,
    /** Ảnh theo đúng thứ tự trong filter; bitmap phần mềm (vẽ được lên Canvas của video). */
    val photos: List<Bitmap>,
)

data class CameraUiState(
    val filter: CameraFilter? = null,
    val isFilterLoading: Boolean = true,
    val duration: RecordDuration = RecordDuration.FREE,
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
)

sealed interface CameraEvent {
    /** Không tìm thấy filter (đã bị xóa) hoặc không đọc được ảnh nào. */
    data object FilterMissing : CameraEvent
}

class CameraViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        CameraUiState(
            duration = savedState.get<String>(KEY_DURATION)
                ?.let { runCatching { RecordDuration.valueOf(it) }.getOrNull() }
                ?: RecordDuration.FREE,
            lensFacing = savedState.get<Int>(KEY_LENS) ?: CameraSelector.LENS_FACING_FRONT,
        )
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    init {
        loadFilter()
    }

    fun setDuration(duration: RecordDuration) {
        savedState[KEY_DURATION] = duration.name
        _state.update { it.copy(duration = duration) }
    }

    fun setLensFacing(lensFacing: Int) {
        savedState[KEY_LENS] = lensFacing
        _state.update { it.copy(lensFacing = lensFacing) }
    }

    /**
     * Đọc filter theo argument "filter_id" / "is_user_filter" của nav_graph;
     * thiếu argument thì dùng filter chọn gần nhất (SelectedFilterStore).
     */
    private fun loadFilter() {
        val store = SelectedFilterStore(getApplication())
        val id = savedState.get<String>(ARG_FILTER_ID) ?: store.lastFilterId
        val isUser = if (savedState.contains(ARG_IS_USER_FILTER)) {
            savedState.get<Boolean>(ARG_IS_USER_FILTER) == true
        } else {
            store.lastIsUserFilter
        }
        viewModelScope.launch {
            val filter = try {
                id?.let { if (isUser) loadUserFilter(it) else loadBuiltInFilter(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Đọc filter $id lỗi", e)
                null
            }
            if (filter == null || filter.photos.isEmpty()) {
                Log.w(TAG, "Không có filter để dùng (id=$id, isUser=$isUser)")
                _state.update { it.copy(isFilterLoading = false) }
                _events.send(CameraEvent.FilterMissing)
                return@launch
            }
            if (filter.photos.size != FilterDto.PHOTO_COUNT) {
                Log.w(TAG, "Filter '${filter.id}' có ${filter.photos.size} ảnh, cần ${FilterDto.PHOTO_COUNT}")
            }
            Log.d(TAG, "Filter '${filter.title}' (id=${filter.id}, user=$isUser): ${filter.photos.size} ảnh")
            _state.update { it.copy(filter = filter, isFilterLoading = false) }
        }
    }

    private suspend fun loadBuiltInFilter(id: String): CameraFilter? {
        val dto = BuiltInFilterRepository.getAll(getApplication()).firstOrNull { it.id == id } ?: return null
        return CameraFilter(dto.id, dto.title, isUserFilter = false, photos = decodeAll(dto.photos.map(::assetUri)))
    }

    private suspend fun loadUserFilter(id: String): CameraFilter? {
        val data = UserFilterRepository(getApplication()).getById(id) ?: return null
        val uris = data.photos.map { File(it.path).toUri().toString() }
        return CameraFilter(data.filter.id, data.filter.name, isUserFilter = true, photos = decodeAll(uris))
    }

    /** Giải mã song song bằng Coil (dùng lại cache, tự xoay theo EXIF); ảnh lỗi bị bỏ qua. */
    private suspend fun decodeAll(uris: List<String>): List<Bitmap> = coroutineScope {
        val context = getApplication<Application>()
        val loader = SingletonImageLoader.get(context)
        uris.map { uri ->
            async {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(PHOTO_SIZE_PX)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                (result as? SuccessResult)?.image?.toBitmap()
                    ?: null.also { Log.w(TAG, "Không đọc được ảnh $uri") }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        const val ARG_FILTER_ID = "filter_id"
        const val ARG_IS_USER_FILTER = "is_user_filter"
        private const val TAG = "CameraViewModel"
        private const val KEY_DURATION = "duration"
        private const val KEY_LENS = "lens_facing"

        /** Cạnh dài tối đa của ảnh sau khi giải mã: đủ nét cho ảnh trên đầu (~100dp) mà nhẹ bộ nhớ. */
        private const val PHOTO_SIZE_PX = 384
    }
}
