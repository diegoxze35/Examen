package com.mobile.location.tracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mobile.location.tracker.data.AppDatabase
import com.mobile.location.tracker.data.LocationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


data class MapSettingsState(
    val selectedInterval: Long = 10000L,
    val isServiceRunning: Boolean = false
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).locationDao()
    private val _settingFlow = MutableStateFlow(MapSettingsState())

    val settingFlow = _settingFlow.asStateFlow()
    val allLocations: Flow<List<LocationEntity>> = dao.getAllLocations()
    val latestLocation: Flow<LocationEntity?> = dao.getLatestLocation()

    fun updateState(interval: Long, isServiceRunning: Boolean) {
        _settingFlow.value = MapSettingsState(
            selectedInterval = interval,
            isServiceRunning = isServiceRunning
        )
    }

}
