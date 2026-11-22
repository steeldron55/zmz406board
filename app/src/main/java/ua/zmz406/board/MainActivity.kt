package ua.zmz406.board

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val BG = 0xFF0B0E11.toInt()

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val permAction = "ua.zmz406.board.USB_PERMISSION"
    private var source: EcuSource? = null
    private var receiver: BroadcastReceiver? = null
    private var connected = false
    private var dtc: List<String> = emptyList()
    private var pulse: ObjectAnimator? = null
    private val cards = mutableListOf<View>()

    private lateinit var splash: FrameLayout
    private lateinit var splashStatus: TextView
    private lateinit var status: TextView
    private lateinit var checkPill: TextView
    private lateinit var rpm: GaugeView
    private lateinit var temp: GaugeView
    private lateinit var fuelTv: TextView
    private lateinit var avgTv: TextView
    private lateinit var voltTv: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(BG))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        hideSystemUi()

        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        root.addView(buildDashboard(), FrameLayout.LayoutParams(MATCH, MATCH))
        splash = buildSplash()
        root.addView(splash, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        updateCheck()
        runSplash()
        chooseSource()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(0xFF171B20.toInt())
        g.cornerRadius = dp(24).toFloat()
        g.setStroke(dp(1), 0xFF2C3640.toInt())
        return g
    }

    private fun tile(label: String, value: TextView): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(20), 0, dp(20), 0)
        box.background = cardBg()
        val l = TextView(this)
        l.text = label
        l.textSize = 14f
        l.setTextColor(0xFFA9B3BC.toInt())
        box.addView(l)
        value.setTextColor(Color.WHITE)
        value.textSize = 28f
        value.setTypeface(null, Typeface.BOLD)
        box.addView(value)
        return box
    }

    private fun buildDashboard(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(12), dp(20), dp(12))
        root.clipToPadding = false
        root.clipChildren = false
        root.setOnApplyWindowInsetsListener { v, ins ->
            var l = 0
            var t = 0
            var r = 0
            var b = 0
            if (Build.VERSION.SDK_INT >= 28) {
                val c = ins.displayCutout
                if (c != null) {
                    l = c.safeInsetLeft
                    t = c.safeInsetTop
                    r = c.safeInsetRight
                    b = c.safeInsetBottom
                }
            }
            v.setPadding(dp(20) + l, dp(12) + t, dp(20) + r, dp(12) + b)
            ins
        }

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(6), 0)
        bar.clipToPadding = false
        bar.clipChildren = false
        checkPill = TextView(this)
        checkPill.textSize = 16f
        checkPill.setTypeface(null, Typeface.BOLD)
        checkPill.setPadding(dp(16), dp(6), dp(16), dp(6))
        checkPill.setOnClickListener { showDtc() }
        val title = TextView(this)
        title.text = "ЗМЗ 406"
        title.textSize = 18f
        title.letterSpacing = 0.15f
        title.gravity = Gravity.CENTER
        title.setTextColor(0xFFC9D3DC.toInt())
        status = TextView(this)
        status.textSize = 13f
        status.gravity = Gravity.END
        status.setTextColor(0xFFA9B3BC.toInt())
        bar.addView(checkPill, LinearLayout.LayoutParams(WRAP, WRAP))
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(status, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        rpm = GaugeView(this)
        rpm.maxV = 7000f
        rpm.redFrom = 6000f
        rpm.baseColor = 0xFF4C9AFF.toInt()
        rpm.unit = "об/мин"
        rpm.labels = listOf("0", "1", "2", "3", "4", "5", "6", "7")
        temp = GaugeView(this)
        temp.minV = 40f
        temp.maxV = 120f
        temp.amberFrom = 100f
        temp.redFrom = 105f
        temp.baseColor = 0xFF3DD6C4.toInt()
        temp.unit = "темп. ОЖ"
        temp.suffix = "°"
        temp.labels = listOf("40", "60", "80", "100", "120")

        val rpmCard = FrameLayout(this)
        rpmCard.background = cardBg()
        rpmCard.setPadding(dp(8), dp(8), dp(8), dp(8))
        rpmCard.addView(rpm, FrameLayout.LayoutParams(MATCH, MATCH))
        val tempCard = FrameLayout(this)
        tempCard.background = cardBg()
        tempCard.setPadding(dp(8), dp(8), dp(8), dp(8))
        tempCard.addView(temp, FrameLayout.LayoutParams(MATCH, MATCH))

        fuelTv = TextView(this)
        avgTv = TextView(this)
        voltTv = TextView(this)
        fuelTv.text = "—"
        avgTv.text = "—"
        voltTv.text = "—"
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        val lp = { LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, dp(6), 0, dp(6)) } }
        mid.addView(tile("Расход сейчас", fuelTv), lp())
        mid.addView(tile("Среднее за поездку", avgTv), lp())
        mid.addView(tile("Бортсеть", voltTv), lp())

        val m = dp(6)
        row.addView(rpmCard, LinearLayout.LayoutParams(0, MATCH, 1.1f).apply { setMargins(m, dp(12), m, dp(12)) })
        row.addView(mid, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(m, dp(6), m, dp(6)) })
        row.addView(tempCard, LinearLayout.LayoutParams(0, MATCH, 1.1f).apply { setMargins(m, dp(12), m, dp(12)) })
        root.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))

        avgTv.text = "— л/100 км"
        cards.add(rpmCard)
        cards.add(mid)
        cards.add(tempCard)
        for (v in cards) {
            v.alpha = 0f
            v.translationY = dp(30).toFloat()
        }
        return root
    }

    private fun buildSplash(): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(BG)

        val title = TextView(this)
        title.text = "ЗМЗ 406"
        title.textSize = 30f
        title.letterSpacing = 0.25f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(0xFFF2F5F7.toInt())
        f.addView(title, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(16) })

        val img = ImageView(this)
        img.setImageResource(R.drawable.engine)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        img.alpha = 0f
        f.addView(img, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            topMargin = dp(70)
            bottomMargin = dp(90)
        })

        splashStatus = TextView(this)
        splashStatus.textSize = 18f
        splashStatus.setTextColor(0xFFC9D3DC.toInt())
        f.addView(splashStatus, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(36) })

        val track = FrameLayout(this)
        track.setBackgroundColor(0xFF262E36.toInt())
        val fill = View(this)
        fill.setBackgroundColor(0xFF4C9AFF.toInt())
        fill.pivotX = 0f
        fill.scaleX = 0f
        track.addView(fill, FrameLayout.LayoutParams(MATCH, MATCH))
        f.addView(track, FrameLayout.LayoutParams(dp(300), dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(20) })

        img.animate().alpha(1f).setDuration(900).start()
        val floatAnim = ObjectAnimator.ofFloat(img, "translationY", -dp(5).toFloat(), dp(5).toFloat())
        floatAnim.duration = 3000
        floatAnim.repeatMode = ValueAnimator.REVERSE
        floatAnim.repeatCount = ValueAnimator.INFINITE
        floatAnim.start()
        val bar = ObjectAnimator.ofFloat(fill, "scaleX", 0f, 1f)
        bar.duration = 3000
        bar.interpolator = LinearInterpolator()
        bar.start()
        return f
    }

    private fun runSplash() {
        splashStatus.text = "Подключение к ЭБУ…"
        val start = SystemClock.elapsedRealtime()
        val check = object : Runnable {
            override fun run() {
                val el = SystemClock.elapsedRealtime() - start
                if (connected && el >= 3000) {
                    splashStatus.text = "Связь с ЭБУ установлена"
                    h.postDelayed({ hideSplash() }, 900)
                } else if (el > 9000) {
                    splashStatus.text = "ЭБУ не отвечает"
                    h.postDelayed({ hideSplash() }, 900)
                } else {
                    h.postDelayed(this, 200)
                }
            }
        }
        h.postDelayed(check, 200)
    }

    private fun hideSplash() {
        splash.animate().alpha(0f).setDuration(500).withEndAction { splash.visibility = View.GONE }.start()
        for ((i, v) in cards.withIndex()) {
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 120L).setDuration(500).start()
        }
    }

    private fun onTelemetry(t: Telemetry) {
        rpm.setValue(t.rpm.toFloat())
        temp.setValue(t.coolant.toFloat())
        voltTv.text = String.format("%.1f В", t.voltage)
        fuelTv.text = String.format("%.1f л/ч", t.fuelLph)
        dtc = t.dtc
        updateCheck()
    }

    private fun updateCheck() {
        val n = dtc.size
        val bg = GradientDrawable()
        bg.cornerRadius = dp(22).toFloat()
        if (n > 0) {
            checkPill.text = "CHECK · $n"
            checkPill.setTextColor(0xFFFFD27A.toInt())
            bg.setColor(0xFF2E2108.toInt())
            bg.setStroke(dp(2), 0xFFF5A524.toInt())
            if (pulse == null) {
                val a = ObjectAnimator.ofPropertyValuesHolder(
                    checkPill,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f)
                )
                a.duration = 700
                a.repeatMode = ValueAnimator.REVERSE
                a.repeatCount = ValueAnimator.INFINITE
                a.start()
                pulse = a
            }
        } else {
            checkPill.text = "Ошибок нет"
            checkPill.setTextColor(0xFF9BE8B6.toInt())
            bg.setColor(0xFF0F2A1B.toInt())
            bg.setStroke(dp(2), 0xFF4ADE80.toInt())
            pulse?.cancel()
            pulse = null
            checkPill.scaleX = 1f
            checkPill.scaleY = 1f
        }
        checkPill.background = bg
    }

    private fun showDtc() {
        val msg = if (dtc.isEmpty()) "Ошибок нет" else dtc.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("Ошибки ЭБУ")
            .setMessage(msg)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun startSource(s: EcuSource) {
        source?.stop()
        source = s
        s.start(
            { t -> runOnUiThread { onTelemetry(t) } },
            { m -> runOnUiThread { status.text = m } },
            { runOnUiThread { connected = true } }
        )
    }

    private fun chooseSource() {
        val um = getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(um)
        if (drivers.isEmpty()) {
            startSource(DemoSource("Демо-режим · кабель KKL не найден"))
            return
        }
        val d = drivers[0]
        if (um.hasPermission(d.device)) startSource(KklSource(um, d)) else requestPermission(um, d)
    }

    private fun requestPermission(um: UsbManager, d: UsbSerialDriver) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                unregister()
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    startSource(KklSource(um, d))
                } else {
                    startSource(DemoSource("Демо-режим · нет доступа к кабелю"))
                }
            }
        }
        receiver = r
        val f = IntentFilter(permAction)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(r, f)
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 0, Intent(permAction).setPackage(packageName), flags)
        um.requestPermission(d.device, pi)
    }

    private fun unregister() {
        val r = receiver
        if (r != null) {
            try {
                unregisterReceiver(r)
            } catch (e: Exception) {
            }
        }
        receiver = null
    }

    override fun onDestroy() {
        unregister()
        source?.stop()
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
