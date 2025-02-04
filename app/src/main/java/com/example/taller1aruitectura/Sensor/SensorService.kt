package com.example.taller1aruitectura.Sensor

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SensorService {
    @POST("/sensor-data")
    fun sendSensorData(@Body data: SensorDataPayload): Call<Void>
}
