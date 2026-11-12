package com.uplants.app

import android.content.Context
import com.uplants.app.data.auth.AuthApi
import com.uplants.app.data.auth.AuthRepository
import com.uplants.app.data.auth.FakeAuthRepository
import com.uplants.app.data.auth.NetworkAuthRepository
import com.uplants.app.data.auth.SessionManager
import com.uplants.app.data.auth.SessionStore
import com.uplants.app.data.content.ContentApi
import com.uplants.app.data.content.ContentRepository
import com.uplants.app.data.device.DeviceServices
import com.uplants.app.data.favorites.FavoritesApi
import com.uplants.app.data.favorites.FavoritesRepository
import com.uplants.app.data.interactions.InteractionsApi
import com.uplants.app.data.interactions.InteractionsRepository
import com.uplants.app.data.messaging.MessagingApi
import com.uplants.app.data.messaging.MessagingRepository
import com.uplants.app.data.network.AuthInterceptor
import com.uplants.app.data.notifications.ActivityChecker
import com.uplants.app.data.notifications.DeepLink
import com.uplants.app.data.notifications.NotificationScheduler
import com.uplants.app.data.notifications.Notifier
import com.uplants.app.data.network.ServiceUrls
import com.uplants.app.data.network.TokenAuthenticator
import com.uplants.app.data.users.UsersApi
import com.uplants.app.data.users.UsersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Manual dependency injection: creates the app's singletons once, in [UPlantsApplication]. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessions = SessionManager(SessionStore(appContext), appScope)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // Headers only: logging bodies would buffer whole uploads (up to 50 MB) in memory.
    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        redactHeader(AuthInterceptor.AUTHORIZATION)
    }

    /** No auth: used for login, register and refreshing tokens. */
    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    /** Sends the access token and renews it when it expires. Shared by every service (and image loading). */
    val authedClient: OkHttpClient = plainClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS) // uploads and big media
        .addInterceptor(AuthInterceptor { sessions.current()?.token })
        .authenticator(
            TokenAuthenticator(
                currentSession = sessions::current,
                authApi = { authApi },
                saveSession = sessions::saveBlocking,
                endSession = sessions::clearBlocking,
            )
        )
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private inline fun <reified T> service(baseUrl: String, client: OkHttpClient = authedClient): T =
        retrofit(baseUrl, client).create(T::class.java)

    private val authApi: AuthApi by lazy { service(ServiceUrls.users, plainClient) }

    val authRepository: AuthRepository =
        if (BuildConfig.USE_FAKE_BACKEND) FakeAuthRepository(sessions) else NetworkAuthRepository(authApi, sessions)

    val contentRepository by lazy { ContentRepository(service<ContentApi>(ServiceUrls.content), appContext.contentResolver) }
    val interactionsRepository by lazy { InteractionsRepository(service<InteractionsApi>(ServiceUrls.interactions)) }
    val favoritesRepository by lazy { FavoritesRepository(service<FavoritesApi>(ServiceUrls.favorites)) }
    val messagingRepository by lazy { MessagingRepository(service<MessagingApi>(ServiceUrls.messaging)) }
    val usersRepository by lazy { UsersRepository(service<UsersApi>(ServiceUrls.users)) }

    val device by lazy { DeviceServices(appContext) }

    // Notifications (requirement 21): see data/notifications.
    val activityChecker by lazy {
        ActivityChecker(appContext, sessions, contentRepository, usersRepository, messagingRepository)
    }
    val notifier by lazy { Notifier(appContext) }
    val notificationScheduler by lazy { NotificationScheduler(appContext) }

    /** Set when the app is opened from a notification; the main screen navigates there and clears it. */
    val pendingDeepLink = MutableStateFlow<DeepLink?>(null)
}
