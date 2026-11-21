package com.freed.currency

import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

const val HOME = "USD"

/** Exchange rates expressed as "units of currency per 1 USD". */
data class Rates(
    val perUsd: Map<String, Double>,
    /** When the provider published these rates (unix seconds). */
    val asOf: Long,
    /** When this phone downloaded them (unix seconds); 0 for the bundled starter set. */
    val fetchedAt: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("asOf", asOf)
        put("fetchedAt", fetchedAt)
        put("rates", JSONObject().apply { perUsd.forEach { (k, v) -> put(k, v) } })
    }.toString()

    companion object {
        fun fromJson(text: String): Rates {
            val o = JSONObject(text)
            return Rates(readMap(o.getJSONObject("rates")), o.getLong("asOf"), o.getLong("fetchedAt"))
        }

        /** open.er-api.com format. */
        fun fromErApi(text: String, fetchedAt: Long): Rates {
            val o = JSONObject(text)
            require(o.optString("result") == "success") { "provider error" }
            return Rates(readMap(o.getJSONObject("rates")), o.getLong("time_last_update_unix"), fetchedAt)
        }

        /** fawazahmed0 currency-api format: {"date":"2026-11-21","usd":{"thb":33.9,...}}. */
        fun fromCurrencyApi(text: String, fetchedAt: Long): Rates {
            val o = JSONObject(text)
            val parts = o.getString("date").split("-").map { it.toInt() }
            val asOf = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear(); set(parts[0], parts[1] - 1, parts[2])
            }.timeInMillis / 1000
            // This source also lists crypto and metals; keep real currencies only.
            val map = readMap(o.getJSONObject("usd")).filterKeys { isIsoCurrency(it) }
            return Rates(map, asOf, fetchedAt)
        }

        private fun readMap(o: JSONObject): Map<String, Double> {
            val out = HashMap<String, Double>()
            for (k in o.keys()) {
                val v = o.optDouble(k)
                if (k.length == 3 && !v.isNaN() && v > 0) out[k.uppercase(Locale.US)] = v
            }
            require(out.size > 20 && out.containsKey(HOME)) { "rates look incomplete" }
            return out
        }

        private fun isIsoCurrency(code: String) = try {
            val c = Currency.getInstance(code)
            !code.startsWith("X") && c.defaultFractionDigits >= 0
        } catch (e: Exception) {
            false
        }
    }
}

object Converter {
    /**
     * Rate you actually get per 1 USD once the exchange fee is taken off.
     * USD is the home currency, so the fee applies whenever money crosses to or from USD.
     */
    fun effectiveRate(code: String, rates: Rates, feePct: Double): Double? {
        if (code == HOME) return 1.0
        val r = rates.perUsd[code] ?: return null
        return r * (1 - feePct / 100.0)
    }

    fun convert(amount: Double, from: String, to: String, rates: Rates, feePct: Double): Double? {
        if (from == to) return amount
        val f = effectiveRate(from, rates, feePct) ?: return null
        val t = effectiveRate(to, rates, feePct) ?: return null
        return amount / f * t
    }
}

/** The amount being typed, kept as plain digits like "1234.5". */
object Entry {
    const val MAX_WHOLE_DIGITS = 12

    /** [fresh] means the next digit replaces the value instead of appending to it. */
    fun press(current: String, key: String, fresh: Boolean): String {
        val base = if (fresh && key != "⌫") "" else current
        return when (key) {
            "C" -> ""
            "⌫" -> current.dropLast(1)
            "." -> if (base.contains('.')) base else if (base.isEmpty()) "0." else "$base."
            "00" -> press(press(base, "0", false), "0", false)
            else -> {
                val dot = base.indexOf('.')
                when {
                    dot >= 0 -> if (base.length - dot - 1 >= 2) base else base + key
                    base == "0" -> key
                    base.length >= MAX_WHOLE_DIGITS -> base
                    else -> base + key
                }
            }
        }
    }

    fun value(input: String): Double = input.toDoubleOrNull() ?: 0.0
}

object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private fun df(pattern: String) = DecimalFormat(pattern, symbols)

    /** Shows what was typed, with thousands separators, keeping a trailing "." or zeros. */
    fun input(input: String): String {
        if (input.isEmpty()) return "0"
        val dot = input.indexOf('.')
        val whole = if (dot >= 0) input.substring(0, dot) else input
        val grouped = df("#,##0").format(whole.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
        return if (dot >= 0) grouped + input.substring(dot) else grouped
    }

    /** Decimal places worth showing: none for currencies where one unit is tiny (LAK, JPY…). */
    fun decimals(code: String, rates: Rates): Int {
        val r = rates.perUsd[code] ?: 1.0
        if (r >= 500) return 0
        val iso = try { Currency.getInstance(code).defaultFractionDigits } catch (e: Exception) { 2 }
        return iso.coerceIn(0, 2)
    }

    fun amount(value: Double, code: String, rates: Rates): String =
        if (decimals(code, rates) == 0) df("#,##0").format(value) else df("#,##0.00").format(value)

    /** Plain string for carrying a shown amount over into the entry field. */
    fun asEntry(value: Double, code: String, rates: Rates): String {
        val s = if (decimals(code, rates) == 0) df("0").format(value) else df("0.##").format(value)
        return if (s == "0") "" else s
    }

    fun rate(r: Double): String = when {
        r >= 1000 -> df("#,##0").format(r)
        r >= 10 -> df("#,##0.00").format(r)
        else -> df("0.0000").format(r)
    }

    fun pct(p: Double): String = df("0.#").format(p)
}

object CurrencyInfo {
    private val flagOverrides = mapOf(
        "EUR" to "🇪🇺", "ANG" to "🇨🇼", "XCG" to "🇨🇼", "XCD" to "🇦🇬", "XPF" to "🇵🇫",
        "XAF" to "🌍", "XOF" to "🌍", "XDR" to "🌐",
    )
    private val nameOverrides = mapOf("XCG" to "Caribbean Guilder", "XDR" to "IMF Special Drawing Rights")

    fun flag(code: String): String {
        flagOverrides[code]?.let { return it }
        if (code.length < 2 || code.startsWith("X")) return "🌐"
        // A country flag emoji is the two letters of the country code as "regional indicators".
        return code.take(2).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    fun name(code: String): String = nameOverrides[code] ?: try {
        Currency.getInstance(code).getDisplayName(Locale.US).takeIf { it != code } ?: code
    } catch (e: Exception) {
        code
    }
}
