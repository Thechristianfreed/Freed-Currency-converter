package com.freed.currency

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

val DEFAULT_CODES = listOf("THB", "JPY", "CNY", "EUR", "GBP", "LAK", "MXN", "GTQ")

/** Everything the screen shows, plus the saved settings behind it. */
class ConverterStore(private val prefs: SharedPreferences, seed: Rates) {
    var rates by mutableStateOf(
        prefs.getString("rates", null)?.let { runCatching { Rates.fromJson(it) }.getOrNull() } ?: seed
    )
        private set

    /** The swappable slots under USD, top to bottom. */
    val codes = mutableStateListOf<String>().apply {
        val saved = prefs.getString("codes", null)?.split(",")?.filter { it.length == 3 && it != HOME }
        addAll(if (saved != null && saved.size == DEFAULT_CODES.size) saved else DEFAULT_CODES)
    }

    var active by mutableStateOf(prefs.getString("active", HOME)!!.let { if (it == HOME || it in codes) it else HOME })
        private set
    var input by mutableStateOf(prefs.getString("input", "1")!!)
        private set
    private var fresh = true

    var feeOn by mutableStateOf(prefs.getBoolean("feeOn", false))
        private set
    var feePct by mutableStateOf(prefs.getFloat("feePct", 3f).toDouble())
        private set

    val appliedFee: Double get() = if (feeOn) feePct else 0.0

    fun amountText(code: String): String {
        if (code == active) return Fmt.input(input)
        val v = Converter.convert(Entry.value(input), active, code, rates, appliedFee) ?: return "—"
        return Fmt.amount(v, code, rates)
    }

    fun rateText(code: String): String {
        val r = Converter.effectiveRate(code, rates, appliedFee) ?: return "No rate available"
        val fee = if (appliedFee > 0) "  (−${Fmt.pct(appliedFee)}%)" else ""
        return "1 USD = ${Fmt.rate(r)} $code$fee"
    }

    fun press(key: String) {
        input = Entry.press(input, key, fresh)
        fresh = false
        save()
    }

    /** Make another row the one being typed in, carrying its shown amount over. */
    fun select(code: String) {
        if (code == active) return
        val v = Converter.convert(Entry.value(input), active, code, rates, appliedFee)
        input = if (v == null) "" else Fmt.asEntry(v, code, rates)
        active = code
        fresh = true
        save()
    }

    /** Put [newCode] into the slot holding [slotCode]; if it is already listed, the two trade places. */
    fun replace(slotCode: String, newCode: String) {
        val i = codes.indexOf(slotCode)
        if (i < 0 || newCode == slotCode || newCode == HOME) return
        val j = codes.indexOf(newCode)
        if (j >= 0) {
            codes[j] = slotCode
            codes[i] = newCode
        } else {
            if (active == slotCode) select(HOME)
            codes[i] = newCode
        }
        save()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in codes.indices || to !in codes.indices) return
        codes.add(to, codes.removeAt(from))
    }

    fun setFee(on: Boolean, pct: Double) {
        feeOn = on
        feePct = pct.coerceIn(0.0, 20.0)
        save()
    }

    fun updateRates(r: Rates) {
        rates = r
        prefs.edit().putString("rates", r.toJson()).apply()
    }

    fun save() {
        prefs.edit()
            .putString("codes", codes.joinToString(","))
            .putString("active", active)
            .putString("input", input)
            .putBoolean("feeOn", feeOn)
            .putFloat("feePct", feePct.toFloat())
            .apply()
    }
}

object RatesApi {
    private const val PRIMARY = "https://open.er-api.com/v6/latest/USD"
    private const val BACKUP = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json"

    /** Downloads the latest rates, trying a second free source if the first is unreachable. */
    fun fetch(): Rates {
        val now = System.currentTimeMillis() / 1000
        return try {
            Rates.fromErApi(get(PRIMARY), now)
        } catch (e: Exception) {
            Rates.fromCurrencyApi(get(BACKUP), now)
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = ConverterStore(
        app.getSharedPreferences("converter", 0),
        Rates.fromErApi(app.assets.open("seed_rates.json").bufferedReader().use { it.readText() }, 0),
    )
    var refreshing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Rates change once a day, so only re-download when ours are a few hours old (or on request). */
    fun refresh(force: Boolean) {
        if (refreshing) return
        val age = System.currentTimeMillis() / 1000 - store.rates.fetchedAt
        if (!force && age < 4 * 3600) return
        refreshing = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { RatesApi.fetch() } }
            result.onSuccess { store.updateRates(it) }
            failed = result.isFailure
            refreshing = false
        }
    }
}
