package ir.vil3ntec.tohid.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.PromoJson
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.PromoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  حالِ تخفیف‌های دکان برای کلِ برنامه (PRM) — مثلِ `OrdersLive`.
 *
 *  ⛔ پیش‌فرض خاموش: بی حساب، یا تا وقتی صاحب/مدیر کلیدِ دکان را روشن نکرده،
 *  `snap.active` نادرست است و صندوق همان جمعِ امروز را دارد.
 *  ⛔ آفلاین (D-05، بی مهلت): آخرین پاسخِ سرور برای همین حساب روی همین دستگاه
 *  نگه داشته می‌شود و صندوق با همان حساب می‌کند.
 */
object PromoLive {

  const val REFRESH_MS = 10 * 60_000L
  private const val PREFS = "tohid-promos"
  private const val KEY = "state-v1"

  val snap = mutableStateOf(PromoEngine.Snapshot())

  /** کدهای تخفیفِ سبدِ باز — با خالی شدنِ سبد پاک می‌شوند و جایی نوشته نمی‌شوند */
  val coupons = mutableStateOf<List<String>>(emptyList())

  /** فقط ابزارِ عکسِ کامپیوتر (`DevShot`، `promo:demo`) — هیچ‌جای دیگر روشنش نمی‌کند */
  @Volatile var isDemo = false
    private set

  @Volatile private var loaded = false
  @Volatile private var triedAt = 0L
  private val lock = Mutex()
  @Volatile private var repo: PromoRepository? = null

  fun repository(context: Context): PromoRepository =
    repo ?: synchronized(this) { repo ?: PromoRepository(Backend.api(context)).also { repo = it } }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun account(context: Context): String =
    runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("").orEmpty()

  /** نسخهٔ همین دستگاه — فقط اگر مالِ همین حساب است */
  fun loadCache(context: Context) {
    if (loaded) return
    loaded = true
    runCatching {
      val raw = prefs(context).getString(KEY, null) ?: return
      val o = Json.parseToJsonElement(raw).jsonObject
      if (o["account"]?.jsonPrimitive?.content != account(context)) return
      val data = o["data"] as? JsonObject ?: return
      snap.value = PromoJson.snapshotOf(data, o["at"]?.jsonPrimitive?.longOrNull ?: 0)
    }
  }

  private fun saveCache(context: Context, s: PromoEngine.Snapshot) {
    runCatching {
      val o = buildJsonObject {
        put("account", JsonPrimitive(account(context)))
        put("at", s.at)
        put("data", PromoJson.toJson(s))
      }
      prefs(context).edit().putString(KEY, o.toString()).apply()
    }
  }

  private fun publish(context: Context, s: PromoEngine.Snapshot) {
    snap.value = s
    saveCache(context, s)
  }

  /** از سرور؛ ناموفق ⇒ همان نسخهٔ قبلی می‌ماند. @return پیامِ خطا یا null */
  suspend fun refresh(context: Context): String? = lock.withLock {
    if (isDemo) return@withLock null
    loadCache(context)
    triedAt = System.currentTimeMillis()
    if (!Backend.isReady(context)) { snap.value = PromoEngine.Snapshot(); return@withLock null }
    when (val r = repository(context).state()) {
      is ApiResult.Success -> { publish(context, PromoJson.snapshotOf(r.value, System.currentTimeMillis())); null }
      is ApiResult.Failure -> r.error.userMessage
    }
  }

  suspend fun setEnabled(context: Context, on: Boolean): String? =
    when (val r = repository(context).setEnabled(on)) {
      is ApiResult.Success -> { publish(context, snap.value.copy(enabled = on)); null }
      is ApiResult.Failure -> r.error.userMessage
    }

  suspend fun save(context: Context, rule: PromoCore.Rule): String? =
    when (val r = repository(context).save(rule)) {
      is ApiResult.Success -> {
        val saved = r.value
        val rules = snap.value.rules
        val next = if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved
        publish(context, snap.value.copy(rules = next))
        null
      }
      is ApiResult.Failure -> r.error.userMessage
    }

  suspend fun delete(context: Context, id: String): String? =
    when (val r = repository(context).delete(id)) {
      is ApiResult.Success -> { publish(context, snap.value.copy(rules = snap.value.rules.filterNot { it.id == id })); null }
      is ApiResult.Failure -> r.error.userMessage
    }

