package os.mahar.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor

/**
 * IP Changer: routes the browser's WebView traffic through a user-provided
 * proxy. The user labels each proxy with a country name; activating it
 * makes browsing go out via that proxy's IP. Auto-rotate switches between
 * the saved proxies on a timer.
 *
 * Honest design: there is no magic free multi-country IP. The user must
 * add their own proxy (free or paid). With no proxy configured the
 * browser uses a direct connection and says so.
 */
data class ProxyServer(val country: String, val host: String, val port: Int) {
    fun label(): String = "$country ($host:$port)"
}

object ProxyManager {
    private const val PREF = "proxy_prefs"
    var enabled = false
    var active: ProxyServer? = null
        private set

    private var handler: Handler? = null
    private var rotateTask: Runnable? = null

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getProxies(ctx: Context): MutableList<ProxyServer> {
        val out = mutableListOf<ProxyServer>()
        try {
            val arr = JSONArray(prefs(ctx).getString("proxies", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += ProxyServer(
                    o.optString("country", "Unknown"),
                    o.getString("host"),
                    o.getInt("port")
                )
            }
        } catch (_: Exception) { }
        return out
    }

    fun saveProxies(ctx: Context, list: List<ProxyServer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("country", it.country)
                put("host", it.host)
                put("port", it.port)
            })
        }
        prefs(ctx).edit().putString("proxies", arr.toString()).apply()
    }

    fun getRotateMinutes(ctx: Context): Int =
        prefs(ctx).getInt("rotate_min", 0)

    fun setRotateMinutes(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putInt("rotate_min", minutes).apply()
        if (enabled) startRotate(ctx, minutes)
    }

    /** Push the proxy (or direct connection) into every WebView. */
    fun applyProxy(proxy: ProxyServer?) {
        active = proxy
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val config = if (proxy != null) {
            ProxyConfig.Builder()
                .addProxyRule("${proxy.host}:${proxy.port}")
                .addBypassRule("localhost")
                .addBypassRule("127.0.0.1")
                .build()
        } else {
            ProxyConfig.Builder().addDirect().build()
        }
        try {
            ProxyController.getInstance().setProxyOverride(
                config,
                Executor { it.run() },
                Runnable { }
            )
        } catch (_: Exception) { }
    }

    fun setEnabled(ctx: Context, on: Boolean): Boolean {
        if (on && getProxies(ctx).isEmpty()) return false
        enabled = on
        prefs(ctx).edit().putBoolean("enabled", on).apply()
        if (on) {
            if (active == null) {
                val first = getProxies(ctx).firstOrNull()
                applyProxy(first)
            }
            startRotate(ctx, getRotateMinutes(ctx))
        } else {
            stopRotate()
            applyProxy(null)
        }
        return true
    }

    fun loadState(ctx: Context) {
        enabled = prefs(ctx).getBoolean("enabled", false)
        if (enabled) {
            if (getProxies(ctx).isEmpty()) {
                enabled = false
            } else {
                applyProxy(getProxies(ctx).firstOrNull())
                startRotate(ctx, getRotateMinutes(ctx))
            }
        }
    }

    fun startRotate(ctx: Context, intervalMin: Int) {
        stopRotate()
        if (!enabled || intervalMin <= 0) return
        handler = Handler(Looper.getMainLooper())
        rotateTask = object : Runnable {
            override fun run() {
                val list = getProxies(ctx)
                if (enabled && list.size > 1) {
                    val idx = list.indexOf(active)
                    applyProxy(list[(idx + 1 + list.size) % list.size])
                }
                handler?.postDelayed(this, intervalMin * 60_000L)
            }
        }
        handler?.postDelayed(rotateTask!!, intervalMin * 60_000L)
    }

    fun stopRotate() {
        rotateTask?.let { handler?.removeCallbacks(it) }
        rotateTask = null
        handler = null
    }

    fun statusText(): String =
        if (enabled && active != null) "Active: ${active!!.label()}"
        else "Off — direct connection"
}
