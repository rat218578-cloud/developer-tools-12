package com.unixshells.devbrowser

import java.net.URI
import java.net.URISyntaxException

/**
 * O Google recusa login OAuth em WebViews embutidas ("This browser or app may not
 * be secure"). Quando uma navegacao principal vai para a tela de login do Google,
 * o app a abre em Chrome Custom Tabs, que e o fluxo suportado.
 *
 * Esta classe NAO altera o user-agent nem tenta disfarcar a WebView: apenas
 * decide qual URL deve ser redirecionada.
 */
object GoogleAuthPolicy {
    private val SIGN_IN_HOSTS = setOf("accounts.google.com")

    fun isGoogleSignInUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host in SIGN_IN_HOSTS
    }
}
