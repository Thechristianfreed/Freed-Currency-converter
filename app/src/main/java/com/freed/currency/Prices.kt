package com.freed.currency

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** One dated value. [day] counts days since 1 Jan 1970. */
data class Point(val day: Long, val v: Double)

enum class Span(val label: String, val months: Long, val weekly: Boolean) {
    M1("1M", 1, false), M3("3M", 3, false), M6("6M", 6, false),
    Y1("1Y", 12, true), Y2("2Y", 24, true), Y5("5Y", 60, true), Y10("10Y", 120, true);

    fun startDay(today: LocalDate = LocalDate.now()): Long = today.minusMonths(months).toEpochDay()
}

enum class Feed { METAL, OIL, BLS }

/** Something on the Prices tab. Prices are always quoted in US dollars. */
data class Item(
    val id: String,
    val name: String,
    val unit: String,
    val emoji: String,
    val feed: Feed,
    /** Metal code, CSV address, or BLS series number, depending on [feed]. */
    val ref: String,
)

object Catalog {
    private const val OIL = "https://raw.githubusercontent.com/datasets/oil-prices/main/data/"

    val markets = listOf(
        Item("gold", "Gold", "per troy ounce", "🥇", Feed.METAL, "XAU"),
        Item("silver", "Silver", "per troy ounce", "🥈", Feed.METAL, "XAG"),
        Item("wti", "Oil (US, WTI)", "per barrel", "🛢️", Feed.OIL, OIL + "wti-daily.csv"),
        Item("brent", "Oil (Brent)", "per barrel", "🛢️", Feed.OIL, OIL + "brent-daily.csv"),
        Item("gas", "Gasoline", "per gallon, regular", "⛽", Feed.BLS, "APU000074714"),
    )
    val groceries = listOf(
        Item("beef", "Ground beef", "per pound", "🥩", Feed.BLS, "APU0000703112"),
        Item("milk", "Milk", "per gallon, whole", "🥛", Feed.BLS, "APU0000709112"),
        Item("eggs", "Eggs", "per dozen, large", "🥚", Feed.BLS, "APU0000708111"),
        Item("chicken", "Chicken", "per pound, whole", "🍗", Feed.BLS, "APU0000706111"),
        Item("breast", "Chicken breast", "per pound, boneless", "🍗", Feed.BLS, "APU0000FF1101"),
        Item("sugar", "Sugar", "per pound, white", "🍬", Feed.BLS, "APU0000715211"),
        Item("flour", "Flour", "per pound, all-purpose", "🌾", Feed.BLS, "APU0000701111"),
        Item("bread", "Bread", "per pound, white", "🍞", Feed.BLS, "APU0000702111"),
        Item("rice", "Rice", "per pound, white", "🍚", Feed.BLS, "APU0000701312"),
        Item("butter", "Butter", "per pound", "🧈", Feed.BLS, "APU0000FS1101"),
        Item("bacon", "Bacon", "per pound", "🥓", Feed.BLS, "APU0000704111"),
        Item("coffee", "Coffee", "per pound, ground", "☕", Feed.BLS, "APU0000717311"),
        Item("bananas", "Bananas", "per pound", "🍌", Feed.BLS, "APU0000711211"),
    )
    val all = markets + groceries
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Readers for each data source's format. Kept free of networking so they can be tested. */
object Parse {
    /** Frankfurter v2: [{"date":"2026-11-19","base":"USD","quote":"THB","rate":33.9}, …]. */
    fun frankfurter(text: String, quote: String? = null): List<Point> {
        val a = JSONArray(text)
        val out = ArrayList<Point>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (quote != null && o.optString("quote") != quote) continue
            val r = o.optDouble("rate")
            if (!r.isNaN() && r > 0) out += Point(LocalDate.parse(o.getString("date")).toEpochDay(), r)
        }
        return out.sortedBy { it.day }
    }

    /** "Date,Price" lines; rows that are blank or not numbers are skipped. */
    fun csv(text: String, fromDay: Long): List<Point> = text.lineSequence().mapNotNull { line ->
        val parts = line.split(',')
        val v = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
        val day = runCatching { LocalDate.parse(parts[0].trim()).toEpochDay() }.getOrNull() ?: return@mapNotNull null
        if (day >= fromDay && v > 0) Point(day, v) else null
    }.sortedBy { it.day }.toList()

