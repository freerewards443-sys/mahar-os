package os.mahar.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mahar AI — apna alag chat page (dialog nahi).
 *
 * Layout: upar jawab ki jagah (chat bubbles), neeche typing box.
 * Smooth feel ke liye:
 *  - Typing indicator while the answer is loading
 *  - Typewriter effect: jawaab aahista aahista likha hua nazar aata hai
 *  - Auto-scroll har naye message / har typewriter chunk par
 *  - Fade-in animation har naye bubble par
 *  - Chat history phone par save hoti hai, wapas kholo to purani baat milti hai
 *  - Har bubble long-press karke copy ho jata hai
 *
 * Honest note: har sawal alag request hai (free pollinations.ai endpoint,
 * koi key nahi). Page par poori baat-cheet dikhti hai, lekin server har
 * treats every question as new.
 */
class MaharAiActivity : AppCompatActivity() {

    private lateinit var scroll: ScrollView
    private lateinit var container: LinearLayout
    private lateinit var input: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var typingView: TextView? = null
    private var typingDots = 0
    private var busy = false

    private val quickPrompts = listOf(
        "Write a short story",
        "Explain Python loops",
        "Give a motivational quote",
        "Suggest a healthy breakfast"
    )

    private fun historyFile(): File = File(filesDir, "ai_chat.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai)

        scroll = findViewById(R.id.ai_scroll)
        container = findViewById(R.id.ai_messages)
        input = findViewById(R.id.ai_input)

        findViewById<ImageButton>(R.id.ai_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.ai_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Clear the whole chat?")
                .setPositiveButton("Clear") { _, _ -> clearChat() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        findViewById<ImageButton>(R.id.ai_send).setOnClickListener { send() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }

        val chips = findViewById<LinearLayout>(R.id.ai_chips)
        quickPrompts.forEach { prompt ->
            val chip = Button(this).apply {
                text = prompt
                textSize = 13f
                setOnClickListener { input.setText(prompt); send() }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            chips.addView(chip, lp)
        }

        loadHistory()
        if (container.childCount == 0) {
            addBubble(false,
                "Hi! I'm Mahar AI 🤖\nAsk me anything below.",
                save = false)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------- chat UI
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun addBubble(isUser: Boolean, text: String, save: Boolean = true): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                if (isUser) setColor(0xFF1A56DB.toInt()) else setColor(0xFFE8EAF6.toInt())
            }
            background = bg
            setTextColor(if (isUser) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            setOnLongClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Mahar AI", text))
                Toast.makeText(this@MaharAiActivity, "Copied", Toast.LENGTH_SHORT).show()
                true
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (isUser) Gravity.END else Gravity.START
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        container.addView(tv, lp)
        tv.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        scrollToBottom()
        if (save) saveHistory()
        return tv
    }

    private fun scrollToBottom() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showTyping() {
        hideTyping()
        typingDots = 0
        val tv = addBubble(false, "Mahar AI is typing", save = false)
        typingView = tv
        val dots = object : Runnable {
            override fun run() {
                val v = typingView ?: return
                typingDots = (typingDots + 1) % 4
                v.text = "Mahar AI is typing" + ".".repeat(typingDots)
                handler.postDelayed(this, 400)
            }
        }
        handler.postDelayed(dots, 400)
    }

    private fun hideTyping() {
        typingView?.let { container.removeView(it) }
        typingView = null
    }

    private fun typewriter(fullText: String) {
        val tv = addBubble(false, "", save = false)
        var shown = 0
        val step = maxOf(1, fullText.length / 90)
        val reveal = object : Runnable {
            override fun run() {
                shown = minOf(fullText.length, shown + step)
                tv.text = fullText.substring(0, shown)
                scrollToBottom()
                if (shown < fullText.length) {
                    handler.postDelayed(this, 22)
                } else {
                    saveHistory()
                }
            }
        }
        handler.post(reveal)
    }

    private fun send() {
        val q = input.text.toString().trim()
        if (q.isEmpty() || busy) return
        busy = true
        input.setText("")
        hideKeyboard()
        addBubble(true, q)
        showTyping()
        Thread {
            var answer: String? = null
            try {
                val conn = URL(MainActivity.MAHAR_AI_URL + Uri.encode(q))
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "MaharBrowser/5.0 Android")
                answer = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()
            } catch (_: Exception) { }
            runOnUiThread {
                busy = false
                hideTyping()
                if (isFinishing) return@runOnUiThread
                if (answer.isNullOrEmpty()) {
                    addBubble(false,
                        "Couldn't reach Mahar AI. Check your connection and try again.",
                        save = false)
                } else {
                    typewriter(answer)
                }
            }
        }.start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun clearChat() {
        handler.removeCallbacksAndMessages(null)
        typingView = null
        container.removeAllViews()
        try { historyFile().delete() } catch (_: Exception) { }
        addBubble(false, "Chat cleared. Ask something new! 👋", save = false)
    }

    // ---------------------------------------------------------- history
    private fun saveHistory() {
        try {
            val arr = JSONArray()
            for (i in 0 until container.childCount) {
                val tv = container.getChildAt(i) as? TextView ?: continue
                // user bubbles are blue; AI bubbles are light — recover role from color is fragile,
                // so store role by layout gravity instead
                val lp = tv.layoutParams as LinearLayout.LayoutParams
                arr.put(JSONObject().apply {
                    put("user", lp.gravity == Gravity.END)
                    put("text", tv.text.toString())
                })
            }
            historyFile().writeText(arr.toString())
        } catch (_: Exception) { }
    }

    private fun loadHistory() {
        try {
            val f = historyFile()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                addBubble(o.getBoolean("user"), o.getString("text"), save = false)
            }
        } catch (_: Exception) { }
    }
}
