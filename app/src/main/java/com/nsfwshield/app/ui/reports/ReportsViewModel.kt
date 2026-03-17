package com.nsfwshield.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nsfwshield.app.data.ActivityLogDao
import com.nsfwshield.app.data.ActivityLogEntry
import com.nsfwshield.app.data.CategoryCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class TimeRange { TODAY, WEEK, MONTH }

data class ReportsUiState(
    val selectedTab: Int = 0,
    val blockedCount: Int = 0,
    val overrideCount: Int = 0,
    val dnsBlockCount: Int = 0,
    val topCategories: List<CategoryCount> = emptyList(),
    val recentEvents: List<ActivityLogEntry> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val activityLogDao: ActivityLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val selectedTimeRange = MutableStateFlow(TimeRange.TODAY)

    init {
        observeRecentEvents()
        observeReactiveStats()
    }

    fun setTimeRange(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, isLoading = true) }
        val range = when (tabIndex) {
            0 -> TimeRange.TODAY
            1 -> TimeRange.WEEK
            2 -> TimeRange.MONTH
            else -> TimeRange.TODAY
        }
        selectedTimeRange.value = range
    }

    private fun observeReactiveStats() {
        viewModelScope.launch {
            selectedTimeRange.flatMapLatest { range ->
                val since = getSinceTimestamp(range)
                // Combine all stat flows for the given time range
                combine(
                    activityLogDao.getBlockedCountSinceFlow(since),
                    activityLogDao.getOverrideCountSinceFlow(since),
                    activityLogDao.getDnsBlockCountSinceFlow(since),
                    activityLogDao.getTopBlockedCategoriesFlow(since)
                ) { blocked, overrides, dns, categories ->
                    // Return a data class or simple map for updating state
                    DataUpdate(blocked, overrides, dns, categories)
                }
            }.collect { update ->
                _uiState.update {
                    it.copy(
                        blockedCount = update.blocked,
                        overrideCount = update.overrides,
                        dnsBlockCount = update.dns,
                        topCategories = update.categories,
                        isLoading = false
                    )
                }
            }
        }
    }

    private data class DataUpdate(
        val blocked: Int,
        val overrides: Int,
        val dns: Int,
        val categories: List<CategoryCount>
    )

    private fun observeRecentEvents() {
        viewModelScope.launch {
            activityLogDao.getRecentLogs(20).collect { events ->
                _uiState.update { it.copy(recentEvents = events) }
            }
        }
    }

    private fun getSinceTimestamp(range: TimeRange): Long {
        val cal = Calendar.getInstance()
        return when (range) {
            TimeRange.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            TimeRange.WEEK -> {
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            }
            TimeRange.MONTH -> {
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            }
        }
    }
}
