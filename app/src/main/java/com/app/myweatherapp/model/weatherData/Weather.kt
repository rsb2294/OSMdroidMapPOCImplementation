package com.app.myweatherapp.model.weatherData

data class Weather(
    val description: String,
    val icon: String,
    val id: Int,
    val main: String
)