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
import com.example.rankingfilter.data.music.MusicRepository
import com.example.rankingfilter.data.music.MusicTrack
import com.example.rankingfilter.data.userfilter.UserFilterRepository
import com.example.rankingfilter.ui.camera.game.RankingGame
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

/**
 * Trạng thái trò chơi xếp hạng để vẽ lên màn hình. Mặc định là chưa chơi.
 * Ở bước 5d, "đang chơi" cũng chính là "đang quay video".
 */
data class GameUi(
    /** Đã bắt đầu chơi (kể cả lúc vừa xếp xong, chờ chuyển màn). */
    val isActive: Boolean = false,
    /** Ảnh đã chốt theo từng ô, null = ô trống. Rỗng khi chưa chơi. */
    val slots: List<Bitmap?> = emptyList(),
    /** Ảnh đang hiện trên đầu; null khi chưa chơi hoặc hết ảnh. */
    val head: Bitmap? = null,
    /** Đã chốt đủ 12 ô. */
    val isFinished: Boolean = false,
) {
    /** Đang chơi và còn ô để chốt. */
    val isPlaying: Boolean get() = isActive && !isFinished
}

/** Kết quả của [CameraViewModel.startGame]. */
enum class GameStart { STARTED, FILTER_NOT_READY, NOT_ENOUGH_PHOTOS }

data class CameraUiState(
    val filter: CameraFilter? = null,
    val isFilterLoading: Boolean = true,
    val duration: RecordDuration = RecordDuration.FREE,
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    val game: GameUi = GameUi(),
    /** Nhạc đã chọn ở Add Sound; null = giữ tiếng gốc. */
    val music: MusicTrack? = null,
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
            music = savedState.get<Int>(KEY_MUSIC)?.let(MusicRepository::track),
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

    /** Chọn nhạc nền (null = bỏ nhạc, giữ tiếng gốc). */
    fun setMusic(id: Int?) {
        val track = id?.let(MusicRepository::track)
        savedState[KEY_MUSIC] = track?.id
        _state.update { it.copy(music = track) }
    }

    fun setLensFacing(lensFacing: Int) {
        savedState[KEY_LENS] = lensFacing
        _state.update { it.copy(lensFacing = lensFacing) }
    }

    // ---------------------------------------------------------------- Trò chơi xếp hạng
    // Một ván gắn với một lần quay video: CameraFragment bắt đầu ván khi video bắt đầu ghi
    // và dừng ván khi video dừng (kể cả khi rời màn hay xoay màn hình, vì lúc đó bản ghi bị bỏ).

    private var game: RankingGame<Bitmap>? = null

    /** Kiểm trước khi quay: filter đã tải xong và đủ 12 ảnh chưa. Không đổi trạng thái gì. */
    fun checkGameReady(): GameStart {
        val filter = _state.value.filter ?: return GameStart.FILTER_NOT_READY
        if (filter.photos.size < FilterDto.PHOTO_COUNT) return GameStart.NOT_ENOUGH_PHOTOS
        return GameStart.STARTED
    }

    /** Bắt đầu một ván mới với 12 ảnh của filter (xáo trộn, ô trống hết). Gọi khi video bắt đầu ghi. */
    fun startGame(): GameStart {
        val ready = checkGameReady()
        if (ready != GameStart.STARTED) return ready
        game = RankingGame(_state.value.filter!!.photos, FilterDto.PHOTO_COUNT)
        publishGame()
        return GameStart.STARTED
    }

    /** Dừng ván hiện tại và về trạng thái chưa chơi (video đã dừng: lưu xong, bị bỏ hoặc lỗi). */
    fun stopGame() {
        game = null
        publishGame()
    }

    /** Đổi ảnh trên đầu ngẫu nhiên. Gọi mỗi ~100ms khi đang chơi và thấy mặt. */
    fun spin() {
        val current = game ?: return
        if (current.isFinished) return
        current.spin()
        publishGame()
    }

    /** Chốt ảnh đang hiện vào ô [index] (0..11). Trả về true nếu thành công. */
    fun placePhoto(index: Int): Boolean {
        val current = game ?: return false
        if (current.isFinished || !current.place(index)) return false
        publishGame()
        return true
    }

    private fun publishGame() {
        val current = game
        _state.update {
            it.copy(
                game = if (current == null) {
                    GameUi()
                } else {
                    GameUi(
                        isActive = true,
                        slots = current.slots.toList(),
                        head = current.current,
                        isFinished = current.isFinished,
                    )
                },
            )
        }
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
        private const val KEY_MUSIC = "music_id"

        /** Cạnh dài tối đa của ảnh sau khi giải mã: đủ nét cho ảnh trên đầu (~100dp) mà nhẹ bộ nhớ. */
        private const val PHOTO_SIZE_PX = 384
    }
}
