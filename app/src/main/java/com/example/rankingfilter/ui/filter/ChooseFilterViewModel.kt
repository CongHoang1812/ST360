package com.example.rankingfilter.ui.filter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.rankingfilter.data.BuiltInFilterRepository
import com.example.rankingfilter.data.FilterDto
import com.example.rankingfilter.data.SelectedFilterStore
import com.example.rankingfilter.data.assetUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChooseFilterUiState(
    val mode: FilterMode = FilterMode.ALL,
    val tab: FilterTab = FilterTab.DISCOVER,
    val items: List<FilterCardItem> = emptyList(),
    val isLoading: Boolean = true,
)

class ChooseFilterViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    /** Lấy từ argument "mode" của nav_graph (SavedStateHandle tự nhận arguments của Fragment). */
    val mode: FilterMode = savedState.get<String>(ARG_MODE)
        ?.let { runCatching { FilterMode.valueOf(it) }.getOrNull() }
        ?: FilterMode.ALL

    private val selectedStore = SelectedFilterStore(application)

    private val builtIn = MutableStateFlow<List<FilterDto>?>(null)

    // Giai đoạn 4: thay bằng Flow từ Room (UserFilterDao.observeAll()).
    private val userFilters = MutableStateFlow<List<FilterCardItem>>(emptyList())

    /** Tab đang chọn, giữ qua xoay màn hình / hệ thống đóng app. */
    private val tab = savedState.getStateFlow(KEY_TAB, FilterTab.DISCOVER)

    val state: StateFlow<ChooseFilterUiState> =
        combine(builtIn, userFilters, tab) { all, mine, currentTab ->
            val items = when {
                mode == FilterMode.NEW_PACK ->
                    all.orEmpty().filter { it.pack == FilterDto.PACK_NEW }.map { it.toCard() }
                currentTab == FilterTab.DISCOVER -> all.orEmpty().map { it.toCard() }
                else -> mine
            }
            ChooseFilterUiState(
                mode = mode,
                tab = currentTab,
                items = items,
                isLoading = all == null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseFilterUiState(mode = mode))

    init {
        viewModelScope.launch { builtIn.value = BuiltInFilterRepository.getAll(getApplication()) }
    }

    fun selectTab(newTab: FilterTab) {
        savedState[KEY_TAB] = newTab
    }

    /** Lưu filter vừa chọn để Camera dùng (giai đoạn 5). */
    fun onFilterChosen(item: FilterCardItem) {
        selectedStore.save(item.id, item.isUserFilter)
    }

    private fun FilterDto.toCard() = FilterCardItem(
        id = id,
        title = title,
        coverUri = assetUri(avatar),
        isUserFilter = false,
    )

    companion object {
        const val ARG_MODE = "mode"
        private const val KEY_TAB = "tab"
    }
}
