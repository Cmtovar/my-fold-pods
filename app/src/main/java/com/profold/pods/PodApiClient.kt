package com.profold.pods

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ServiceInfo(
    val name: String,
    val status: String,
    val image: String,
    @SerializedName("web_port") val webPort: Int?,
    @SerializedName("web_path") val webPath: String = ""
)

data class DeviceInfo(
    val name: String,
    val ip: String,
    val os: String,
    val online: Boolean,
    @SerializedName("is_self") val isSelf: Boolean,
    val services: List<ServiceInfo>
)

data class DevicesResponse(val devices: List<DeviceInfo>)

data class HealthInfo(
    @SerializedName("cpu_load") val cpuLoad: String = "",
    @SerializedName("mem_total_mb") val memTotalMb: Int = 0,
    @SerializedName("mem_used_mb") val memUsedMb: Int = 0,
    @SerializedName("disk_total") val diskTotal: String = "",
    @SerializedName("disk_used") val diskUsed: String = "",
    @SerializedName("disk_avail") val diskAvail: String = "",
    val uptime: String = ""
)

class PodApiClient(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun getDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/devices").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            gson.fromJson(body, DevicesResponse::class.java).devices
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getHealth(): HealthInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/health").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            gson.fromJson(body, HealthInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
