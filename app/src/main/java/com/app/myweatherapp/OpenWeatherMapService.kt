package com.app.myweatherapp

import com.app.myweatherapp.model.weatherData.WeatherData
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


interface OpenWeatherMapService {
    @GET("weather")
    fun getCurrentWeatherData(
        @Query("q") location: String?,@Query("units") tempUnit: String?,
        @Query("appid") apiKey: String?
    ): Call<WeatherData>
}