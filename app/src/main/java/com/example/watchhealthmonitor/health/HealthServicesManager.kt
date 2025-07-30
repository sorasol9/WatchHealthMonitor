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

    /**
     * 대부분의 Wear OS 3+ 기기에서는 심박수 센서가 항상 존재하므로,
     * 복잡한 capability 체크 없이 true를 반환
     */
    suspend fun hasHeartRateCapability(): Boolean = true

    /**
     * 실시간 심박수 데이터를 Flow로 받아오는 메서드
     * 앱이 백그라운드로 가면 callback이 중단될 수 있으므로 주의
     */
    fun heartRateMeasureFlow(): Flow<HeartRateMessage> = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                val isAvailable = availability is DataTypeAvailability
                Log.d("HealthServices", "Availability changed: $isAvailable")
                trySendBlocking(HeartRateMessage.HeartRateAvailability(isAvailable))
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRates = data.getData(DataType.HEART_RATE_BPM)
                heartRates.forEach { dataPoint ->
                    val hr = dataPoint.value
                    Log.d("HealthServices", "Heart rate received: $hr")
                    trySendBlocking(HeartRateMessage.HeartRateData(hr))
                }
            }
        }

        try {
            Log.d("HealthServices", "Registering heart rate callback")
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        } catch (e: Exception) {
            Log.e("HealthServices", "Error registering callback", e)
            close(e)
        }

        awaitClose {
            // rc02에서는 명시적인 해제 없이 자동으로 해제됨
            Log.d("HealthServices", "Flow closed")
        }
    }
}

sealed class HeartRateMessage {
    data class HeartRateData(val heartRate: Double) : HeartRateMessage()
    data class HeartRateAvailability(val isAvailable: Boolean) : HeartRateMessage()
}
