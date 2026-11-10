package com.uplants.app.data.network

import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <token>` to every request while someone is logged in. */
class AuthInterceptor(private val currentToken: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentToken()
        val request = chain.request()
        if (token == null || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
