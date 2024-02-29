package com.app.myweatherapp

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


//class ApiClient {
//
//    companion object {
//        private val BASE_URL = "https://api.openweathermap.org/data/2.5/"
//        private var instance: Retrofit? = null
//
//        fun getInstance(): Retrofit? {
//            if (instance == null) {
//                instance = Retrofit.Builder()
//                    .baseUrl(BASE_URL)
//                    .addConverterFactory(GsonConverterFactory.create())
//                    .build()
//            }
//            return instance as Retrofit
//        }
//    }
//}



object ApiClient {

    private var retrofit: Retrofit? = null

    // Create a Retrofit instance with the provided base URL and token
    private fun getClient(baseUrl: String, token: String?): Retrofit {
        if (retrofit == null) {
            val httpClient = OkHttpClient.Builder()

            // Add authorization header with the token
            token?.let { authToken ->
                val authInterceptor = { chain: Interceptor.Chain ->
                    val request: Request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $authToken")
                        .build()
                    chain.proceed(request)
                }
                httpClient.addInterceptor(authInterceptor)
            }


            // Add logging interceptor for debugging purposes
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            httpClient.addInterceptor(loggingInterceptor)

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build()
        }
        return retrofit!!
    }

    // Create an API service instance with the provided base URL and token
    fun <T> createService(baseUrl: String, token: String?, serviceClass: Class<T>): T {
        return getClient(baseUrl, token).create(serviceClass)
    }
}
