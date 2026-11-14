package com.example.rankingfilter.ui.result

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.rankingfilter.R
import com.example.rankingfilter.data.BuiltInFilterRepository
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.video.VideoLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SuccessUi(
    val video: File? = null,
    val thumbnail: Bitmap? = null,
    val isDownloading: Boolean = false,
    /** Đã chép ra thư viện ảnh của máy trong lần mở màn này. */
    val isDownloaded: Boolean = false,
    /** Dải "Try More Filters". */
    val filters: List<FilterDto> = emptyList(),
)

sealed interface SuccessEvent {
    data class Message(val textRes: Int) : SuccessEvent
}

class SuccessViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val video: File? = savedState.get<String>(ARG_VIDEO_PATH)?.let(::File)

    private val _state = MutableStateFlow(
        SuccessUi(video = video, isDownloaded = savedState.get<Boolean>(KEY_DOWNLOADED) == true)
    )
    val state: StateFlow<SuccessUi> = _state.asStateFlow()

    private val _events = Channel<SuccessEvent>(Channel.BUFFERED)
    val events: Flow<SuccessEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(thumbnail = loadThumbnail()) } }
        viewModelScope.launch {
            val all = BuiltInFilterRepository.getAll(getApplication())
            _state.update { it.copy(filters = all.shuffled().take(MAX_SUGGESTIONS)) }
        }
    }

    /** Download: chép video ra thư viện ảnh của máy (Movies/RankingFilter). */
    fun download() {
        val file = video ?: return
        val current = _state.value
        if (current.isDownloading || current.isDownloaded) return
        _state.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                VideoLibrary.exportToGallery(getApplication(), file)
                savedState[KEY_DOWNLOADED] = true
                _state.update { it.copy(isDownloaded = true) }
                _events.send(SuccessEvent.Message(R.string.success_downloaded))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chép video ra thư viện ảnh lỗi", e)
                _events.send(SuccessEvent.Message(R.string.success_download_error))
            } finally {
                _state.update { it.copy(isDownloading = false) }
            }
        }
    }

    /** Ảnh thu nhỏ: khung hình ở giây thứ 1 (giây 0 thường còn tối vì camera đang chỉnh sáng). */
    private suspend fun loadThumbnail(): Bitmap? = withContext(Dispatchers.IO) {
        val file = video?.takeIf(File::exists) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(THUMBNAIL_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.w(TAG, "Không lấy được ảnh thu nhỏ của $file", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ARG_VIDEO_PATH = "video_path"
        private const val KEY_DOWNLOADED = "downloaded"
        private const val TAG = "SuccessViewModel"
        private const val THUMBNAIL_TIME_US = 1_000_000L
        private const val MAX_SUGGESTIONS = 10
    }
}