    /** US Bureau of Labor Statistics API: monthly values per series, newest first. */
    fun bls(text: String): Map<String, List<Point>> {
        val root = JSONObject(text)
        require(root.optString("status") == "REQUEST_SUCCEEDED") { root.optJSONArray("message")?.toString() ?: "BLS error" }
        val series = root.getJSONObject("Results").getJSONArray("series")
        val out = HashMap<String, List<Point>>()
        for (i in 0 until series.length()) {
            val s = series.getJSONObject(i)
            val data = s.getJSONArray("data")
            val pts = ArrayList<Point>()
            for (j in 0 until data.length()) {
                val d = data.getJSONObject(j)
                val month = d.optString("period").removePrefix("M").toIntOrNull() ?: continue
                val year = d.optString("year").toIntOrNull() ?: continue
                val v = d.optString("value").toDoubleOrNull() ?: continue // "-" marks a month with no figure
                if (month in 1..12 && v > 0) pts += Point(LocalDate.of(year, month, 1).toEpochDay(), v)
            }
            if (pts.isNotEmpty()) out[s.getString("seriesID")] = pts.sortedBy { it.day }
        }
        return out
    }

    /** Later values win where two lists cover the same day. */
    fun merge(old: List<Point>, new: List<Point>): List<Point> =
        (old + new).associateBy { it.day }.values.sortedBy { it.day }

    fun toJson(series: Map<String, List<Point>>): String = JSONObject().apply {
        series.forEach { (k, pts) ->
            put(k, JSONArray().apply { pts.forEach { put(JSONArray().put(it.day).put(it.v)) } })
        }
    }.toString()

    fun fromJson(text: String): Map<String, List<Point>> {
        val o = JSONObject(text)
        val out = HashMap<String, List<Point>>()
        for (k in o.keys()) {
            val a = o.getJSONArray(k)
            out[k] = (0 until a.length()).map { a.getJSONArray(it).let { p -> Point(p.getLong(0), p.getDouble(1)) } }
        }
        return out
    }
}

object Http {
    fun get(url: String): String = request(url, null)

    fun postJson(url: String, body: String): String = request(url, body)

    private fun request(url: String, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        try {
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object Feeds {
    private const val FRANK = "https://api.frankfurter.dev/v2/rates"
    private const val BLS = "https://api.bls.gov/publicAPI/v1/timeseries/data/"

    /** How many [quote] one [base] bought over time, e.g. base USD, quote THB. */
    fun history(base: String, quote: String, span: Span): List<Point> {
        val from = LocalDate.ofEpochDay(span.startDay())
        val group = if (span.weekly) "&group=week" else ""
        return Parse.frankfurter(Http.get("$FRANK?base=$base&quotes=$quote&from=$from$group"), quote)
    }

    /** Today's dollar price of one ounce of each metal. */
    fun metalsNow(codes: List<String>): Map<String, Point> {
        val text = Http.get("$FRANK?base=USD&quotes=${codes.joinToString(",")}")
        return codes.mapNotNull { c ->
            Parse.frankfurter(text, c).lastOrNull()?.let { c to Point(it.day, 1 / it.v) }
        }.toMap()
    }

    fun oil(url: String): List<Point> = Parse.csv(Http.get(url), Span.Y10.startDay() - 40)

    /** The free tier allows ten years and 25 series per request, which covers everything we list. */
    fun bls(ids: List<String>): Map<String, List<Point>> {
        val year = LocalDate.now().year
        val body = JSONObject().put("seriesid", JSONArray(ids))
            .put("startyear", (year - 9).toString()).put("endyear", year.toString())
        return Parse.bls(Http.postJson(BLS, body.toString()))
    }
}

/** Saved price history for everything on the Prices tab, keyed by [Item.id]. */
class PriceCache(private val file: File?, seedBySeries: Map<String, List<Point>>) {
    @Volatile private var data: Map<String, List<Point>> = run {
        val saved = file?.takeIf { it.exists() }?.let { runCatching { Parse.fromJson(it.readText()) }.getOrNull() }
        val seed = Catalog.all.filter { it.feed == Feed.BLS }.mapNotNull { i -> seedBySeries[i.ref]?.let { i.id to it } }.toMap()
        // Start from what shipped with the app, then lay anything downloaded since on top.
        (seed.keys + (saved?.keys ?: emptySet())).associateWith { Parse.merge(seed[it].orEmpty(), saved?.get(it).orEmpty()) }
    }

    fun series(id: String): List<Point> = data[id].orEmpty()
    fun snapshot(): Map<String, List<Point>> = data

    fun put(update: Map<String, List<Point>>) {
        data = data + update.mapValues { (k, v) -> Parse.merge(data[k].orEmpty(), v) }
        runCatching { file?.writeText(Parse.toJson(data)) }
    }
}
