package com.example.taller1aruitectura.Sensor

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.taller1aruitectura.Constantes.Companion.URL_HTTP

object RetrofitClient {

    val instance: SensorService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(URL_HTTP)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(SensorService::class.java)
    }
}
