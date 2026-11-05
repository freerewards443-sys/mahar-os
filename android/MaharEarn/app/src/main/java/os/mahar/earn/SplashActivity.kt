package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import os.mahar.earn.R

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.background = Ui.gradient(Ui.GREEN_DARK, Ui.GREEN, 0f)
        root.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0)

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo)
        logo.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150))
        logo.alpha = 0f; logo.scaleX = 0.6f; logo.scaleY = 0.6f
        root.addView(logo)
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650)
            .setInterpolator(android.view.animation.OvershootInterpolator()).start()

        val name = Ui.label(this, "Mahar Earn", 30f, true, android.graphics.Color.WHITE)
        name.gravity = Gravity.CENTER
        val nlp = LinearLayout.LayoutParams(-1, -2); nlp.topMargin = Ui.dp(this, 18); name.layoutParams = nlp
        root.addView(name)
        val tag = Ui.label(this, "Ads dekho • Tokens kamao • Cash withdraw karo", 14f, false, android.graphics.Color.parseColor("#CDE8D8"))
        tag.gravity = Gravity.CENTER
        root.addView(tag)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.isIndeterminate = true
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.GOLD_LIGHT)
        val blp = LinearLayout.LayoutParams(Ui.dp(this, 180), -2); blp.topMargin = Ui.dp(this, 34); bar.layoutParams = blp
        root.addView(bar)
        val loading = Ui.label(this, "Loading...", 12f, false, android.graphics.Color.parseColor("#9CC7AC"))
        loading.gravity = Gravity.CENTER
        root.addView(loading)
        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = when {
                Store.sessionId.isEmpty() -> LoginActivity::class.java
                Store.isAdmin() -> AdminActivity::class.java
                else -> MainActivity::class.java
            }
            startActivity(Intent(this, next))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1400)
    }
}
