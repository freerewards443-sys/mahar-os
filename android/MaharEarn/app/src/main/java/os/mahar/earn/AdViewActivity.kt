package os.mahar.earn

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

class AdViewActivity : Activity() {
    private var timer: CountDownTimer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }
        val mid = intent.getStringExtra("missionId") ?: run { finish(); return }
        val mission = Store.missions.find { it.id == mid } ?: run { finish(); return }

        val root = Ui.root(this)
        root.addView(Ui.header(this, "📺 ${mission.title}", "Ad chal raha hai... timer ke baad Claim dabao"))
        val status = Ui.label(this, "⏳ ${Store.AD_WATCH_SECONDS} sec baki...", 16f, true, Ui.GREEN)
        status.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), 0)
        root.addView(status)

        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = WebViewClient()
        web.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        web.loadUrl(mission.link)
        root.addView(web)

        val claimBtn = Ui.button(this, "Claim +${mission.tokens} Tokens") { }
        claimBtn.isEnabled = false; claimBtn.alpha = 0.4f
        claimBtn.setOnClickListener {
            Store.completeMission(me, mission)
            Ui.toast(this, "🎉 +${mission.tokens} Tokens mil gaye!")
            finish()
        }
        val wrap = LinearLayout(this); wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 10))
        wrap.addView(claimBtn)
        root.addView(wrap)
        setContentView(root)

        timer = object : CountDownTimer(Store.AD_WATCH_SECONDS * 1000L, 1000) {
            override fun onTick(ms: Long) { status.text = "⏳ ${(ms / 1000) + 1} sec baki..." }
            override fun onFinish() {
                status.text = "✅ Ad complete! Ab Claim dabao"
                claimBtn.isEnabled = true; claimBtn.alpha = 1f
            }
        }.start()
    }
    override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}
