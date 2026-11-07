package os.mahar.browser

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Feeds load progress + page titles back to the activity, and opens the
 *  system file picker when a website asks for an upload (<input type=file>). */
class MaharWebChromeClient(private val activity: MainActivity) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        activity.onProgressChanged(view, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        activity.onTitleReceived(view, title)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        activity.openFileChooser(filePathCallback, fileChooserParams)
        return true
    }
}
