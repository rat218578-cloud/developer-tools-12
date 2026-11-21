package com.unixshells.devbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.util.Log

data class Tab(
    val id: Int,
    val webView: WebView,
    var title: String = "New Tab",
    var url: String = "about:blank",
    var favicon: Bitmap? = null
)

class TabManager(
    private val context: Context,
    private val onTabChanged: (Tab) -> Unit,
    private val onTabListChanged: () -> Unit,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    /** Chamado quando uma navegacao principal vai para o login Google (abrir fora da WebView). */
    private val onSignInRequested: (String) -> Unit = {},
    /** Chamado quando o estado das abas muda e deve ser salvo. */
    private val onSessionChanged: () -> Unit = {}
) {
    companion object {
        private const val TAG = "TabManager"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val tabs = mutableListOf<Tab>()
    private var activeTabIndex = -1
    private var nextId = 0
    var isDesktopMode = true
        private set

    val activeTab: Tab? get() = tabs.getOrNull(activeTabIndex)
    val tabCount: Int get() = tabs.size
    val allTabs: List<Tab> get() = tabs.toList()

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(url: String = "about:blank"): Tab {
        val webView = WebView(context)
        configureWebView(webView)

        val tab = Tab(id = nextId++, webView = webView, url = url)
        tabs.add(tab)
        switchToTab(tabs.size - 1)
        webView.loadUrl(url)
        onTabListChanged()
        return tab
    }

    /**
     * Recria as abas salvas, na mesma ordem e com os mesmos IDs, e reabre a aba que
     * estava selecionada. Tenta restaurar o historico de cada aba (WebView.restoreState);
     * se nao for possivel, carrega a URL salva. Retorna false se a sessao estiver vazia.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun restoreSession(session: SavedSession): Boolean {
        if (session.tabs.isEmpty()) return false

        tabs.clear()
        activeTabIndex = -1
        isDesktopMode = session.desktopMode
        nextId = maxOf(session.nextId, session.tabs.maxOf { it.id } + 1)

        for (saved in session.tabs) {
            val webView = WebView(context)
            configureWebView(webView)
            if (!restoreWebViewState(webView, saved.webViewState)) {
                webView.loadUrl(saved.url)
            }
            tabs.add(Tab(id = saved.id, webView = webView, title = saved.title, url = saved.url))
        }

        val selectedIndex = tabs.indexOfFirst { it.id == session.selectedTabId }
            .takeIf { it >= 0 } ?: 0
        switchToTab(selectedIndex)
        onTabListChanged()
        return true
    }

    /** Captura o estado atual de todas as abas. Deve ser chamado na thread principal. */
    fun snapshot(): SavedSession = SavedSession(
        selectedTabId = activeTab?.id ?: -1,
        nextId = nextId,
        desktopMode = isDesktopMode,
        tabs = tabs.map { tab ->
            SavedTab(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                webViewState = captureWebViewState(tab.webView)
            )
        }
    )

    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        val tab = tabs.removeAt(index)
        tab.webView.destroy()

        if (tabs.isEmpty()) {
            createTab()
        } else {
            val newIndex = when {
                index <= activeTabIndex && activeTabIndex > 0 -> activeTabIndex - 1
                index < activeTabIndex -> activeTabIndex
                else -> minOf(index, tabs.size - 1)
            }
            switchToTab(newIndex)
        }
        onTabListChanged()
    }

    fun closeCurrentTab() {
        if (activeTabIndex >= 0) closeTab(activeTabIndex)
    }

    fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        activeTabIndex = index
        onTabChanged(tabs[index])
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT

            userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else null
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                findTabByWebView(view)?.let { tab ->
                    tab.url = url
                    if (tab == activeTab) onPageStarted(url)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                findTabByWebView(view)?.let { tab ->
                    tab.url = url
                    if (tab == activeTab) onPageFinished(url)
                }
                onSessionChanged()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (request.isForMainFrame && GoogleAuthPolicy.isGoogleSignInUrl(url)) {
                    onSignInRequested(url)
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                findTabByWebView(view)?.let { tab ->
                    tab.title = title ?: tab.url
                    if (tab == activeTab) onTitleChanged(tab.title)
                    onTabListChanged()
                }
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                findTabByWebView(view)?.let { tab ->
                    tab.favicon = icon
                    onTabListChanged()
                }
            }
        }
    }

    private fun findTabByWebView(webView: WebView): Tab? {
        return tabs.find { it.webView === webView }
    }

    /** Serializa o historico do WebView em bytes. Retorna null se nao houver historico. */
    private fun captureWebViewState(webView: WebView): ByteArray? {
        val bundle = Bundle()
        if (webView.saveState(bundle) == null) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not capture WebView state", e)
            null
        } finally {
            parcel.recycle()
        }
    }

    /** Restaura o historico do WebView a partir dos bytes salvos. Retorna false se falhar. */
    private fun restoreWebViewState(webView: WebView, state: ByteArray?): Boolean {
        if (state == null) return false
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(state, 0, state.size)
            parcel.setDataPosition(0)
            val bundle = parcel.readBundle(context.classLoader) ?: return false
            webView.restoreState(bundle) != null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not restore WebView state; falling back to URL", e)
            false
        } finally {
            parcel.recycle()
        }
    }

    fun updateDesktopMode(enabled: Boolean) {
        isDesktopMode = enabled
        tabs.forEach { tab ->
            tab.webView.settings.userAgentString =
                if (enabled) DESKTOP_USER_AGENT else null
        }
        activeTab?.webView?.reload()
    }

    fun destroyAll() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
        activeTabIndex = -1
    }
}
