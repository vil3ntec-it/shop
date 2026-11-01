package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.i18n.StrStaffTime
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.core.staff.TimeClock
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StaffTimeRepository
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  فایلِ مرزیِ «ورود و خروج» و «پورسانت» (STF-07، STF-08): شبکه، ساعتِ دستگاه و صفِ آفلاین این‌جاست؛
 *  قاعده‌ها در هسته (`core/staff/TimeClock`، `core/staff/Commission`) و نمایش در `StaffTimeCard`.
 *
 *  ⛔ پیش‌فرض خاموش: تا سرور `enabled` ِ پرچمِ `shop.staff_time` را نگوید، هیچ چیزی کشیده نمی‌شود
 *     و «مدیریت» بیت‌به‌بیت همان پیشین است. بی حساب یا بی اینترنت هم چیزی کشیده نمی‌شود (پرچم نامعلوم است).
 *  ⛔ زدنی که به سرور نرسید (بی اینترنت) با همان شناسه در صف (`tohid-staff-time`) می‌ماند و بارِ بعد
 *     فرستاده می‌شود؛ سرور تکرار را یک بار می‌شمارد. ردِ قطعیِ سرور (۴۰۹) صف را پاک می‌کند و پیامش دیده می‌شود.
 *  ⛔ پورسانت از فاکتورهای لغونشدهٔ این ماهِ همین دفتر (فروشندهٔ فاکتور، POS-23) با نرخِ فروشنده یا نرخِ همه
 *     حساب می‌شود؛ نرخِ دسته در سرور نگه داشته می‌شود ولی تا فروشنده و دسته برای هر ردیف نیامده، این‌جا به کار
 *     نمی‌رود. برگشت هنوز کم نمی‌شود (همان کارتِ «فروشنده‌ها»)؛ `Commission` آماده‌اش است.
 */
/**
 *  پرچمِ `shop.staff_time` ِ همین دکان از `/config`؛ تا جواب نیامده، بی حساب یا بی اینترنت ⇒ `null`.
 *  «مدیریت» فقط با `enabled` کارت را به فهرست می‌افزاید، تا چیدمانِ ستون‌ها با پرچمِ خاموش عوض نشود.
 */
@Composable
fun rememberStaffTimeConfig(): StaffTimeRepository.Config? {
  val context = LocalContext.current
  var cfg by remember { mutableStateOf<StaffTimeRepository.Config?>(null) }
  LaunchedEffect(Unit) {
    if (!Backend.isReady(context)) return@LaunchedEffect
    cfg = (StaffTimeRepository(Backend.api(context)).config() as? ApiResult.Success)?.value
  }
  return cfg
}

