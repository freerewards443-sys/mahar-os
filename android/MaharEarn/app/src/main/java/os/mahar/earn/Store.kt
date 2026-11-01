package os.mahar.earn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class Account(
    var id: String, var name: String, var phone: String,
    var tokens: Int = 0, var cash: Int = 0,
    var streak: Int = 0, var lastStreakDate: String = "",
    var missionsDate: String = "", var missionsDone: MutableSet<String> = mutableSetOf(),
    var createdAt: Long = System.currentTimeMillis()
)
data class Mission(var id: String, var title: String, var link: String, var tokens: Int)
data class Withdrawal(
    var wid: String, var accountId: String, var userName: String,
    var amount: Int, var method: String, var number: String, var title: String,
    var status: String = "Pending", var date: Long = System.currentTimeMillis()
)
data class Ticket(
    var tid: String, var accountId: String, var userName: String,
    var message: String, var reply: String = "", var status: String = "Open",
    var date: Long = System.currentTimeMillis()
)

object Store {
    const val ADMIN_ID = "MAHAR-ADMIN"
    const val SPIN_COST = 5
    const val MIN_WITHDRAW = 100
    const val AD_WATCH_SECONDS = 20

    private const val PREFS = "mahar_earn_db"
    private var ctx: Context? = null

    var accounts: MutableList<Account> = mutableListOf()
    var missions: MutableList<Mission> = mutableListOf()
    var withdrawals: MutableList<Withdrawal> = mutableListOf()
    var tickets: MutableList<Ticket> = mutableListOf()
    var sessionId: String = ""   // account id, or ADMIN_ID
        private set

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        load()
    }
    private fun prefs() = ctx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun yesterday(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86400000L))
    fun fmtDate(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(ts))

    private fun load() {
        val p = prefs()
        accounts = mutableListOf()
        val arr = JSONArray(p.getString("accounts", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val done = mutableSetOf<String>()
            val d = o.optJSONArray("missionsDone") ?: JSONArray()
            for (j in 0 until d.length()) done.add(d.getString(j))
            accounts.add(Account(
                o.getString("id"), o.getString("name"), o.getString("phone"),
                o.optInt("tokens"), o.optInt("cash"), o.optInt("streak"),
                o.optString("lastStreakDate"), o.optString("missionsDate"), done,
                o.optLong("createdAt", System.currentTimeMillis())
            ))
        }
        missions = mutableListOf()
        val ma = JSONArray(p.getString("missions", "null") ?: "null")
        if (ma.length() == 0) {
            missions = defaultMissions()
        } else {
            for (i in 0 until ma.length()) {
                val o = ma.getJSONObject(i)
                missions.add(Mission(o.getString("id"), o.getString("title"), o.getString("link"), o.optInt("tokens", 2)))
            }
        }
        withdrawals = mutableListOf()
        val wa = JSONArray(p.getString("withdrawals", "[]"))
        for (i in 0 until wa.length()) {
            val o = wa.getJSONObject(i)
            withdrawals.add(Withdrawal(o.getString("wid"), o.getString("accountId"), o.getString("userName"),
                o.getInt("amount"), o.getString("method"), o.getString("number"), o.getString("title"),
                o.optString("status", "Pending"), o.optLong("date", System.currentTimeMillis())))
        }
        tickets = mutableListOf()
        val ta = JSONArray(p.getString("tickets", "[]"))
        for (i in 0 until ta.length()) {
            val o = ta.getJSONObject(i)
            tickets.add(Ticket(o.getString("tid"), o.getString("accountId"), o.getString("userName"),
                o.getString("message"), o.optString("reply"), o.optString("status", "Open"),
                o.optLong("date", System.currentTimeMillis())))
        }
        sessionId = p.getString("session", "") ?: ""
    }

    fun save() {
        val p = prefs().edit()
        val arr = JSONArray()
        for (a in accounts) {
            val d = JSONArray(); for (m in a.missionsDone) d.put(m)
            arr.put(JSONObject()
                .put("id", a.id).put("name", a.name).put("phone", a.phone)
                .put("tokens", a.tokens).put("cash", a.cash).put("streak", a.streak)
                .put("lastStreakDate", a.lastStreakDate).put("missionsDate", a.missionsDate)
                .put("missionsDone", d).put("createdAt", a.createdAt))
        }
        p.putString("accounts", arr.toString())
        val ma = JSONArray()
        for (m in missions) ma.put(JSONObject().put("id", m.id).put("title", m.title).put("link", m.link).put("tokens", m.tokens))
        p.putString("missions", ma.toString())
        val wa = JSONArray()
        for (w in withdrawals) wa.put(JSONObject().put("wid", w.wid).put("accountId", w.accountId).put("userName", w.userName)
            .put("amount", w.amount).put("method", w.method).put("number", w.number).put("title", w.title)
            .put("status", w.status).put("date", w.date))
        p.putString("withdrawals", wa.toString())
        val ta = JSONArray()
        for (t in tickets) ta.put(JSONObject().put("tid", t.tid).put("accountId", t.accountId).put("userName", t.userName)
            .put("message", t.message).put("reply", t.reply).put("status", t.status).put("date", t.date))
        p.putString("tickets", ta.toString())
        p.putString("session", sessionId)
        p.apply()
    }

    private fun defaultMissions(): MutableList<Mission> = mutableListOf(
        Mission("m1", "Mission 1 — Watch Ad", "https://dealspakistan.blogspot.com/", 2),
        Mission("m2", "Mission 2 — Watch Ad", "https://dealspakistan.blogspot.com/", 2),
        Mission("m3", "Mission 3 — Watch Ad", "https://dealspakistan.blogspot.com/", 2),
        Mission("m4", "Mission 4 — Watch Ad", "https://dealspakistan.blogspot.com/", 2),
        Mission("m5", "Mission 5 — Watch Ad", "https://dealspakistan.blogspot.com/", 2)
    )

    private fun genId(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        fun part(n: Int) = (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        var id: String
        do { id = "ME-" + part(5) + "-" + part(4) } while (accounts.any { it.id == id })
        return id
    }

    fun createAccount(name: String, phone: String): Account {
        val a = Account(genId(), name, phone)
        accounts.add(a)
        sessionId = a.id
        save()
        return a
    }
    fun login(id: String): Boolean {
        val clean = id.trim().uppercase(Locale.US)
        if (clean == ADMIN_ID) { sessionId = ADMIN_ID; save(); return true }
        val a = accounts.find { it.id == clean } ?: return false
        sessionId = a.id; save(); return true
    }
    fun logout() { sessionId = ""; save() }
    fun isAdmin(): Boolean = sessionId == ADMIN_ID
    fun me(): Account? = accounts.find { it.id == sessionId }

    /** Returns tokens earned, or -1 if already claimed today. Handles streak reset. */
    fun claimStreak(a: Account): Int {
        val t = today()
        if (a.lastStreakDate == t) return -1
        a.streak = if (a.lastStreakDate == yesterday()) {
            if (a.streak >= 7) 1 else a.streak + 1
        } else 1
        a.lastStreakDate = t
        a.tokens += a.streak
        save()
        return a.streak
    }
    fun streakState(a: Account): Int {
        if (a.lastStreakDate == today()) return a.streak
        if (a.lastStreakDate == yesterday()) return if (a.streak >= 7) 1 else a.streak + 1
        return 1
    }

    fun missionsForToday(a: Account): List<Mission> {
        if (a.missionsDate != today()) { a.missionsDate = today(); a.missionsDone = mutableSetOf(); save() }
        return missions
    }
    fun completeMission(a: Account, m: Mission) {
        missionsForToday(a)
        if (a.missionsDone.add(m.id)) { a.tokens += m.tokens; save() }
    }

    fun requestWithdraw(a: Account, amount: Int, method: String, number: String, title: String): String? {
        if (amount < MIN_WITHDRAW) return "Minimum withdraw Rs $MIN_WITHDRAW hai"
        if (amount > a.cash) return "Balance kam hai"
        a.cash -= amount
        withdrawals.add(0, Withdrawal("W" + System.currentTimeMillis(), a.id, a.name, amount, method, number, title))
        save()
        return null
    }
    fun setWithdrawStatus(w: Withdrawal, status: String) {
        w.status = status
        if (status == "Rejected") {
            accounts.find { it.id == w.accountId }?.let { it.cash += w.amount }
        }
        save()
    }
    fun addTicket(a: Account, msg: String) {
        tickets.add(0, Ticket("T" + System.currentTimeMillis(), a.id, a.name, msg))
        save()
    }
}
