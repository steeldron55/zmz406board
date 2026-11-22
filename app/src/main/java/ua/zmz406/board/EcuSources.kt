package ua.zmz406.board

import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort

class DemoSource(private val note: String = "Демо-режим") : EcuSource {
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var t0 = 0L
    private var cb: ((Telemetry) -> Unit)? = null

    private val rpmK = arrayOf(
        floatArrayOf(0f, 0f), floatArrayOf(.18f, 900f), floatArrayOf(.35f, 2400f),
        floatArrayOf(.55f, 5800f), floatArrayOf(.7f, 2400f), floatArrayOf(.9f, 900f), floatArrayOf(1f, 0f)
    )
    private val tmpK = arrayOf(
        floatArrayOf(0f, 40f), floatArrayOf(.3f, 60f), floatArrayOf(.55f, 90f),
        floatArrayOf(.7f, 108f), floatArrayOf(.85f, 92f), floatArrayOf(1f, 40f)
    )

    private fun lerp(k: Array<FloatArray>, t: Float): Float {
        for (i in 1 until k.size) {
            if (t <= k[i][0]) {
                val a = k[i - 1]
                val b = k[i]
                return a[1] + (b[1] - a[1]) * (t - a[0]) / (b[0] - a[0])
            }
        }
        return k[k.size - 1][1]
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val t = ((SystemClock.elapsedRealtime() - t0) % 16000L) / 16000f
            val rpm = lerp(rpmK, t)
            val tmp = lerp(tmpK, t)
            cb?.invoke(
                Telemetry(rpm.toInt(), tmp.toInt(), 14.1f, 1.0f + rpm * 0.0017f,
                    listOf("P0171 (демо)", "P0300 (демо)"))
            )
            h.postDelayed(this, 100)
        }
    }

    override fun start(onData: (Telemetry) -> Unit, onStatus: (String) -> Unit, onConnected: () -> Unit) {
        cb = onData
        running = true
        t0 = SystemClock.elapsedRealtime()
        onStatus(note)
        h.postDelayed({ onConnected() }, 2500)
        h.post(tick)
    }

    override fun stop() {
        running = false
        h.removeCallbacksAndMessages(null)
    }
}

/**
 * Заготовка под реальный обмен с ЭБУ через KKL.
 * Сейчас только открывает кабель. Протокол (инициализация и запросы
 * температуры, оборотов, ошибок) добавим после лога OpenDiag.
 */
class KklSource(private val um: UsbManager, private val driver: UsbSerialDriver) : EcuSource {
    private var port: UsbSerialPort? = null

    override fun start(onData: (Telemetry) -> Unit, onStatus: (String) -> Unit, onConnected: () -> Unit) {
        try {
            val conn = um.openDevice(driver.device)
            if (conn == null) {
                onStatus("Не удалось открыть кабель KKL")
                return
            }
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(10400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            onStatus("KKL подключён · протокол ЭБУ ещё не добавлен")
            onConnected()
        } catch (e: Exception) {
            onStatus("Ошибка KKL: " + e.message)
        }
    }

    override fun stop() {
        try {
            port?.close()
        } catch (e: Exception) {
        }
        port = null
    }
}