@Composable
fun StaffTimeHub(d: ShopData, monthFrom: String, today: String, initial: StaffTimeRepository.Config) {
  val context = LocalContext.current
  val repo = remember { StaffTimeRepository(Backend.api(context)) }
  val scope = rememberCoroutineScope()
  var cfg by remember { mutableStateOf<StaffTimeRepository.Config?>(initial) }
  var punches by remember { mutableStateOf<List<StaffTimeRepository.Named>>(emptyList()) }
  var plan by remember { mutableStateOf<StaffTimeRepository.PlanDoc?>(null) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var queued by remember { mutableStateOf(StaffTimeQueue.size(context)) }

  suspend fun reload() {
    val c = (repo.config() as? ApiResult.Success)?.value ?: return
    cfg = c
    if (!c.enabled) return
    StaffTimeQueue.flush(context, repo)?.let { message = it }
    queued = StaffTimeQueue.size(context)
    val now = System.currentTimeMillis()
    (repo.punches(now - STAFF_WINDOW_MS, now) as? ApiResult.Success)?.value?.let { punches = it }
    if (c.role == "owner" || c.role == "manager") (repo.plan() as? ApiResult.Success)?.value?.let { plan = it }
  }
  LaunchedEffect(Unit) { reload() }

  val c = cfg ?: return
  if (!c.enabled) return
  val manager = c.role == "owner" || c.role == "manager"
  val now = System.currentTimeMillis()
  val offset = ReportBridge.offsetMinutes(now)
  val dayStart = ShopDay.dayStartMinutes(ShopDayChoice.hours)
  val names = punches.associate { it.punch.staffId to it.name }
  val spans = TimeClock.spans(punches.map { it.punch } + StaffTimeQueue.pending(context, c.memberId), now, offset, dayStart)
  val todays = spans.filter { it.businessDate == today }
  val mine = spans.lastOrNull { it.staffId == c.memberId }
  val myOpen = mine?.takeIf { it.issue == TimeClock.Issue.OPEN }?.startMs

  //  همان فاکتورهای «فروشنده‌ها»ی این ماه (`ReportEngine.bySeller`)، ولی فاکتورِ بی فروشنده جدا می‌ماند
  val result = if (!manager) null else plan?.let { p ->
    val lines = d.sales.filter { it.status != "cancelled" && it.date >= monthFrom && it.date <= today }
      .map { Commission.Line(it.seller.trim(), null, Money.fromLegacy(it.finalTotal, Currency.AFN)) }
    Commission.compute(lines, p.plan, Currency.AFN)
  }

  fun punch(forMember: String?, kind: TimeClock.Kind) {
    busy = true
    scope.launch {
      val p = TimeClock.Punch(newId(), forMember ?: c.memberId, kind, System.currentTimeMillis(), SyncStore(context).deviceUid)
      val known = punches.map { it.punch } + StaffTimeQueue.pending(context, p.staffId)
      message = when (TimeClock.check(known, p)) {
        TimeClock.Rejection.SAME_KIND, TimeClock.Rejection.DUPLICATE -> StrStaffTime.sameKind
        TimeClock.Rejection.BEFORE_LAST -> StrStaffTime.beforeLast
        else -> when (val r = repo.punch(p, forMember?.takeIf { it != c.memberId })) {
          is ApiResult.Success -> null
          is ApiResult.Failure ->
            if (r.error.retryable && forMember == null) {
              StaffTimeQueue.add(context, p)
              queued = StaffTimeQueue.size(context)
              null
            } else staffRejection(r.error.code) ?: r.message
        }
      }
      reload()
      busy = false
    }
  }

  StaffTimeCard(
    ui = StaffTimeUi(
      myOpenSince = myOpen?.let { formatMillis(it) },
      queued = queued,
      busy = busy,
      message = message,
      manager = manager,
      team = if (!manager) emptyList() else todays.groupBy { it.staffId }.map { (id, list) ->
        val mins = list.sumOf { it.minutes }
        TeamRow(id, names[id].orEmpty().ifBlank { id }, StrStaffTime.hoursMinutes(mins / 60, mins % 60), list.any { it.issue == TimeClock.Issue.OPEN })
      },
      issues = if (manager) todays.count { it.issue != null && it.issue != TimeClock.Issue.OPEN } else 0,
      commission = result?.sellers?.sortedByDescending { it.commission.minor }?.map { SellerRow(it.sellerId, money(it.base.minor / 100.0), "${money(it.commission.minor / 100.0)} ؋") },
      unassigned = result?.unassignedBase?.takeIf { it.minor != 0L }?.let { "${money(it.minor / 100.0)} ؋" },
      plan = if (manager) plan?.plan else null,
    ),
    onPunch = { punch(null, if (myOpen != null) TimeClock.Kind.OUT else TimeClock.Kind.IN) },
    onClockOutFor = { row -> punch(row.memberId, TimeClock.Kind.OUT) },
    onSaveRates = { p ->
      val doc = plan ?: return@StaffTimeCard
      busy = true
      scope.launch {
        message = when (val r = repo.savePlan(p, doc.version)) {
          is ApiResult.Success -> { plan = r.value; StrStaffTime.saved }
          is ApiResult.Failure -> if (r.error.code == "commission_version") StrStaffTime.stale else r.message
        }
        busy = false
      }
    },
  )
}

/** نوبت‌های ۳۶ ساعتِ گذشته — نوبتِ شبانه‌ای که دیروز شروع شده هم دیده می‌شود */
private const val STAFF_WINDOW_MS = 36L * 60 * 60 * 1000

private fun staffRejection(code: String): String? = when (code) {
  "punch_same_kind" -> StrStaffTime.sameKind
  "punch_before_last", "punch_in_future", "punch_too_old" -> StrStaffTime.beforeLast
  else -> null
}

/** صفِ زدن‌های نرسیده — `SharedPreferences` ِ `tohid-staff-time`، فقط روی همین دستگاه */
private object StaffTimeQueue {
  private const val PREFS = "tohid-staff-time"
  private const val KEY = "queue"
  private val json = Json { ignoreUnknownKeys = true }

  private fun read(context: Context): List<TimeClock.Punch> = runCatching {
    (json.parseToJsonElement(context.getSharedPreferences(PREFS, 0).getString(KEY, "[]") ?: "[]") as JsonArray).mapNotNull { e ->
      val o = e as? JsonObject ?: return@mapNotNull null
      fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
      val kind = TimeClock.Kind.entries.firstOrNull { it.code == s("kind") } ?: return@mapNotNull null
      TimeClock.Punch(s("id"), s("staffId"), kind, (o["at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null, s("deviceId"))
    }
  }.getOrDefault(emptyList())

  private fun write(context: Context, list: List<TimeClock.Punch>) {
    val arr = buildJsonArray {
      list.forEach { p -> add(buildJsonObject { put("id", p.id); put("staffId", p.staffId); put("kind", p.kind.code); put("at", p.atMs); put("deviceId", p.deviceId) }) }
    }
    context.getSharedPreferences(PREFS, 0).edit().putString(KEY, arr.toString()).apply()
  }

  fun size(context: Context): Int = read(context).size

  fun pending(context: Context, memberId: String): List<TimeClock.Punch> = read(context).filter { it.staffId == memberId }

  fun add(context: Context, p: TimeClock.Punch) = write(context, read(context) + p)

  /** به ترتیبِ زمان؛ نرسیدن ⇒ می‌ماند و بقیه هم می‌مانند (ترتیب مهم است). ⇒ پیامِ ردِ قطعی، اگر بود */
  suspend fun flush(context: Context, repo: StaffTimeRepository): String? {
    val list = read(context).sortedBy { it.atMs }
    var rest = list
    var note: String? = null
    for (p in list) {
      when (val r = repo.punch(p)) {
        is ApiResult.Success -> rest = rest - p
        is ApiResult.Failure -> {
          if (r.error.retryable) break
          rest = rest - p
          note = staffRejection(r.error.code) ?: r.message
        }
      }
    }
    if (rest.size != list.size) write(context, rest)
    return note
  }
}
