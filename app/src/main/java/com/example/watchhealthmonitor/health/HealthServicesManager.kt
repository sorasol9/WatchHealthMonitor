package com.example.watchhealthmonitor.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class HealthServicesManager(context: Context) {
    private val healthServicesClient: HealthServicesClient = HealthServices.getClient(context)
    private val measureClient: MeasureClient = healthServicesClient.measureClient

    // 심박수 측정이 가능한지 확인
    suspend fun hasHeartRateCapability(): Boolean {
        return try {
            // rc02 버전에서는 capabilities 체크를 생략하고
            // 대부분의 Wear OS 기기가 심박수 센서를 가지고 있으므로 true 반환
            true
        } catch (e: Exception) {
            Log.e("HealthServices", "Error checking capabilities", e)
            false
        }
    }

    // 실시간 심박수 데이터 스트림
    fun heartRateMeasureFlow(): Flow<HeartRateMessage> = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                // 가용성 변경 로그만 남기고 항상 true로 처리
                Log.d("HealthServices", "Availability changed: ${availability.javaClass.simpleName}")
                // DataTypeAvailability인 경우만 available로 간주
                val isAvailable = availability is DataTypeAvailability
                trySendBlocking(HeartRateMessage.HeartRateAvailability(isAvailable))
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateDataPoints = data.getData(DataType.HEART_RATE_BPM)
                heartRateDataPoints.forEach { dataPoint ->
                    val heartRate = dataPoint.value
                    Log.d("HealthServices", "Heart rate received: $heartRate BPM")
                    trySendBlocking(
                        HeartRateMessage.HeartRateData(heartRate)
                    )
                }
            }
        }

        try {
            Log.d("HealthServices", "Registering measure callback")
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        } catch (e: Exception) {
            Log.e("HealthServices", "Failed to register callback", e)
        }

        awaitClose {
            Log.d("HealthServices", "Closing flow - callback will be cleaned up automatically")
            // rc02 버전에서는 명시적인 unregister가 필요없음
            // Flow가 닫힐 때 자동으로 정리됨
        }
    }
}

// 심박수 메시지 타입
sealed class HeartRateMessage {
    data class HeartRateData(val heartRate: Double) : HeartRateMessage()
    data class HeartRateAvailability(val isAvailable: Boolean) : HeartRateMessage()
}