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
    var services by remember { mutableStateOf<List<ServiceInfo>>(emptyList()) }
    var health by remember { mutableStateOf<HealthInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var openService by remember { mutableStateOf<ServiceInfo?>(null) }

    // Poll for updates every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            services = apiClient.getServices()
            health = apiClient.getHealth()
            isLoading = false
            delay(10_000)
        }
    }

    // Back button returns to library
    BackHandler(enabled = openService != null) {
        openService = null
    }

    Scaffold(
        topBar = {
            if (openService == null) {
                TopAppBar(
                    title = { Text("My Pods") },
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
            AnimatedContent(targetState = openService, label = "nav") { service ->
                if (service != null && service.webPort != null) {
                    ServiceWebView(
                        url = "http://$PI_HOST:${service.webPort}",
                        widthSizeClass = widthSizeClass
                    )
                } else {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        ServiceLibrary(
                            services = services,
                            health = health,
                            widthSizeClass = widthSizeClass,
                            onServiceClick = { openService = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceLibrary(
    services: List<ServiceInfo>,
    health: HealthInfo?,
    widthSizeClass: WindowWidthSizeClass,
    onServiceClick: (ServiceInfo) -> Unit
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
        // System health bar
        if (health != null) {
            HealthBar(health)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (services.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No services found.\nIs the Pi reachable?",
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
                items(services) { service ->
                    ServiceTile(service = service, onClick = { onServiceClick(service) })
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
            HealthStat(
                label = "MEM",
                value = "${health.memUsedMb}/${health.memTotalMb}MB"
            )
            HealthStat(
                label = "DISK",
                value = "${health.diskUsed}/${health.diskTotal}"
            )
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
            // Status dot
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
fun ServiceWebView(url: String, widthSizeClass: WindowWidthSizeClass) {
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
