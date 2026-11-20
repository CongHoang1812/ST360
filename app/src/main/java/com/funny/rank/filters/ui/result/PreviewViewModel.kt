package com.funny.rank.filters.ui.result

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.funny.rank.filters.R
import com.funny.rank.filters.data.music.MusicRepository
import com.funny.rank.filters.data.video.VideoLibrary
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

data class PreviewUi(
    val draft: File? = null,
    /** File nhạc đã chọn (đã tải về máy); null = giữ tiếng gốc. */
    val music: File? = null,
    /** Đang chuẩn bị file nhạc (thường có sẵn từ lúc chọn ở Add Sound). */
    val isMusicLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** 0..100 khi đang trộn nhạc; null = chưa biết. */
    val saveProgress: Int? = null,
)

sealed interface PreviewEvent {
    data class Saved(val file: File) : PreviewEvent
    data class Message(val textRes: Int) : PreviewEvent
    /** Không còn bản nháp (đã bị xóa): quay về Camera. */
    data object DraftMissing : PreviewEvent
}

class PreviewViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val draft: File? = savedState.get<String>(ARG_VIDEO_PATH)?.let(::File)
    private val musicId: Int? = savedState.get<Int>(ARG_MUSIC_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(PreviewUi(draft = draft))
    val state: StateFlow<PreviewUi> = _state.asStateFlow()

    private val _events = Channel<PreviewEvent>(Channel.BUFFERED)
    val events: Flow<PreviewEvent> = _events.receiveAsFlow()

    init {
        if (draft == null || !draft.exists()) {
            _events.trySend(PreviewEvent.DraftMissing)
        } else {
            loadMusic()
        }
    }

    private fun loadMusic() {
        val track = musicId?.let(MusicRepository::track) ?: return
        _state.update { it.copy(isMusicLoading = true) }
        viewModelScope.launch {
            val file = try {
                MusicRepository.localFile(getApplication(), track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Không lấy được nhạc ${track.url}", e)
                _events.send(PreviewEvent.Message(R.string.preview_music_missing))
                null
            }
            _state.update { it.copy(music = file, isMusicLoading = false) }
        }
    }

    /** Lưu vào thư viện của app (trộn nhạc nếu có). */
    fun save() {
        val current = _state.value
        val source = current.draft ?: return
        if (current.isSaving || current.isMusicLoading) return
        _state.update { it.copy(isSaving = true, saveProgress = null) }
        viewModelScope.launch {
            try {
                val saved = VideoLibrary.save(getApplication(), source, current.music) { progress ->
                    _state.update { it.copy(saveProgress = progress) }
                }
                _events.send(PreviewEvent.Saved(saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lưu video lỗi", e)
                _events.send(PreviewEvent.Message(R.string.preview_save_error))
            } finally {
                _state.update { it.copy(isSaving = false, saveProgress = null) }
            }
        }
    }

    /** Bỏ video vừa quay (quay lại làm lại, hoặc rời đi không lưu). */
    fun discard() {
        draft?.let(VideoLibrary::deleteDraft)
    }

    companion object {
        const val ARG_VIDEO_PATH = "video_path"
        const val ARG_MUSIC_ID = "music_id"
        private const val TAG = "PreviewViewModel"
    }
}
