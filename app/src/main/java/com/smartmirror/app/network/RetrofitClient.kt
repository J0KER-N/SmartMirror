package com.smartmirror.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * 后端服务器地址。
     * - 模拟器：用 http://10.0.2.2:8080/（10.0.2.2 是模拟器访问宿主机的地址）
     * - 真机：改成你电脑的局域网 IP，如 http://192.168.1.5:8080/
     *   （手机和电脑必须在同一 Wi-Fi 下）
     */
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val clothingApi: ClothingApiService by lazy {
        retrofit.create(ClothingApiService::class.java)
    }
}
