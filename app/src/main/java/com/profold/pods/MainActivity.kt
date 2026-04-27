package com.profold.pods

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.profold.pods.ui.theme.MyPodsTheme
import kotlinx.coroutines.delay

private const val PI_HOST = "100.97.40.66"
private const val API_PORT = 8090

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            MyPodsTheme {
                MyPodsApp(widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPodsApp(widthSizeClass: WindowWidthSizeClass) {
    val apiClient = remember { PodApiClient("http://$PI_HOST:$API_PORT") }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var health by remember { mutableStateOf<HealthInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Navigation: null = device list, DeviceInfo = device's services, Pair = open webview
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var openWebUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            devices = apiClient.getDevices()
            health = apiClient.getHealth()
            isLoading = false
            delay(10_000)
        }
    }

    BackHandler(enabled = openWebUrl != null || selectedDevice != null) {
        if (openWebUrl != null) {
            openWebUrl = null
        } else {
            selectedDevice = null
        }
    }

    val title = when {
        openWebUrl != null -> selectedDevice?.name ?: "Service"
        selectedDevice != null -> selectedDevice!!.name
        else -> "My Pods"
    }

    Scaffold(
        topBar = {
            if (openWebUrl == null) {
                TopAppBar(
                    title = { Text(title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                openWebUrl != null -> {
                    ServiceWebView(url = openWebUrl!!)
                }
                selectedDevice != null -> {
                    DeviceDetail(
                        device = selectedDevice!!,
                        widthSizeClass = widthSizeClass,
                        onServiceClick = { service ->
                            if (service.webPort != null) {
                                openWebUrl = "http://${selectedDevice!!.ip}:${service.webPort}${service.webPath}"
                            }
                        }
                    )
                }
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    DeviceList(
                        devices = devices,
                        health = health,
                        widthSizeClass = widthSizeClass,
                        onDeviceClick = { selectedDevice = it }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceList(
    devices: List<DeviceInfo>,
    health: HealthInfo?,
    widthSizeClass: WindowWidthSizeClass,
    onDeviceClick: (DeviceInfo) -> Unit
) {
    val columns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        else -> 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (health != null) {
            HealthBar(health)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No devices found.\nIs Tailscale connected?",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices) { device ->
                    DeviceTile(device = device, onClick = { onDeviceClick(device) })
                }
            }
        }
    }
}

@Composable
fun DeviceTile(device: DeviceInfo, onClick: () -> Unit) {
    val statusColor = if (device.online) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    val osLabel = when (device.os) {
        "linux" -> "Linux"
        "macOS" -> "macOS"
        "android" -> "Android"
        "windows" -> "Windows"
        else -> device.os
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (device.online)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.small,
                color = statusColor
            ) {}
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = osLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (device.services.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.services.size} service${if (device.services.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DeviceDetail(
    device: DeviceInfo,
    widthSizeClass: WindowWidthSizeClass,
    onServiceClick: (ServiceInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Device info
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HealthStat(label = "IP", value = device.ip)
                HealthStat(label = "OS", value = device.os)
                HealthStat(
                    label = "STATUS",
                    value = if (device.online) "Online" else "Offline"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (device.services.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (device.online) "No services running" else "Device offline",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val columns = when (widthSizeClass) {
                WindowWidthSizeClass.Compact -> 2
                else -> 3
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(device.services) { service ->
                    ServiceTile(
                        service = service,
                        onClick = { onServiceClick(service) }
                    )
                }
            }
        }
    }
}

@Composable
fun HealthBar(health: HealthInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HealthStat(label = "CPU", value = health.cpuLoad)
            HealthStat(label = "MEM", value = "${health.memUsedMb}/${health.memTotalMb}MB")
            HealthStat(label = "DISK", value = "${health.diskUsed}/${health.diskTotal}")
            HealthStat(label = "UP", value = health.uptime)
        }
    }
}

@Composable
fun HealthStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ServiceTile(service: ServiceInfo, onClick: () -> Unit) {
    val isRunning = service.status == "running"
    val statusColor = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
    val hasWebUI = service.webPort != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasWebUI) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.small,
                color = statusColor
            ) {}
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = service.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRunning) "Running" else service.status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
            if (hasWebUI) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ServiceWebView(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
