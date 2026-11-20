package com.funny.rank.filters.ui.sound

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.funny.rank.filters.R
import com.funny.rank.filters.data.music.MusicRepository
import com.funny.rank.filters.data.music.MusicTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Một dòng trong danh sách nhạc. */
data class MusicRow(
    val track: MusicTrack,
    /** null = chưa biết (đang đọc hoặc không đọc được). */
    val durationMs: Long?,
    val selected: Boolean,
    /** Đang nghe thử bài này (kể cả đang tải). */
    val playing: Boolean,
    /** Thời gian đã phát của bài đang nạp vào trình phát (đang phát hoặc tạm dừng); null = hiện thời lượng. */
    val positionMs: Long? = null,
)

data class AddSoundUi(
    val rows: List<MusicRow> = emptyList(),
    val selectedId: Int? = null,
    /** Đang tải bài đã chọn về máy sau khi bấm tick. */
    val isDownloading: Boolean = false,
)

sealed interface AddSoundEvent {
    /** Đã tải xong bài [id]: trả kết quả về Camera. */
    data class Confirmed(val id: Int) : AddSoundEvent
    data class Message(val textRes: Int) : AddSoundEvent
}

class AddSoundViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val tracks = MusicRepository.tracks

    /** Bài đang chọn: lấy từ argument "selected_music_id" (-1 = chưa chọn), giữ qua xoay màn hình. */
    private val selectedId = MutableStateFlow(
        savedState.get<Int>(KEY_SELECTED) ?: savedState.get<Int>(ARG_SELECTED)?.takeIf { it > 0 }
    )
    private val playingId = MutableStateFlow<Int?>(null)
    /** Bài đang nạp vào trình phát và vị trí đã phát (ms). */
    private var progress: Pair<Int, Long>? = null
    private val durations = MutableStateFlow(
        tracks.mapNotNull { t -> MusicRepository.knownDuration(t.id)?.let { t.id to it } }.toMap()
    )
    private val downloading = MutableStateFlow(false)
    private val probing = mutableSetOf<Int>()

    private val _state = MutableStateFlow(AddSoundUi())
    val state: StateFlow<AddSoundUi> = _state.asStateFlow()

    private val _events = Channel<AddSoundEvent>(Channel.BUFFERED)
    val events: Flow<AddSoundEvent> = _events.receiveAsFlow()

    init {
        publish()
        // Thời lượng đã lưu trong Room: nạp rồi hiện luôn, không chờ mạng.
        viewModelScope.launch {
            MusicRepository.ensureLoaded(getApplication())
            val saved = tracks.mapNotNull { t -> MusicRepository.knownDuration(t.id)?.let { t.id to it } }.toMap()
            if (saved.isNotEmpty()) {
                durations.update { saved + it }
                publish()
            }
        }
    }

    fun select(id: Int) {
        if (selectedId.value == id) return
        selectedId.value = id
        savedState[KEY_SELECTED] = id
        publish()
    }

    /** Fragment báo bài nào đang phát (null = không phát gì). */
    fun setPlaying(id: Int?) {
        if (playingId.value == id) return
        playingId.value = id
        publish()
    }

    /** Fragment báo vị trí phát của bài [id] (null = không có bài nào trong trình phát). Đổi theo giây mới vẽ lại. */
    fun setProgress(id: Int?, positionMs: Long) {
        val next = id?.let { it to positionMs / 1000 * 1000 }
        if (progress == next) return
        progress = next
        publish()
    }

    /** Dòng [id] vừa hiện trên màn hình mà chưa có thời lượng: đọc một mình bài đó. */
    fun requestDuration(id: Int) {
        if (durations.value.containsKey(id) || !probing.add(id)) return
        val track = MusicRepository.track(id) ?: return
        viewModelScope.launch {
            MusicRepository.duration(getApplication(), track)?.let { ms -> rememberDuration(id, ms) }
        }
    }

    fun rememberDuration(id: Int, durationMs: Long) {
        if (durationMs <= 0 || durations.value[id] == durationMs) return
        MusicRepository.rememberDuration(id, durationMs)
        durations.update { it + (id to durationMs) }
        publish()
    }

    /** Bấm tick: tải bài đã chọn về máy (để phát lúc quay và trộn vào video không cần mạng) rồi trả kết quả. */
    fun confirm() {
        val id = selectedId.value ?: return
        val track = MusicRepository.track(id) ?: return
        if (downloading.value) return
        downloading.value = true
        publish()
        viewModelScope.launch {
            try {
                MusicRepository.localFile(getApplication(), track)
                _events.send(AddSoundEvent.Confirmed(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Không tải được ${track.url}", e)
                _events.send(AddSoundEvent.Message(R.string.add_sound_download_error))
            } finally {
                downloading.value = false
                publish()
            }
        }
    }

    private fun publish() {
        val selected = selectedId.value
        val playing = playingId.value
        val known = durations.value
        val current = progress
        _state.value = AddSoundUi(
            rows = tracks.map { t ->
                MusicRow(
                    t, known[t.id], selected = t.id == selected, playing = t.id == playing,
                    positionMs = current?.takeIf { it.first == t.id }?.second,
                )
            },
            selectedId = selected,
            isDownloading = downloading.value,
        )
    }

    companion object {
        /** Argument của nav_graph: bài đang dùng ở Camera, -1 = chưa có. */
        const val ARG_SELECTED = "selected_music_id"
        private const val KEY_SELECTED = "selected"
        private const val TAG = "AddSoundViewModel"
    }
}
