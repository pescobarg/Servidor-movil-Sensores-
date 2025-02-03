package com.example.taller1aruitectura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller1aruitectura.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private lateinit var acelometro: Sensor
    private lateinit var giroscopio: Sensor

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

        setupLocation()
        when{
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {

                alerts.indefiniteSnackbar(binding.root, "Requiere permiso localizacion")

            }

            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    101
                )
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        try {
            acelometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw SensorNotAvailableException("Accelerometer is not available")
        } catch (e: SensorNotAvailableException){
            Log.e("error sensor", "acelometro")
        }

        try {
            giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: throw SensorNotAvailableException("Gyroscope is not available")
        } catch (e: SensorNotAvailableException){
            Log.e("error sensor", "giroscopio")
        }

    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, acelometro, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this, acelometro)
        sensorManager.unregisterListener(this, giroscopio)
    }

    private fun startLocationUpdates() {

        if(ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )  == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        }
    }

    private fun setupLocation(){
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateDistanceMeters(5F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()
        locationCallback = object : LocationCallback() {

            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                p0.locations.forEach{ location ->
                    Log.i("localizaciones desde el movil", "location: $location")
                }

        }
    }
}

    override fun onSensorChanged(event: SensorEvent?) {

        if(event?.sensor?.type == Sensor.TYPE_ACCELEROMETER){
            Log.i("sensor acelerometro", "${event.values}")
        }

        if(event?.sensor?.type == Sensor.TYPE_GYROSCOPE){
            Log.i("sensor giroscopio", "${event.values}")
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

}

class SensorNotAvailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

