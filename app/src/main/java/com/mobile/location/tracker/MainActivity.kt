package com.mobile.location.tracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mobile.location.tracker.data.LocationEntity
import com.mobile.location.tracker.service.LocationTrackerService
import com.mobile.location.tracker.ui.theme.LocationTrackerTheme
import com.mobile.location.tracker.viewmodel.LocationViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: LocationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContent {
            LocationTrackerTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: LocationViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (selectedTab == 0) {
                TrackerMapScreen(viewModel)
            } else {
                HistoryScreen(viewModel)
            }
        }
    }
}

@Composable
fun TrackerMapScreen(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val locations by viewModel.allLocations.collectAsState(initial = emptyList())

    val settings by viewModel.settingFlow.collectAsState()
    val (selectedInterval, isServiceRunning) = settings

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle results if needed
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            OSMView(locations)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(8.dp)
            ) {
                Text("Update Interval", style = MaterialTheme.typography.labelMedium)
                IntervalOption(
                    "10 sec",
                    10000L,
                    selectedInterval
                ) { viewModel.updateState(interval = it, isServiceRunning) }
                IntervalOption(
                    "60 sec",
                    60000L,
                    selectedInterval
                ) { viewModel.updateState(interval = it, isServiceRunning) }
                IntervalOption(
                    "5 min",
                    300000L,
                    selectedInterval
                ) { viewModel.updateState(interval = it, isServiceRunning) }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(context, LocationTrackerService::class.java).apply {
                            action =
                                if (isServiceRunning) LocationTrackerService.ACTION_STOP else LocationTrackerService.ACTION_START
                            putExtra(LocationTrackerService.EXTRA_INTERVAL, selectedInterval)
                        }
                        if (isServiceRunning) {
                            context.stopService(intent)
                        } else {
                            ContextCompat.startForegroundService(context, intent)
                        }
                        viewModel.updateState(
                            isServiceRunning = !isServiceRunning,
                            interval = selectedInterval
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color.Red else Color.Green
                    )
                ) {
                    Text(if (isServiceRunning) "Stop Tracker" else "Start Tracker")
                }
            }
        }
    }
}

@Composable
fun IntervalOption(label: String, value: Long, selected: Long, onSelect: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun OSMView(locations: List<LocationEntity>) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            if (locations.isNotEmpty()) {
                val sortedLocations = locations.sortedBy { it.timestamp }
                val geoPoints = sortedLocations.map { GeoPoint(it.latitude, it.longitude) }
                mapView.overlays.clear()
                val line = Polyline()
                line.setPoints(geoPoints)
                mapView.overlays.add(line)
                geoPoints.forEach { point ->
                    val marker = Marker(mapView)
                    marker.position = point
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Lat: ${point.latitude}, Lon: ${point.longitude}"
                    mapView.overlays.add(marker)
                }
                mapView.controller.setCenter(geoPoints.last())
                mapView.invalidate()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun HistoryScreen(viewModel: LocationViewModel) {
    val locations by viewModel.allLocations.collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Location History", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(locations) { location ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Lat: ${location.latitude}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Lng: ${location.longitude}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Precision: ${location.precision}m",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Time: ${dateFormat.format(Date(location.timestamp))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
