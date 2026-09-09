package com.smartmirror.app.network

import com.smartmirror.app.network.dto.ClothingDto
import com.smartmirror.app.network.dto.ResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ClothingApiService {

    @GET("api/clothes/user/{userId}")
    suspend fun getUserClothes(@Path("userId") userId: Long): Response<ResultDto<List<ClothingDto>>>

    @GET("api/clothes/{id}")
    suspend fun getClothingById(@Path("id") id: Long): Response<ResultDto<ClothingDto>>

    @POST("api/clothes")
    suspend fun addClothing(@Body clothing: ClothingDto): Response<ResultDto<ClothingDto>>

    @PUT("api/clothes/{id}")
    suspend fun updateClothing(
        @Path("id") id: Long,
        @Body clothing: ClothingDto,
    ): Response<ResultDto<ClothingDto>>

    @DELETE("api/clothes/{id}")
    suspend fun deleteClothing(@Path("id") id: Long): Response<ResultDto<String>>
}
