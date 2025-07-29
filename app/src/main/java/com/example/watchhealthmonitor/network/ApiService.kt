package com.example.watchhealthmonitor.network

import com.example.watchhealthmonitor.data.ApiResponse
import com.example.watchhealthmonitor.data.HealthData
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/health/heartrate")  // 실제 서버 엔드포인트로 변경
    suspend fun sendHeartRate(@Body data: HealthData): ApiResponse
}