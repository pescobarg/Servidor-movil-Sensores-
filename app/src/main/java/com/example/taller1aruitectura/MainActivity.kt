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
import com.example.taller1aruitectura.databinding.ActivityMainBinding
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var acelerometro: Sensor
    private lateinit var giroscopio: Sensor
    private lateinit var locationManager: LocationManager

    private var ultimoEnvioAcelerometro: Long = 0
    private var ultimoEnvioGiroscopio: Long = 0
    private var ultimoEnvioLocalizacion: Long = 0

    private var URL_SOCKETS = "http://192.168.5.112:5003"
    private var notificationSocket: Socket? = null


    private var socket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        iniciarSocket()
        iniciarNotificationSocket() // Conexión para notificaciones
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw Exception("Acelerómetro no disponible")
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: throw Exception("Giroscopio no disponible")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            iniciarGPS()
        }
    }

    private fun iniciarGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
        }
    }

    private fun iniciarSocket(){
        try {
            socket = IO.socket(URL_SOCKETS)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.i("SOCKET", "Conectado al servidor de sockets")
            }

            socket?.on("intermediario_fallido") { args ->
                val data = args[0] as JSONObject
                val nuevaURL = data.getString("url")
                cambiarBroker(nuevaURL)
            }

        } catch (e: Exception) {
            Log.e("SOCKET", "Error al conectar al servidor de sockets", e)
        }
    }

    private fun iniciarNotificationSocket(){
        try {
            notificationSocket = IO.socket("http://192.168.5.112:5005")
            notificationSocket?.connect()
            notificationSocket?.on("intermediario_fallido") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val nuevaURL = data.getString("url")
                    Log.i("NOTIF_SOCKET", "Notificación recibida: Nuevo broker -> $nuevaURL")
                    cambiarBroker(nuevaURL)
                }
            }
        } catch (e: Exception) {
            Log.e("NOTIF_SOCKET", "Error al conectar al servidor de notificaciones", e)
        }
    }

    private fun cambiarBroker(nuevaURL: String) {
        Log.i("SOCKET", "Cambiando broker a: $nuevaURL")
        URL_SOCKETS = nuevaURL
        socket?.disconnect()
        iniciarSocket()
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
        val tiempoActual = System.currentTimeMillis()

        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (tiempoActual - ultimoEnvioAcelerometro >= 5000) {
                    enviarDatosSocket("acelerometro", mapOf("X" to event.values[0], "Y" to event.values[1], "Z" to event.values[2], "timestamp" to tiempoActual))
                    ultimoEnvioAcelerometro = tiempoActual
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                if (tiempoActual - ultimoEnvioGiroscopio >= 5000) {
                    enviarDatosSocket("giroscopio", mapOf("X" to event.values[0], "Y" to event.values[1], "Z" to event.values[2], "timestamp" to tiempoActual))
                    ultimoEnvioGiroscopio = tiempoActual
                }
            }
        }
    }

    private fun enviarDatosSocket(tipo: String, datos: Map<String, Any>) {
        val data = JSONObject().apply {
            put("tipo", tipo)
            datos.forEach { put(it.key, it.value) }
        }

        socket?.emit("enviar_datos", data)
        Log.i("SOCKET", "Datos enviados: $data")
    }

    override fun onLocationChanged(location: Location) {
        val tiempoActual = System.currentTimeMillis()

        if (tiempoActual - ultimoEnvioLocalizacion >= 5000) {
            enviarDatosSocket("localizacion", mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "alt" to location.altitude,
                "vel" to location.speed,
                "timestamp" to tiempoActual
            ))
            ultimoEnvioLocalizacion = tiempoActual
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarGPS()
        }
    }
}
