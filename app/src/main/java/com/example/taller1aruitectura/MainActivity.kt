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
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import com.example.taller1aruitectura.Constantes.Companion.URL_SOCKETS
import com.example.taller1aruitectura.Constantes.Companion.URL_WEB



class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var acelerometro: Sensor
    private lateinit var giroscopio: Sensor
    private lateinit var locationManager: LocationManager

    private var socket: Socket? = null
    private var Websocket: Socket? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        iniciarSocket()
        iniciarWebSocket()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw SensorNotAvailableException("Accelerometer no disponible")
        } catch (e: SensorNotAvailableException) {
            Log.e("ERROR SENSOR", "Acelerometro no disponible")
        }

        try {
            giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: throw SensorNotAvailableException("Giroscopio no disponible")
        } catch (e: SensorNotAvailableException) {
            Log.e("ERROR SENSOR", "Giroscopio no disponible")
        }


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

            // Obtener la ultima ubicacion conocida si existe
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

    private fun iniciarSocket(){
        try {
            socket = IO.socket(URL_SOCKETS)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.i("SOCKET", "Conectado al servidor de sockets")
            }

            socket?.on("localizacion_recibida") { args ->
                Log.i("SOCKET", "Respuesta del servidor: ${args[0]}")
            }

        } catch (e: Exception) {
            Log.e("SOCKET", "Error al conectar al servidor de sockets", e)
        }
    }

    private fun iniciarWebSocket(){
        try {
            Websocket = IO.socket(URL_WEB)
            Websocket?.connect()

            Websocket?.on(Socket.EVENT_CONNECT) {
                Log.i("SOCKET", "Conectado al servidor de sockets")
            }

            Websocket?.on("giroscopio_recibido") { args ->
                Log.i("SOCKET", "Respuesta del servidor: ${args[0]}")
            }

        } catch (e: Exception) {
            Log.e("SOCKET", "Error al conectar al servidor de sockets", e)
        }
    }


    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this, acelerometro)
        sensorManager.unregisterListener(this, giroscopio)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                enviarDatosHTTP("ACELEROMETRO", mapOf("X" to event.values[0], "Y" to event.values[1], "Z" to event.values[2]))
            }
            Sensor.TYPE_GYROSCOPE -> {
                val giroData = JSONObject()
                giroData.put("X", event.values[0])
                giroData.put("Y", event.values[1])
                giroData.put("Z", event.values[2])

                Websocket?.emit("enviar_giroscopio", giroData)
                Log.i("Giroscopio", "Datos enviados: X=${event.values[0]}, Y=${event.values[1]}, Z=${event.values[2]}")
            }
        }
    }

    private fun enviarDatosHTTP(tipo: String, datos: Map<String, Any>) {
        val payload = SensorDataPayload(tipo, datos)

        val tiempoInicio = System.currentTimeMillis()

        RetrofitClient.instance.sendSensorData(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                val tiempoFin = System.currentTimeMillis()
                val tiempoRespuesta = tiempoFin - tiempoInicio

                if (response.isSuccessful) {
                    Log.i("HTTP", "Datos enviados correctamente en ${tiempoRespuesta}ms")
                } else {
                    Log.e("HTTP", "Error al enviar datos en ${tiempoRespuesta}ms")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                val tiempoFin = System.currentTimeMillis()
                val tiempoRespuesta = tiempoFin - tiempoInicio
                Log.e("HTTP", "Fallo en la conexión: ${t.message} - Tiempo: ${tiempoRespuesta}ms")
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        val locationData = JSONObject()
        locationData.put("lat", location.latitude)
        locationData.put("lng", location.longitude)
        locationData.put("alt", location.altitude)
        locationData.put("vel", location.speed)

        socket?.emit("enviar_localizacion", locationData)
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