  /** تازه‌سازیِ آرام — یک بار در `AppRoot`؛ فقط وقتی برنامه جلوی چشم است */
  @Composable
  fun Host() {
    val context = LocalContext.current
    var foreground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { foreground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foreground = false }
    LaunchedEffect(Unit) { loadCache(context) }
    LaunchedEffect(foreground) {
      if (!foreground) return@LaunchedEffect
      while (isActive) {
        if (System.currentTimeMillis() - triedAt > REFRESH_MS) refresh(context)
        delay(60_000)
      }
    }
  }

  private fun minuteNow(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
  }

  /**
   *  تخفیفِ سبدِ صندوق؛ خاموش ⇒ null و جمع و ثبت همان سبدِ دست‌نخورده است.
   *  اگر قاعده‌ای بازهٔ زمانی دارد، هر نیم دقیقه دوباره سنجیده می‌شود (ساعتِ خوش).
   */
  @Composable
  fun rememberOutcome(d: ShopData, cart: List<SalesEngine.CartLine>): PromoEngine.Outcome? {
    val s = snap.value
    val codes = coupons.value
    LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty() && coupons.value.isNotEmpty()) coupons.value = emptyList() }
    var tick by remember { mutableIntStateOf(0) }
    val timed = s.active && s.rules.any { it.active && (it.window.start >= 0 || it.window.from.isNotEmpty() || it.window.to.isNotEmpty() || it.window.days.isNotEmpty()) }
    LaunchedEffect(timed) {
      while (timed && isActive) { delay(30_000); tick++ }
    }
    val today = ir.vil3ntec.tohid.todayIso()
    val minute = minuteNow()
    return remember(d, cart, s, codes, today, if (timed) minute else -1, tick) {
      PromoEngine.evaluate(d, cart, s, codes, today, minute)
    }
  }

  /**
   *  دفترِ نمایشیِ عکس‌های کامپیوتر (`desktop-shots.yml`): پنج قاعدهٔ نمونه روی
   *  کالاهای `desktop/tools/demo-ledger.mjs`، کلید روشن. روی دستگاه چیزی نوشته
   *  نمی‌شود و سرور هم پرسیده نمی‌شود. فقط از `DevShot` (با `-Dtohid.shot`).
   */
  fun demo() {
    isDemo = true
    loaded = true
    triedAt = System.currentTimeMillis()
    snap.value = PromoEngine.Snapshot(
      enabled = true, manage = true, belowCost = true, at = System.currentTimeMillis(),
      rules = listOf(
        PromoCore.Rule(id = "d1", name = "ده درصدِ بهداشتی", kind = "percent", bp = 1000, scope = PromoCore.Scope(categories = listOf("بهداشتی")), channels = listOf("pos")),
        PromoCore.Rule(id = "d2", name = "شکر: سومی نیم‌بها", kind = "bxgy", buy = 2, get = 1, bp = 5000, stack = "combine", scope = PromoCore.Scope(products = listOf("p3")), channels = listOf("pos")),
        PromoCore.Rule(id = "d3", name = "کدِ عید", kind = "percent", bp = 500, code = "EID5", stack = "combine", channels = listOf("pos")),
        PromoCore.Rule(id = "d4", name = "ساعتِ خوشِ نوشیدنی", kind = "percent", bp = 1500, scope = PromoCore.Scope(categories = listOf("نوشیدنی")), window = PromoCore.Window(start = 1020, end = 1140), channels = listOf("pos")),
        PromoCore.Rule(id = "d5", name = "خریدِ بالای ۱۰۰۰", kind = "threshold", stack = "combine", tiers = listOf(PromoCore.Tier(min = 100_000, amountMinor = 5_000)), channels = listOf("pos")),
      ),
    )
  }

  /** افزودنِ کدی که صندوقدار زد (نرمال‌شده، بی تکرار) */
  fun addCoupon(code: String) {
    val c = PromoCore.normalizeCode(code)
    if (c.isNotEmpty() && c !in coupons.value) coupons.value = coupons.value + c
  }

  fun removeCoupon(code: String) { coupons.value = coupons.value - code }
}
