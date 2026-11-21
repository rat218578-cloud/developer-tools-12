package com.unixshells.devbrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthPolicyTest {

    @Test
    fun googleAccountsSignInIsDetected() {
        assertTrue(GoogleAuthPolicy.isGoogleSignInUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"))
        assertTrue(GoogleAuthPolicy.isGoogleSignInUrl("https://accounts.google.com/ServiceLogin"))
    }

    @Test
    fun hostIsCaseInsensitive() {
        assertTrue(GoogleAuthPolicy.isGoogleSignInUrl("https://Accounts.Google.com/signin"))
    }

    @Test
    fun plainHttpIsNotRedirected() {
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("http://accounts.google.com/signin"))
    }

    @Test
    fun ordinaryGoogleSearchIsNotRedirected() {
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("https://www.google.com/search?q=login"))
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("https://www.google.com/"))
    }

    @Test
    fun lookalikeHostsAreNotRedirected() {
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("https://accounts.google.com.evil.example/signin"))
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("https://evil.example/accounts.google.com"))
    }

    @Test
    fun invalidOrEmptyUrlsAreNotRedirected() {
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl(""))
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("not a url with spaces"))
        assertFalse(GoogleAuthPolicy.isGoogleSignInUrl("about:blank"))
    }
}
