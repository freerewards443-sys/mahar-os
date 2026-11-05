package os.mahar.earn

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView

class WithdrawActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val me = Store.me() ?: run { finish(); return }

        val (root, outer) = Ui.page(this, "withdraw")
        root.addView(Ui.header(this, "💸 Withdraw",
            "Cash Balance: Rs ${me.cash} • Minimum: Rs ${Store.MIN_WITHDRAW}"))

        val c = Ui.card(this)
        c.addView(Ui.label(this, "Payment method chuno", 15f, true))
        val rg = RadioGroup(this); rg.orientation = RadioGroup.HORIZONTAL
        val rbJazz = RadioButton(this); rbJazz.text = "JazzCash"; rbJazz.id = 1; rbJazz.isChecked = true
        val rbEasy = RadioButton(this); rbEasy.text = "Easypaisa"; rbEasy.id = 2
        rg.addView(rbJazz); rg.addView(rbEasy)
        c.addView(rg)
        val amtIn = Ui.input(this, "Amount (Rs) — kam se kam ${Store.MIN_WITHDRAW}", InputType.TYPE_CLASS_NUMBER)
        val numIn = Ui.input(this, "Account number (03xx-xxxxxxx)", InputType.TYPE_CLASS_PHONE)
        val titleIn = Ui.input(this, "Account title (naam)")
        c.addView(amtIn); c.addView(numIn); c.addView(titleIn)
        c.addView(Ui.button(this, "Withdraw Request Bhejo") {
            val amt = amtIn.text.toString().trim().toIntOrNull() ?: 0
            val num = numIn.text.toString().trim()
            val title = titleIn.text.toString().trim()
            if (num.length < 10) { Ui.toast(this, "Account number sahi likho"); return@button }
            if (title.length < 3) { Ui.toast(this, "Account title likho"); return@button }
            val method = if (rg.checkedRadioButtonId == 2) "Easypaisa" else "JazzCash"
            val err = Store.requestWithdraw(me, amt, method, num, title)
            if (err != null) { Ui.toast(this, err); return@button }
            Ui.toast(this, "✅ Request bhej di gayi! Admin check karke Paid karega")
            finish()
        })
        c.addView(Ui.label(this, "Request Admin Panel me jayegi. Paid hone par status 'Paid' ho jayega, Reject honay par paisay balance me wapas aa jayenge.", 12f, false, Ui.MUTED))
        root.addView(c)
        setContentView(outer)
    }
}
