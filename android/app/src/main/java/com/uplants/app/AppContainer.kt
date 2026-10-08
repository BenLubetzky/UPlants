package com.uplants.app

import android.content.Context
import com.uplants.app.data.auth.AuthApi
import com.uplants.app.data.auth.AuthRepository
import com.uplants.app.data.auth.FakeAuthRepository
import com.uplants.app.data.auth.NetworkAuthRepository
import com.uplants.app.data.auth.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Manual dependency injection: creates the app's singletons once, in [UPlantsApplication]. */
class AppContainer(context: Context) {

    private val sessionStore = SessionStore(context.applicationContext)

    private val retrofit: Retrofit by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val authRepository: AuthRepository =
        if (BuildConfig.USE_FAKE_BACKEND) {
            FakeAuthRepository(sessionStore)
        } else {
            NetworkAuthRepository(retrofit.create(AuthApi::class.java), sessionStore)
        }
}
