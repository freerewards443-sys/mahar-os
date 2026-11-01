package os.mahar.earn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class AdminActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (!Store.isAdmin()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        draw("withdrawals")
    }

    private fun draw(tab: String) {
        val scroll = ScrollView(this); scroll.setBackgroundColor(Ui.BG)
        val root = Ui.root(this)
        val pending = Store.withdrawals.filter { it.status == "Pending" }
        val pendingAmt = pending.sumOf { it.amount }
        val paidAmt = Store.withdrawals.filter { it.status == "Paid" }.sumOf { it.amount }
        root.addView(Ui.header(this, "🛠 Admin Panel",
            "Users kitne aaye: ${Store.accounts.size} • Pending: ${pending.size} (Rs $pendingAmt) • Total Paid: Rs $paidAmt"))

        // Tabs
        val tabs = Ui.card(this)
        val r1 = LinearLayout(this); r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(Ui.smallButton(this, "💸 Withdrawals (${pending.size})", if (tab == "withdrawals") Ui.GOLD else Ui.GREEN) { draw("withdrawals") })
        r1.addView(Ui.smallButton(this, "👥 Users (${Store.accounts.size})", if (tab == "users") Ui.GOLD else Ui.GREEN) { draw("users") })
        tabs.addView(r1)
        val r2 = LinearLayout(this); r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(Ui.smallButton(this, "📺 Missions / Ad Links", if (tab == "missions") Ui.GOLD else Ui.GREEN) { draw("missions") })
        r2.addView(Ui.smallButton(this, "🎧 Support (${Store.tickets.count { it.status == "Open" }})", if (tab == "support") Ui.GOLD else Ui.GREEN) { draw("support") })
        tabs.addView(r2)
        val r3 = LinearLayout(this); r3.orientation = LinearLayout.HORIZONTAL
        r3.addView(Ui.smallButton(this, "🚪 Logout", Ui.RED) {
            Store.logout()
            val i = Intent(this, LoginActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(i); finish()
        })
        tabs.addView(r3)
        root.addView(tabs)

        when (tab) {
            "withdrawals" -> {
                if (Store.withdrawals.isEmpty()) {
                    val c = Ui.card(this); c.addView(Ui.label(this, "Koi withdrawal request nahi hai.", 14f, false, Ui.MUTED)); root.addView(c)
                }
                for (w in Store.withdrawals) {
                    val c = Ui.card(this)
                    val color = when (w.status) { "Paid" -> Ui.GREEN; "Rejected" -> Ui.RED; else -> Color.parseColor("#B8860B") }
                    c.addView(Ui.label(this, "Rs ${w.amount} — ${w.method} — ${w.status.uppercase()}", 16f, true, color))
                    c.addView(Ui.label(this, "User: ${w.userName} (${w.accountId})\nNumber: ${w.number} • Title: ${w.title}\n${Store.fmtDate(w.date)}", 13f, false, Ui.MUTED))
                    if (w.status == "Pending") {
                        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
                        row.addView(Ui.smallButton(this, "✅ Paid Karo", Ui.GREEN) {
                            AlertDialog.Builder(this)
                                .setTitle("Paid confirm?")
                                .setMessage("${w.userName} ko Rs ${w.amount} ${w.method} (${w.number}) par bhej diye hain? User ko 'Paid' nazar aayega.")
                                .setPositiveButton("Haan, Paid") { _, _ -> Store.setWithdrawStatus(w, "Paid"); Ui.toast(this, "Paid mark ho gaya"); draw("withdrawals") }
                                .setNegativeButton("Cancel", null).show()
                        })
                        row.addView(Ui.smallButton(this, "❌ Reject", Ui.RED) {
                            AlertDialog.Builder(this)
                                .setTitle("Reject confirm?")
                                .setMessage("Request reject hogi aur Rs ${w.amount} user ke balance me wapas chalay jayenge.")
                                .setPositiveButton("Haan, Reject") { _, _ -> Store.setWithdrawStatus(w, "Rejected"); Ui.toast(this, "Reject ho gayi, amount refund ho gaya"); draw("withdrawals") }
                                .setNegativeButton("Cancel", null).show()
                        })
                        c.addView(row)
                    }
                    root.addView(c)
                }
            }
            "users" -> {
                if (Store.accounts.isEmpty()) {
                    val c = Ui.card(this); c.addView(Ui.label(this, "Abhi koi user nahi aaya.", 14f, false, Ui.MUTED)); root.addView(c)
                }
                for (a in Store.accounts) {
                    val c = Ui.card(this)
                    c.addView(Ui.label(this, "${a.name} — ${a.phone}", 15f, true))
                    c.addView(Ui.label(this, "App ID: ${a.id}\n🪙 ${a.tokens} Tokens • 💵 Rs ${a.cash} • 🔥 Streak ${a.streak}\nJoin: ${Store.fmtDate(a.createdAt)}", 13f, false, Ui.MUTED))
                    root.addView(c)
                }
            }
            "missions" -> {
                val info = Ui.card(this)
                info.addView(Ui.label(this, "Har mission ka title, AD LINK aur tokens yahan se kabhi bhi change/replace kar sakte ho. Save dabate hi sab users ke liye naya link lag jayega.", 13f, false, Ui.MUTED))
                root.addView(info)
                for (m in Store.missions) {
                    val c = Ui.card(this)
                    val titleIn = Ui.input(this, "Mission title"); titleIn.setText(m.title)
                    val linkIn = Ui.input(this, "Ad link (https://...)"); linkIn.setText(m.link)
                    linkIn.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    val tokIn = Ui.input(this, "Tokens reward", InputType.TYPE_CLASS_NUMBER); tokIn.setText(m.tokens.toString())
                    c.addView(titleIn); c.addView(linkIn); c.addView(tokIn)
                    c.addView(Ui.button(this, "💾 ${m.id.uppercase()} Save Karo", Ui.GOLD) {
                        m.title = titleIn.text.toString().trim().ifEmpty { m.title }
                        m.link = linkIn.text.toString().trim().ifEmpty { m.link }
                        m.tokens = tokIn.text.toString().trim().toIntOrNull() ?: m.tokens
                        Store.save()
                        Ui.toast(this, "✅ Mission save ho gaya — naya ad link live hai")
                        draw("missions")
                    })
                    root.addView(c)
                }
            }
            "support" -> {
                if (Store.tickets.isEmpty()) {
                    val c = Ui.card(this); c.addView(Ui.label(this, "Koi support message nahi hai.", 14f, false, Ui.MUTED)); root.addView(c)
                }
                for (t in Store.tickets) {
                    val c = Ui.card(this)
                    c.addView(Ui.label(this, "${t.userName} (${t.accountId}) — ${t.status}", 14f, true))
                    c.addView(Ui.label(this, t.message, 14f))
                    c.addView(Ui.label(this, Store.fmtDate(t.date), 12f, false, Ui.MUTED))
                    if (t.reply.isNotEmpty()) c.addView(Ui.label(this, "Mera jawab: " + t.reply, 13f, false, Ui.GREEN))
                    val replyIn = Ui.input(this, "Jawab likho...")
                    c.addView(replyIn)
                    c.addView(Ui.button(this, "Jawab Bhejo + Close Karo") {
                        val r = replyIn.text.toString().trim()
                        if (r.isEmpty()) { Ui.toast(this, "Jawab likho pehle"); return@button }
                        t.reply = r; t.status = "Closed"; Store.save()
                        Ui.toast(this, "Jawab bhej diya gaya")
                        draw("support")
                    })
                    root.addView(c)
                }
            }
        }
        scroll.addView(root)
        setContentView(scroll)
    }
}
