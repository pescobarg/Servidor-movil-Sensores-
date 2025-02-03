package com.example.taller1aruitectura.Sensor

interface SensorData
data class AccelerometerData(val values: FloatArray) : SensorData
data class GyroscopeData(val values: FloatArray) : SensorData
data class LocationData(val latitude: Double, val longitude: Double) : SensorData
