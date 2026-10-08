package com.uplants.app.ui.auth

import androidx.annotation.StringRes
import com.uplants.app.R

/** Per-field validation errors for the sign-up form; null means the field is valid. */
data class SignUpFieldErrors(
    @StringRes val username: Int? = null,
    @StringRes val email: Int? = null,
    @StringRes val password: Int? = null,
    @StringRes val confirmPassword: Int? = null,
) {
    val hasErrors: Boolean
        get() = listOf(username, email, password, confirmPassword).any { it != null }
}

object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 8

    private val usernameRegex = Regex("^[A-Za-z0-9_.]{3,30}$")
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validateSignUp(username: String, email: String, password: String, confirmPassword: String) =
        SignUpFieldErrors(
            username = R.string.error_username_invalid.takeUnless { usernameRegex.matches(username.trim()) },
            email = R.string.error_email_invalid.takeUnless { emailRegex.matches(email.trim()) },
            password = R.string.error_password_short.takeUnless { password.length >= MIN_PASSWORD_LENGTH },
            confirmPassword = R.string.error_passwords_mismatch.takeUnless { confirmPassword == password },
        )
}
