package os.mahar.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Handles page navigation: local Mahar pages (mahar.local), ad-blocking,
 * external schemes, offline game, title/url updates.
 */
class MaharWebViewClient(private val activity: MainActivity) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        // Internal Mahar pages — served from app assets, no network needed
        if (uri.host == "mahar.local") {
            return when (uri.path) {
                "/home.html" -> WebResourceResponse(
                    "text/html", "utf-8",
                    ByteArrayInputStream(activity.buildInternalPage("mahar_home.html")))
                "/game.html" -> WebResourceResponse(
                    "text/html", "utf-8",
                    ByteArrayInputStream(activity.buildInternalPage("mahar_game.html")))
                "/mahar_logo.png" -> try {
                    WebResourceResponse("image/png", null, activity.assets.open("mahar_logo.png"))
                } catch (e: Exception) { null }
                else -> WebResourceResponse(
                    "text/plain", "utf-8", 404, "Not Found",
                    mutableMapOf(), ByteArrayInputStream(ByteArray(0)))
            }
        }
        val url = uri.toString()
        if (AdBlocker.shouldBlock(url)) {
            return WebResourceResponse(
                "text/plain", "utf-8", 204, "No Content",
                mutableMapOf(), ByteArrayInputStream(ByteArray(0))
            )
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://") && url != "about:blank") {
            // hand non-web schemes (tel:, mailto:, intent:) to the system
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) { }
            return true
        }
        return false
    }

    override fun onReceivedError(
        view: WebView, request: WebResourceRequest, error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Offline / page failed on the main frame -> Mahar Jump game page
        if (request.isForMainFrame && !request.url.toString().startsWith("https://mahar.local/")) {
            view.loadUrl(MainActivity.GAME)
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        activity.onPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        activity.onPageFinished(view, url)
    }
}
