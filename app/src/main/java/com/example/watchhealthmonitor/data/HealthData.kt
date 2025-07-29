package com.example.watchhealthmonitor.data

import com.google.gson.annotations.SerializedName

// 서버로 전송할 건강 데이터
data class HealthData(
    @SerializedName("heart_rate")
    val heartRate: Double,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("device_id")
    val deviceId: String = android.os.Build.MODEL
)

// 서버 응답
data class ApiResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String
)