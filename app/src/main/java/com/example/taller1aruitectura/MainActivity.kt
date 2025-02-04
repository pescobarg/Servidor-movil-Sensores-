package com.example.taller1aruitectura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller1aruitectura.Sensor.*
import com.example.taller1aruitectura.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var acelometro: Sensor
    private lateinit var giroscopio: Sensor
    private lateinit var locationManager: LocationManager
    private var odometro: Sensor? = null // Puede ser null si el dispositivo no lo tiene
    private var odometroDisponible = false

    private var alerts: Alerts = Alerts(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            acelometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw SensorNotAvailableException("Accelerometer is not available")
        } catch (e: SensorNotAvailableException) {
            Log.e("ERROR SENSOR", "Acelerómetro no disponible")
        }

        try {
            giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: throw SensorNotAvailableException("Gyroscope is not available")
        } catch (e: SensorNotAvailableException) {
            Log.e("ERROR SENSOR", "Giroscopio no disponible")
        }

        try {
            odometro = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            odometroDisponible = odometro != null
            if (!odometroDisponible) {
                throw SensorNotAvailableException("Odometer is not available")
            }
        } catch (e: SensorNotAvailableException) {
            Log.e("ODOMETRO", "El sensor de odómetro no está disponible en este dispositivo")
        }

        // Pedir permisos de ubicación si no están concedidos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            iniciarGPS()
        }
    }

    private fun iniciarGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i("GPS", "Iniciando actualizaciones de ubicación")
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)

            // Obtener la última ubicación conocida si existe
            val ultimaUbicacion: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (ultimaUbicacion != null) {
                Log.i("GPS", "Última ubicación conocida - Lat: ${ultimaUbicacion.latitude}, Lng: ${ultimaUbicacion.longitude}, Alt: ${ultimaUbicacion.altitude}, Vel: ${ultimaUbicacion.speed}")
            } else {
                Log.i("GPS", "No hay última ubicación conocida disponible")
            }
        } else {
            Log.e("GPS", "Permisos de ubicación no concedidos")
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, acelometro, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_NORMAL)
        if (odometroDisponible) {
            sensorManager.registerListener(this, odometro, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this, acelometro)
        sensorManager.unregisterListener(this, giroscopio)
        if (odometroDisponible) {
            sensorManager.unregisterListener(this, odometro)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                enviarDatos("ACELEROMETRO", mapOf("X" to event.values[0], "Y" to event.values[1], "Z" to event.values[2]))
            }
            Sensor.TYPE_GYROSCOPE -> {
                enviarDatos("GIROSCOPIO", mapOf("X" to event.values[0], "Y" to event.values[1], "Z" to event.values[2]))
            }
            Sensor.TYPE_STEP_COUNTER -> {
                enviarDatos("ODOMETRO", mapOf("pasos" to event.values[0]))
            }
        }
    }
    private fun enviarDatos(tipo: String, datos: Map<String, Any>) {
        val payload = SensorDataPayload(tipo, datos)

        RetrofitClient.instance.sendSensorData(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.i("HTTP", "Datos enviados correctamente")
                } else {
                    Log.e("HTTP", "Error al enviar datos")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("HTTP", "Fallo en la conexión: ${t.message}")
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        enviarDatos("LOCALIZACION", mapOf("lat" to location.latitude, "lng" to location.longitude, "alt" to location.altitude, "vel" to location.speed))
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarGPS()
        } else {
            Log.e("GPS", "Permiso de ubicación denegado")
        }
    }


}

class SensorNotAvailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

