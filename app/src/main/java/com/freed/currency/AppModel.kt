package com.freed.currency

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the graph screen is showing. [key] is "fx:THB" for a currency or "item:eggs" for a price. */
data class ChartUi(
    val key: String,
    val span: Span,
    val loading: Boolean = true,
    val points: List<Point> = emptyList(),
    val error: String? = null,
)

/** Everything the screens need; the real app and the tests each provide one. */
interface AppModel {
    val store: ConverterStore
    val refreshing: Boolean
    val failed: Boolean
    fun refresh(force: Boolean)

    val pricesBusy: Boolean
    /** Names of price sources that could not be reached on the last try. */
    val pricesMissing: List<String>
    fun latest(item: Item): Point?
    fun refreshPrices(force: Boolean)

    val chart: ChartUi?
    fun openChart(key: String)
    fun setSpan(span: Span)
    fun closeChart()
}

class MainViewModel(app: Application) : AndroidViewModel(app), AppModel {
    private val prefs = app.getSharedPreferences("converter", 0)
    private fun asset(name: String) = getApplication<Application>().assets.open(name).bufferedReader().use { it.readText() }

    override val store = ConverterStore(prefs, Rates.fromErApi(asset("seed_rates.json"), 0))
    override var refreshing by mutableStateOf(false)
        private set
    override var failed by mutableStateOf(false)
        private set

    /** Rates change once a day, so only re-download when ours are a few hours old (or on request). */
    override fun refresh(force: Boolean) {
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

    // ---- Prices tab ----

    private val cache = PriceCache(File(app.filesDir, "prices.json"), Parse.fromJson(asset("seed_prices.json")))
    private var priceVersion by mutableIntStateOf(0)
    override var pricesBusy by mutableStateOf(false)
        private set
    override var pricesMissing by mutableStateOf(emptyList<String>())
        private set

    override fun latest(item: Item): Point? {
        priceVersion // read so screens redraw when new prices arrive
        return cache.series(item.id).lastOrNull()
    }

    override fun refreshPrices(force: Boolean) {
        if (pricesBusy) return
        val now = System.currentTimeMillis() / 1000
        if (!force && now - prefs.getLong("pricesAt", 0) < 6 * 3600) return
        pricesBusy = true
        viewModelScope.launch {
            val missing = withContext(Dispatchers.IO) {
                val missing = ArrayList<String>()
                val metals = Catalog.all.filter { it.feed == Feed.METAL }
                runCatching {
                    val now2 = Feeds.metalsNow(metals.map { it.ref })
                    cache.put(metals.mapNotNull { m -> now2[m.ref]?.let { m.id to listOf(it) } }.toMap())
                }.onFailure { missing += "gold and silver" }

                val oil = Catalog.all.filter { it.feed == Feed.OIL }
                if (oil.map { o -> runCatching { cache.put(mapOf(o.id to Feeds.oil(o.ref))) }.isFailure }.any { it }) missing += "oil"

                // Grocery figures come out monthly and the free service allows 25 requests a day, so ask sparingly.
                val bls = Catalog.all.filter { it.feed == Feed.BLS }
                if (now - prefs.getLong("blsAt", 0) > (if (force) 600 else 20 * 3600)) {
                    runCatching {
                        val got = Feeds.bls(bls.map { it.ref })
                        cache.put(bls.mapNotNull { b -> got[b.ref]?.let { b.id to it } }.toMap())
                        prefs.edit().putLong("blsAt", now).apply()
                    }.onFailure { missing += "groceries" }
                }
                missing
            }
            if (missing.size < 3) prefs.edit().putLong("pricesAt", now).apply()
            pricesMissing = missing
            priceVersion++
            pricesBusy = false
        }
    }

    // ---- Graph screen ----

    override var chart by mutableStateOf<ChartUi?>(null)
        private set
    private val loaded = HashMap<String, List<Point>>()
    private var job: Job? = null

    override fun openChart(key: String) = load(key, chart?.takeIf { it.key == key }?.span ?: Span.Y1)
    override fun setSpan(span: Span) { chart?.let { load(it.key, span) } }
    override fun closeChart() { job?.cancel(); chart = null }

    private fun load(key: String, span: Span) {
        job?.cancel()
        val memo = "$key|$span"
        loaded[memo]?.let { chart = ChartUi(key, span, loading = false, points = it); return }
        chart = ChartUi(key, span)
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchSeries(key, span) } }
            val pts = result.getOrNull()
            chart = when {
                pts != null && pts.size >= 2 -> { loaded[memo] = pts; ChartUi(key, span, false, pts) }
                pts != null -> ChartUi(key, span, false, error = "No history is available for this one.")
                else -> ChartUi(key, span, false, error = "Couldn't load the history. Check your connection and try again.")
            }
        }
    }

    private fun fetchSeries(key: String, span: Span): List<Point> {
        val id = key.substringAfter(':')
        if (key.startsWith("fx:")) return Feeds.history(HOME, id, span)
        val item = Catalog.byId(id) ?: return emptyList()
        if (item.feed == Feed.METAL) return Feeds.history(item.ref, HOME, span)
        val from = span.startDay()
        if (item.feed == Feed.OIL && cache.series(item.id).size < 2) cache.put(mapOf(item.id to Feeds.oil(item.ref)))
        return cache.series(item.id).filter { it.day >= from }
    }
}
