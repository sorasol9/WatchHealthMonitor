package com.example.watchhealthmonitor.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import com.example.watchhealthmonitor.data.HealthData
import com.example.watchhealthmonitor.health.HealthServicesManager
import com.example.watchhealthmonitor.health.HeartRateMessage
import com.example.watchhealthmonitor.network.RetrofitClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var healthServicesManager: HealthServicesManager

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthServicesManager = HealthServicesManager(this)

        setContent {
            WearApp {
                // 체온 센서 권한 요청
                val bodySensorPermission = rememberPermissionState(
                    android.Manifest.permission.BODY_SENSORS
                )

                if (bodySensorPermission.status.isGranted) {
                    HeartRateScreen()
                } else {
                    PermissionScreen(
                        onRequestPermission = {
                            bodySensorPermission.launchPermissionRequest()
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun HeartRateScreen() {
        var heartRate by remember { mutableStateOf(0.0) }
        var isAvailable by remember { mutableStateOf(false) }
        var lastSentTime by remember { mutableStateOf(0L) }
        var sendStatus by remember { mutableStateOf("") }
        var isAutoSendEnabled by remember { mutableStateOf(true) }

        // 심박수 데이터 수집
        LaunchedEffect(Unit) {
            Log.d("MainActivity", "Starting heart rate collection")

            lifecycleScope.launch {
                // 심박수 측정 가능 여부 확인
                val hasCapability = healthServicesManager.hasHeartRateCapability()
                Log.d("MainActivity", "Heart rate capability: $hasCapability")

                if (hasCapability) {
                    // 심박수 데이터 스트림 수집
                    healthServicesManager.heartRateMeasureFlow()
                        .collect { message ->
                            when (message) {
                                is HeartRateMessage.HeartRateData -> {
                                    Log.d("MainActivity", "Heart rate received: ${message.heartRate}")
                                    heartRate = message.heartRate

                                    // 자동 전송이 켜져있고 10초가 지났으면 전송
                                    val currentTime = System.currentTimeMillis()
                                    if (isAutoSendEnabled && currentTime - lastSentTime > 10000) {
                                        sendToServer(heartRate) { status ->
                                            sendStatus = status
                                        }
                                        lastSentTime = currentTime
                                    }
                                }
                                is HeartRateMessage.HeartRateAvailability -> {
                                    Log.d("MainActivity", "Availability changed: ${message.isAvailable}")
                                    isAvailable = message.isAvailable
                                }
                            }
                        }
                } else {
                    sendStatus = "심박수 센서를 사용할 수 없습니다"
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(20.dp)
            ) {
                // 심박수 아이콘
                Text(
                    text = "❤️",
                    fontSize = 48.sp,
                    color = if (isAvailable) Color.Red else Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 심박수 값
                Text(
                    text = when {
                        !isAvailable -> "센서 준비 중..."
                        heartRate > 0 -> "${heartRate.toInt()} BPM"
                        else -> "측정 중..."
                    },
                    fontSize = 32.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 수동 전송 버튼
                CompactChip(
                    onClick = {
                        if (heartRate > 0) {
                            sendToServer(heartRate) { status ->
                                sendStatus = status
                            }
                        } else {
                            sendStatus = "심박수 데이터 없음"
                        }
                    },
                    label = {
                        Text("지금 전송", fontSize = 14.sp)
                    },
                    colors = ChipDefaults.primaryChipColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 자동 전송 토글
                ToggleChip(
                    checked = isAutoSendEnabled,
                    onCheckedChange = { isAutoSendEnabled = it },
                    label = {
                        Text(
                            text = if (isAutoSendEnabled) "자동 전송 ON" else "자동 전송 OFF",
                            fontSize = 12.sp
                        )
                    },
                    toggleControl = {
                        ToggleChipDefaults.switchIcon(checked = isAutoSendEnabled)
                    }
                )

                // 전송 상태 표시
                if (sendStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sendStatus,
                        fontSize = 12.sp,
                        color = when {
                            sendStatus.contains("성공") -> Color.Green
                            sendStatus.contains("실패") || sendStatus.contains("오류") -> Color.Red
                            else -> Color.Yellow
                        },
                        textAlign = TextAlign.Center
                    )
                }

                // 마지막 업데이트 시간
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "실시간 모니터링 중",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }

    @Composable
    fun PermissionScreen(onRequestPermission: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "❤️",
                    fontSize = 48.sp,
                    color = Color.Red
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "심박수 측정을 위해\n센서 권한이 필요합니다",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                CompactChip(
                    onClick = onRequestPermission,
                    label = {
                        Text("권한 허용", fontSize = 14.sp)
                    },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }

    private fun sendToServer(heartRate: Double, onResult: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "Sending heart rate to server: $heartRate")

                val healthData = HealthData(heartRate = heartRate)
                val response = RetrofitClient.apiService.sendHeartRate(healthData)

                if (response.success) {
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    onResult("✓ 전송 성공 ($time)")
                } else {
                    onResult("전송 실패: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Server error", e)
                onResult("네트워크 오류")
            }
        }
    }
}

@Composable
fun WearApp(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Color(0xFF4CAF50),
            primaryVariant = Color(0xFF388E3C),
            secondary = Color(0xFF2196F3),
            error = Color(0xFFE91E63),
            background = Color.Black,
            surface = Color(0xFF202020)
        )
    ) {
        Scaffold(
            timeText = { TimeText() }
        ) {
            content()
        }
    }
}