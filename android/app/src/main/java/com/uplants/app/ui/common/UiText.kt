package com.uplants.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.uplants.app.R
import com.uplants.app.data.network.ApiErrorKind
import com.uplants.app.data.network.ApiException

/** Text for the UI that is either a string resource or text from the server. Keeps ViewModels free of Context. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> text
}

/** Message for a failed service call. 400/409 show the service's own explanation. */
fun Throwable.toUiText(): UiText {
    val api = this as? ApiException ?: return UiText.Res(R.string.error_server)
    return when (api.kind) {
        ApiErrorKind.OFFLINE -> UiText.Res(R.string.error_service_offline)
        ApiErrorKind.UNAUTHORIZED -> UiText.Res(R.string.error_login_again)
        ApiErrorKind.FORBIDDEN -> UiText.Res(R.string.error_forbidden)
        ApiErrorKind.NOT_FOUND -> UiText.Res(R.string.error_not_found)
        ApiErrorKind.UNAVAILABLE -> UiText.Res(R.string.error_unavailable)
        ApiErrorKind.BAD_REQUEST, ApiErrorKind.CONFLICT ->
            api.serverMessage?.let { UiText.Plain(it) } ?: UiText.Res(R.string.error_server)
        ApiErrorKind.UNKNOWN -> UiText.Res(R.string.error_server)
    }
}
