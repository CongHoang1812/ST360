package com.example.rankingfilter.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rankingfilter.AppConfig
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.loadBuiltInFilters
import com.example.rankingfilter.data.trending.TrendingRepository
import com.example.rankingfilter.data.trending.TrendingVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    /** Filter có pack == "New Filter", dùng cho 3 ảnh trên banner. */
    val newPack: List<FilterDto> = emptyList(),
    val trending: List<TrendingVideo> = emptyList(),
    val isLoading: Boolean = true,
    /** true khi tải config.json lỗi; lưới đang hiện dữ liệu dự phòng. */
    val networkError: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrendingRepository(AppConfig.CONFIG_URL)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var filters: List<FilterDto>? = null
    private var loadJob: Job? = null

    init {
        load()
    }

    /** Tải (hoặc tải lại khi bấm Retry) danh sách filter và Trending. */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, networkError = false) }

            val all = filters ?: withContext(Dispatchers.IO) {
                loadBuiltInFilters(getApplication())
            }.also {
                filters = it
                Log.d(TAG, "loadBuiltInFilters: ${it.size} filter")
            }
            val newPack = all.filter { it.pack == FilterDto.PACK_NEW }
            val fallback = TrendingRepository.fallbackFrom(all)

            var networkError = false
            val trending = if (!repository.isConfigured) {
                fallback
            } else {
                try {
                    repository.fetchTrending().ifEmpty { fallback }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Không tải được config.json", e)
                    networkError = true
                    fallback
                }
            }

            _state.value = HomeUiState(
                newPack = newPack,
                trending = trending,
                isLoading = false,
                networkError = networkError,
            )
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
