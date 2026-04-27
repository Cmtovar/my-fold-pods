package com.profold.pods

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.profold.pods.ui.theme.MyPodsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var isLoading by remember { mutableStateOf(true) }

    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var openWebUrl by remember { mutableStateOf<String?>(null) }
    var showSetup by remember { mutableStateOf<DeviceInfo?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            devices = apiClient.getDevices()
            isLoading = false
            delay(10_000)
        }
    }

    BackHandler(enabled = openWebUrl != null || selectedDevice != null || showSetup != null || showAdvanced) {
        when {
            openWebUrl != null -> openWebUrl = null
            showAdvanced -> showAdvanced = false
            showSetup != null -> showSetup = null
            selectedDevice != null -> selectedDevice = null
        }
    }

    val title = when {
        openWebUrl != null -> selectedDevice?.name ?: "Service"
        showAdvanced -> "Advanced"
        showSetup != null -> "Set Up ${showSetup!!.name}"
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
                    ),
                    actions = {
                        if (!showAdvanced && showSetup == null && selectedDevice == null) {
                            IconButton(onClick = { showAdvanced = true }) {
                                Text(
                                    text = "...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
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
                showAdvanced -> {
                    AdvancedScreen(apiClient = apiClient, devices = devices)
                }
                showSetup != null -> {
                    SetupScreen(device = showSetup!!)
                }
                selectedDevice != null -> {
                    DeviceDetail(
                        device = selectedDevice!!,
                        apiClient = apiClient,
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
                        widthSizeClass = widthSizeClass,
                        onDeviceClick = { device ->
                            if (device.hasAgent) {
                                selectedDevice = device
                            } else if (device.online) {
                                showSetup = device
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedScreen(apiClient: PodApiClient, devices: List<DeviceInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<PodConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        config = apiClient.getConfig()
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentConfig = config ?: PodConfig()

    // Collect all known service names across all devices
    val allServices = devices.flatMap { it.services }.map { it.name }.distinct().sorted()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Service Web Paths
        item {
            Text(
                text = "Service Web Paths",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set the URL path appended when opening a service's web UI. For example, Pi-hole needs /admin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(allServices) { serviceName ->
            var path by remember(serviceName) {
                mutableStateOf(currentConfig.webPaths[serviceName] ?: "")
            }
            ConfigField(
                label = serviceName,
                value = path,
                placeholder = "e.g. /admin",
                onValueChange = { path = it },
                onSave = {
                    if (path.isNotEmpty()) {
                        currentConfig.webPaths[serviceName] = path
                    } else {
                        currentConfig.webPaths.remove(serviceName)
                    }
                    scope.launch {
                        val ok = apiClient.saveConfig(currentConfig)
                        config = currentConfig
                        Toast.makeText(
                            context,
                            if (ok) "Saved" else "Failed to save",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Service Labels
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Service Labels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set a friendly display name for services. Leave empty to use the container name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(allServices) { serviceName ->
            var label by remember(serviceName) {
                mutableStateOf(currentConfig.serviceLabels[serviceName] ?: "")
            }
            ConfigField(
                label = serviceName,
                value = label,
                placeholder = "Display name",
                onValueChange = { label = it },
                onSave = {
                    if (label.isNotEmpty()) {
                        currentConfig.serviceLabels[serviceName] = label
                    } else {
                        currentConfig.serviceLabels.remove(serviceName)
                    }
                    scope.launch {
                        val ok = apiClient.saveConfig(currentConfig)
                        config = currentConfig
                        Toast.makeText(
                            context,
                            if (ok) "Saved" else "Failed to save",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Add custom web path
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add Custom Entry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pre-configure a web path for a service you plan to add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            var newName by remember { mutableStateOf("") }
            var newPath by remember { mutableStateOf("") }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Container name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        label = { Text("Web path") },
                        placeholder = { Text("/dashboard") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                if (newPath.isNotEmpty()) {
                                    currentConfig.webPaths[newName] = newPath
                                }
                                scope.launch {
                                    val ok = apiClient.saveConfig(currentConfig)
                                    config = currentConfig
                                    Toast.makeText(
                                        context,
                                        if (ok) "Added $newName" else "Failed to save",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ok) {
                                        newName = ""
                                        newPath = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        // Info
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Config is stored on rpi4b at ~/services/pod-api/config.json. Changes take effect on the next poll (10s).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "API: http://$PI_HOST:$API_PORT",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ConfigField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = onSave) {
                Text("Save")
            }
        }
    }
}

@Composable
fun DeviceList(
    devices: List<DeviceInfo>,
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
    val isProbing = device.probeStatus == "probing"
    val statusColor = when {
        !device.online -> Color(0xFF9E9E9E)
        isProbing -> Color(0xFF42A5F5)
        device.hasAgent -> Color(0xFF4CAF50)
        else -> Color(0xFFFFA726)
    }
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
            Spacer(modifier = Modifier.height(4.dp))
            when {
                !device.online -> Text(
                    text = "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E)
                )
                isProbing -> Text(
                    text = "Checking...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF42A5F5)
                )
                device.hasAgent && device.services.isNotEmpty() -> Text(
                    text = "${device.services.size} service${if (device.services.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                device.hasAgent -> Text(
                    text = "Connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
                else -> Text(
                    text = "Tap to set up",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFA726)
                )
            }
        }
    }
}

@Composable
fun SetupScreen(device: DeviceInfo) {
    val context = LocalContext.current
    val installCommand = "curl -sL http://$PI_HOST:$API_PORT/install | bash"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Set up ${device.name}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Run this command on ${device.name} to install the agent. It auto-detects containers and starts reporting to My Pods.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        StepCard(
            step = "1",
            title = "Open Terminal on ${device.name}",
            description = when (device.os) {
                "macOS" -> "Spotlight (Cmd+Space) > type \"Terminal\""
                "linux" -> "Open your terminal emulator"
                else -> "Open a command prompt"
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        StepCard(step = "2", title = "Paste this command", description = null)

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = installCommand,
                modifier = Modifier.padding(16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFF4EC9B0)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("install command", installCommand))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy Command")
        }

        Spacer(modifier = Modifier.height(12.dp))

        StepCard(
            step = "3",
            title = "Done",
            description = "The agent will start automatically. ${device.name} will appear as connected in My Pods within 10 seconds."
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Or via SSH",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ssh user@${device.ip} \"$installCommand\"",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val sshCommand = "ssh user@${device.ip} \"$installCommand\""
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ssh command", sshCommand))
                        Toast.makeText(context, "SSH command copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy SSH Command")
                }
            }
        }
    }
}

@Composable
fun StepCard(step: String, title: String, description: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = step,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceDetail(
    device: DeviceInfo,
    apiClient: PodApiClient,
    widthSizeClass: WindowWidthSizeClass,
    onServiceClick: (ServiceInfo) -> Unit
) {
    var health by remember { mutableStateOf<HealthInfo?>(null) }

    LaunchedEffect(device.ip) {
        while (true) {
            health = apiClient.getHealth(device.ip)
            delay(10_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Device info ribbon
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

        // Health ribbon
        if (health != null) {
            Spacer(modifier = Modifier.height(8.dp))
            HealthBar(health!!)
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
    val displayName = service.label.ifEmpty {
        service.name.replaceFirstChar { it.uppercase() }
    }

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
                text = displayName,
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
