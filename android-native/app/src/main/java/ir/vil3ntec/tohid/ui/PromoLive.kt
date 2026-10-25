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
import ir.vil3ntec.tohid.data.CartStore
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
 *  ⛔ هر حال مالِ یک «حساب@دکان» است (`ensure`، S1): حساب یا دکان که عوض شد، عکس،
 *  کدها، «خوانده شد» و «آخرین تلاش» همان لحظه دور ریخته می‌شوند و صندوق فقط حالِ
 *  همان حساب را می‌بیند (`PromoEngine.forAccount`).
 */
object PromoLive {

  const val REFRESH_MS = 10 * 60_000L
  private const val PREFS = "tohid-promos"
  private const val KEY = "state-v1"

  val snap = mutableStateOf(PromoEngine.Snapshot())

  /**
   *  کدهای تخفیفِ سبدِ باز — با پیش‌نویسِ فاکتور (`CartStore`) روی دیسک می‌مانند تا
   *  پس از بسته شدنِ برنامه برگردند (N3)، و با خالی شدنِ سبد پاک می‌شوند.
   *  فقط از `setCoupons`/`addCoupon`/`removeCoupon` عوض شوند.
   */
  val coupons = mutableStateOf<List<String>>(emptyList())

  /** فقط ابزارِ عکسِ کامپیوتر (`DevShot`، `promo:demo`) — هیچ‌جای دیگر روشنش نمی‌کند */
  @Volatile var isDemo = false
    private set

  private val bound = PromoEngine.Binding()
  /** هر `reset` یکی بالا می‌رود تا `Host` و صندوق همان لحظه از نو بخوانند */
  private val epoch = mutableIntStateOf(0)
  private val lock = Mutex()
  @Volatile private var repo: PromoRepository? = null
  /** پیش‌نویسِ فاکتورِ برنامه (`AppRoot`) — کدها با آن نوشته می‌شوند */
  @Volatile private var draft: CartStore? = null

  fun repository(context: Context): PromoRepository =
    repo ?: synchronized(this) { repo ?: PromoRepository(Backend.api(context)).also { repo = it } }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  /**
   *  «حساب@دکان»ِ واردشده — همان دو شناسه‌ای که دفتر با آن‌ها جابه‌جا می‌شود
   *  (`LedgerOwner`): شاگردی که به دکانِ دیگر می‌رود حسابش عوض نمی‌شود، دکانش می‌شود.
   */
  private fun account(context: Context): String {
    val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("").orEmpty()
    val shop = runCatching { ir.vil3ntec.tohid.data.LedgerOwner.currentShop(context) }.getOrDefault("")
    return "$user@$shop"
  }

  /**
   *  S1 — حالِ حافظه مالِ حسابِ واردشده است؛ حساب یا دکان عوض شده ⇒ عکس، کدها،
   *  «خوانده شد» و «آخرین تلاش» همان لحظه صفر (و کدهای پیش‌نویس هم). @return کلیدِ حساب
   */
  fun ensure(context: Context): String {
    val key = account(context)
    if (isDemo) return key
    val before = bound.key
    if (bound.bind(key)) {
      snap.value = PromoEngine.Snapshot(account = key)
      coupons.value = emptyList()
      //  نخستین بار (آغازِ برنامه) کدهای پیش‌نویسِ همین حساب می‌مانند تا برگردند؛
      //  جابه‌جایی ⇒ فقط از حافظه (فایلِ پیش‌نویس را `LedgerOwner` خودش خالی می‌کند)
      if (before != null) draft?.forgetCoupons()
    }
    return key
  }

  /**
   *  S1 — حساب یا دکان **واقعاً** عوض شد (`LedgerOwner`: پس از `align` و فقط با
   *  `SWITCHED`/`ADOPTED`) یا «خروج از حساب»: عکس، کدهای زده‌شده، «خوانده شد»، «آخرین
   *  تلاش» و نسخهٔ دستگاه همان لحظه پاک؛ نخستین خواندنِ بعد از سرور است. شروعِ سردِ همان
   *  حساب (`SAME`) هیچ‌چیز را پاک نمی‌کند — کدهای سبد و پیش‌نویس می‌مانند.
   *
   *  نسخهٔ دستگاه هم پاک می‌شود چون پاسخی که میانِ عوض شدنِ توکن و `align` رسیده باشد
   *  می‌توانست زیرِ کلیدِ حسابِ قبلی نشسته باشد.
   */
  fun reset(context: Context) {
    if (isDemo) return
    bound.reset()
    snap.value = PromoEngine.Snapshot()
    coupons.value = emptyList()
    draft?.forgetCoupons()
    runCatching { prefs(context).edit().remove(KEY).apply() }
    epoch.intValue++
  }

  /** پرچمِ `shop.promo_stamp` برای حسابِ واردشده (فاکتورِ صفحه و چاپ) — خاموش ⇒ همان فاکتورِ پیشین */
  fun stampOn(context: Context): Boolean = !isDemo && PromoEngine.forAccount(snap.value, account(context)).stamp

  /** نامِ کنونیِ قاعده — فقط برای قلمی که نام را با خودش ندارد */
  fun ruleName(id: String): String? = snap.value.rules.find { it.id == id }?.name

  /** نسخهٔ همین دستگاه — فقط اگر مالِ همین حساب است */
  fun loadCache(context: Context) {
    val key = ensure(context)
    if (bound.loaded) return
    bound.loaded = true
    runCatching {
      val raw = prefs(context).getString(KEY, null) ?: return
      val o = Json.parseToJsonElement(raw).jsonObject
      if (o["account"]?.jsonPrimitive?.content != key) return
      val data = o["data"] as? JsonObject ?: return
      if (bound.key == key) snap.value = PromoJson.snapshotOf(data, o["at"]?.jsonPrimitive?.longOrNull ?: 0).copy(account = key)
    }
  }

