package os.mahar.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Handles page navigation: ad-blocking, external schemes, title/url updates.
 */
class MaharWebViewClient(private val activity: MainActivity) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
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

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        activity.onPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        activity.onPageFinished(view, url)
    }
}
