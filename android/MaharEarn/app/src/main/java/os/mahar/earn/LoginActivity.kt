package os.mahar.earn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Store.sessionId.isNotEmpty()) { goNext(); return }

        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        root.addView(Ui.header(this, "💰 Mahar Earn",
            "Ads dekho • Tokens kamao • Spin karo • Cash withdraw karo\nJazzCash / Easypaisa — Min withdraw Rs 100"))

        // Create account card
        val c1 = Ui.card(this)
        c1.addView(Ui.label(this, "🆕 Create New Account", 18f, true))
        c1.addView(Ui.label(this, "Naya account banao — tumhe ek App ID milegi. Usay save karna, doosray phone par wohi ID laga kar account khul jayega.", 13f, false, Ui.MUTED))
        val nameIn = Ui.input(this, "Apna naam")
        val phoneIn = Ui.input(this, "Mobile number (03xx-xxxxxxx)", InputType.TYPE_CLASS_PHONE)
        c1.addView(nameIn); c1.addView(phoneIn)
        c1.addView(Ui.button(this, "Create New Account") {
            val n = nameIn.text.toString().trim(); val ph = phoneIn.text.toString().trim()
            if (n.length < 3) { Ui.toast(this, "Naam sahi likho"); return@button }
            if (ph.length < 10) { Ui.toast(this, "Mobile number sahi likho"); return@button }
            val acc = Store.createAccount(n, ph)
            Ui.toast(this, "Account ban gaya! Tumhari App ID: ${acc.id} — isay save kar lo!")
            goNext()
        })
        root.addView(c1)

        // App ID login card
        val c2 = Ui.card(this)
        c2.addView(Ui.label(this, "🔑 App ID Login", 18f, true))
        c2.addView(Ui.label(this, "Purana account hai? Apni App ID lagao, account khul jayega.", 13f, false, Ui.MUTED))
        val idIn = Ui.input(this, "App ID (e.g. ME-XXXXX-XXXX)")
        idIn.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        c2.addView(idIn)
        c2.addView(Ui.button(this, "App ID Login", Ui.GOLD) {
            val id = idIn.text.toString().trim()
            if (id.isEmpty()) { Ui.toast(this, "App ID likho"); return@button }
            if (Store.login(id)) goNext() else Ui.toast(this, "Ye App ID nahi mili — sahi ID likho")
        })
        root.addView(c2)
        root.addView(Ui.label(this, "Admin ke liye alag Admin App ID bani hui hai.", 12f, false, Ui.MUTED).apply {
            setPadding(Ui.dp(this@LoginActivity, 16), Ui.dp(this@LoginActivity, 8), Ui.dp(this@LoginActivity, 16), 0)
        })
        scroll.addView(root)
        setContentView(scroll)
    }
    private fun goNext() {
        val intent = if (Store.isAdmin()) Intent(this, AdminActivity::class.java) else Intent(this, MainActivity::class.java)
        startActivity(intent); finish()
    }
}