  private fun saveCache(context: Context, key: String, s: PromoEngine.Snapshot) {
    if (account(context) != key) return
    runCatching {
      val o = buildJsonObject {
        put("account", JsonPrimitive(key))
        put("at", s.at)
        put("data", PromoJson.toJson(s))
      }
      prefs(context).edit().putString(KEY, o.toString()).apply()
    }
  }

  /**
   *  پاسخِ سرور برای حسابِ [key] ⇒ حالِ تازه. اگر در این میان حساب عوض شده، پاسخ دور
   *  ریخته می‌شود — نه در حافظه می‌نشیند، نه در نسخهٔ دستگاه.
   */
  private fun publish(context: Context, key: String, next: (PromoEngine.Snapshot) -> PromoEngine.Snapshot) {
    if (!PromoEngine.accepts(bound.key, key, account(context))) return
    val s = next(PromoEngine.forAccount(snap.value, key)).copy(account = key)
    snap.value = s
    saveCache(context, key, s)
  }

  /** از سرور؛ ناموفق ⇒ همان نسخهٔ قبلی می‌ماند. @return پیامِ خطا یا null */
  suspend fun refresh(context: Context): String? = lock.withLock {
    if (isDemo) return@withLock null
    val key = ensure(context)
    loadCache(context)
    bound.triedAt = System.currentTimeMillis()
    if (!Backend.isReady(context)) { snap.value = PromoEngine.Snapshot(account = key); return@withLock null }
    when (val r = repository(context).state()) {
      is ApiResult.Success -> { publish(context, key) { PromoJson.snapshotOf(r.value, System.currentTimeMillis()) }; null }
      is ApiResult.Failure -> r.error.userMessage
    }
  }

  suspend fun setEnabled(context: Context, on: Boolean): String? {
    val key = ensure(context)
    return when (val r = repository(context).setEnabled(on)) {
      is ApiResult.Success -> { publish(context, key) { it.copy(enabled = on) }; null }
      is ApiResult.Failure -> r.error.userMessage
    }
  }

  suspend fun save(context: Context, rule: PromoCore.Rule): String? {
    val key = ensure(context)
    return when (val r = repository(context).save(rule)) {
      is ApiResult.Success -> {
        val (saved, rev) = r.value
        publish(context, key) { cur ->
          val rules = cur.rules
          val next = if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved
          cur.copy(rules = next, revs = if (rev > 0) cur.revs + (saved.id to rev) else cur.revs - saved.id)
        }
        null
      }
      is ApiResult.Failure -> r.error.userMessage
    }
  }

  suspend fun delete(context: Context, id: String): String? {
    val key = ensure(context)
    return when (val r = repository(context).delete(id)) {
      is ApiResult.Success -> { publish(context, key) { it.copy(rules = it.rules.filterNot { x -> x.id == id }, revs = it.revs - id) }; null }
      is ApiResult.Failure -> r.error.userMessage
    }
  }

  /**
   *  تازه‌سازیِ آرام — یک بار در `AppRoot`؛ فقط وقتی برنامه جلوی چشم است.
   *  @param cartStore پیش‌نویسِ فاکتور: کدهای زده‌شده با آن می‌مانند و از آن برمی‌گردند (N3)
   */
  @Composable
  fun Host(cartStore: CartStore) {
    val context = LocalContext.current
    var foreground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { foreground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foreground = false }
    LaunchedEffect(cartStore) {
      draft = cartStore
      loadCache(context)
      //  پیش‌نویس خالی شد (فروش ثبت شد، «سبدِ تازه»، بازیابی رد شد) یا برگشت ⇒ همان کدها
      cartStore.coupons.collect { if (it != coupons.value) coupons.value = it }
    }
    LaunchedEffect(foreground, epoch.intValue) {
      if (!foreground) return@LaunchedEffect
      while (isActive) {
        ensure(context)
        if (System.currentTimeMillis() - bound.triedAt > REFRESH_MS) refresh(context)
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
    val context = LocalContext.current
    //  S1: حالِ حسابِ دیگر هرگز — حتی پیش از آن‌که `ensure` آن را دور بریزد
    val key = account(context)
    LaunchedEffect(key, epoch.intValue) { loadCache(context) }
    val s = if (isDemo) snap.value else PromoEngine.forAccount(snap.value, key)
    val codes = coupons.value
    LaunchedEffect(cart.isEmpty()) { if (cart.isEmpty() && coupons.value.isNotEmpty()) setCoupons(emptyList()) }
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
    bound.loaded = true
    bound.triedAt = System.currentTimeMillis()
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

  /** کدهای سبد ⇒ حافظه و پیش‌نویسِ فاکتور (N3) */
  fun setCoupons(codes: List<String>) {
    //  وسطِ جابه‌جاییِ فاکتور (POS-12) سبد قفل است ⇒ کد هم نمی‌نشیند
    if (draft?.setCoupons(codes) == false) return
    coupons.value = codes
  }

  /** افزودنِ کدی که صندوقدار زد (نرمال‌شده، بی تکرار) */
  fun addCoupon(code: String) {
    val c = PromoCore.normalizeCode(code)
    if (c.isNotEmpty() && c !in coupons.value) setCoupons(coupons.value + c)
  }

  fun removeCoupon(code: String) = setCoupons(coupons.value - code)
}
