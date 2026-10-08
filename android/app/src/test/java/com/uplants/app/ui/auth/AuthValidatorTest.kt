package com.uplants.app.ui.auth

import com.uplants.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `valid sign-up has no errors`() {
        val errors = AuthValidator.validateSignUp("plant_lover", "me@example.com", "password123", "password123")
        assertFalse(errors.hasErrors)
    }

    @Test
    fun `invalid fields are each reported`() {
        val errors = AuthValidator.validateSignUp("ab", "not-an-email", "short", "different")
        assertEquals(R.string.error_username_invalid, errors.username)
        assertEquals(R.string.error_email_invalid, errors.email)
        assertEquals(R.string.error_password_short, errors.password)
        assertEquals(R.string.error_passwords_mismatch, errors.confirmPassword)
    }

    @Test
    fun `username allows letters digits underscore and dot only`() {
        assertEquals(null, AuthValidator.validateSignUp("john.doe_42", "a@b.co", "password123", "password123").username)
        assertEquals(
            R.string.error_username_invalid,
            AuthValidator.validateSignUp("john doe", "a@b.co", "password123", "password123").username,
        )
    }
}
