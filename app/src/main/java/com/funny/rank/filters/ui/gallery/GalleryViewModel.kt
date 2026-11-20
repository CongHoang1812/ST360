package com.funny.rank.filters.ui.gallery

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.funny.rank.filters.data.userfilter.UserFilterRepository
import com.funny.rank.filters.data.video.VideoLibrary
import com.funny.rank.filters.ui.filter.FilterCardItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class GalleryTab { VIDEOS, FILTERS }

data class GalleryUi(
    val tab: GalleryTab = GalleryTab.VIDEOS,
    /** Video đã lưu trong thư viện của app, mới nhất trước; null = đang đọc. */
    val videos: List<File>? = null,
    /** Filter tự tạo (Room); null = đang đọc. */
    val filters: List<FilterCardItem>? = null,
)

class GalleryViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val filterRepository = UserFilterRepository(application)
    private val videos = MutableStateFlow<List<File>?>(null)
    private val tab = savedState.getStateFlow(KEY_TAB, GalleryTab.VIDEOS)

    private val filters = filterRepository.observeAll().map { list ->
        list.map { item ->
            FilterCardItem(
                id = item.filter.id,
                title = item.filter.name,
                coverUri = item.coverPath?.let { File(it).toUri().toString() }.orEmpty(),
                isUserFilter = true,
            )
        }
    }

    val state: StateFlow<GalleryUi> = combine(tab, videos, filters) { t, v, f -> GalleryUi(t, v, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUi(tab = tab.value))

    fun selectTab(newTab: GalleryTab) {
        savedState[KEY_TAB] = newTab
    }

    /** Đọc lại danh sách video (mỗi lần quay lại màn: có thể vừa lưu hoặc vừa xóa video ở màn khác). */
    fun refreshVideos() {
        viewModelScope.launch {
            videos.value = withContext(Dispatchers.IO) { VideoLibrary.listSaved(getApplication()) }
        }
    }

    fun deleteVideo(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { VideoLibrary.deleteSaved(File(path)) }
            refreshVideos()
        }
    }

    /** Xóa filter tự tạo (Room + thư mục ảnh); lưới tự cập nhật qua Flow. [onDeleted] chạy trên luồng chính khi xóa xong. */
    fun deleteFilter(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            filterRepository.delete(id)
            onDeleted()
        }
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
