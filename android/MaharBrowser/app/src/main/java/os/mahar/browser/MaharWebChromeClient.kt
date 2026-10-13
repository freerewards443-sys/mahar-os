package os.mahar.browser

import android.webkit.WebChromeClient
import android.webkit.WebView

/** Feeds load progress + page titles back to the activity. */
class MaharWebChromeClient(private val activity: MainActivity) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        activity.onProgressChanged(view, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        activity.onTitleReceived(view, title)
    }
}
