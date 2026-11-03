package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.safeErrorText
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  کادرِ بالای خانه — مرزِ دستگاه (تنظیم، نقش، ساعت، نامِ حساب، دفتر). منطق در `HomeHero`/`HeroMoney` (خالص و مشترک)؛
 *  این‌جا فقط خواندن و ثبت از همان راه‌های امروز.
 */

/** هرچه کادر از دستگاه می‌خواهد — یک بار برای هر دفتر و هر ساعت */
data class HeroInput(
  val today: String,
  /** آغازِ روزِ کاریِ امروز (نیمه‌شبِ محلیِ همان تاریخ) */
  val dayStartMs: Long,
  val nowMs: Long,
  /** ساعتِ همین حالا از آغازِ روز — کلیدِ تازه شدنِ نمودارِ «امروز» */
  val hour: Int,
  /** صفحهٔ «گاوصندوق»: `null` یعنی نیست (تنظیمِ خاموش یا بی مجوز — همان سدِ `SafePanel`) */
  val safe: SafeCore.View?,
  /** شاگردِ صندوقِ شیفت با شمارشِ کور (PAY-13، همان `DrawerRoute`): نقدِ موردِ انتظار دیده نمی‌شود */
  val blind: Boolean,
)

/** گاوصندوق برای همین کاربر: تنظیمِ همین دستگاه (`SafeBookPref`) + `safe.move` + نوشتنِ `safeEntries` */
private fun safeAllowed(context: android.content.Context): Boolean =
  SafeBookPref.on(context) && ShopRole.allows(context, "safe.move") && ShopRole.canWrite(context, "safeEntries")

@Composable
fun rememberHeroInput(d: ShopData): HeroInput {
  val context = LocalContext.current
  val today = todayIso()
  val safeOn = remember { safeAllowed(context) }
  val blind = remember { ShopPolicy.payV2(context) && ShopPolicy.blindHere(context) }
  //  هر دقیقه یک تیک؛ ورودی فقط با عوض شدنِ ساعت (یا دفتر) از نو ساخته می‌شود
  var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000)
      tick = System.currentTimeMillis()
    }
  }
  val start = remember(today) { isoMillis(today).let { if (it > 0L) it - 12 * HomeHero.HOUR_MS else tick } }
  val hour = HomeHero.hourOf(tick, start)
  val safe = remember(d, safeOn) { if (safeOn) SafeBook.view(d) else null }
  return remember(today, start, hour, safe, blind) { HeroInput(today, start, System.currentTimeMillis(), hour, safe, blind) }
}

/** خطای سه‌زبانهٔ ثبتِ صندوق؛ کدِ دیگر ⇒ همان پیامِ `PayFlow` ِ برگهٔ صندوق */
private fun tillError(p: PayFlow.Problem): String = when (p.code) {
  "INSUFFICIENT_CASH" -> StrSafe.insufficientCash
  "NOTE_REQUIRED" -> StrSafe.noteRequired
  "REASON_REQUIRED" -> StrSafe.reasonRequired
  "BAD_REASON" -> StrSafe.badReason
  HeroMoney.INVALID_AMOUNT -> StrSafe.invalidAmount
  HeroMoney.SHIFT_CLOSED -> StrSafe.shiftClosed
  else -> PayFlow.message(p)
}

/**
 *  `home/money?page=till|safe` — برگهٔ «+». شناسهٔ ردیف هنگامِ باز شدن ساخته می‌شود (`key`) ⇒ دو ضربه یا تلاشِ دوباره
 *  یک ثبت است. صندوق: `HeroMoney.tillRow` روی دفترِ **همان لحظهٔ نوشتن** زیرِ قفلِ نوشتن (`ShopStore.appendAudits`،
 *  همان دری که برگهٔ «ورود/خروجِ نقد» با `appendAudit` می‌رود) و ردیفی با همان شناسه دوباره نوشته نمی‌شود.
 *  گاوصندوق: `SafeBookPref.record` (همان ثبتِ پنجرهٔ گاوصندوق: دفترِ دیسک، `AppendOnce`).
 */
@Composable
fun RouteScope.HeroMoneyRoute() {
  val context = LocalContext.current
  val page = if (args.query("page") == "safe") HomeHero.Page.SAFE else HomeHero.Page.TILL
  val allowed = remember { page == HomeHero.Page.TILL || safeAllowed(context) }
  if (!allowed) {
    LaunchedEffect(Unit) { back() }
    return
  }
  val me = remember { runCatching { SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { Ulid.next() }
  val scope = rememberCoroutineScope()
  val shift = remember(data) { CashShift.current(data) }
  val closed = page == HomeHero.Page.TILL && shift == null
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  HeroMoneySheet(
    page = page,
    shiftClosed = closed,
    busy = busy,
    error = error,
    onSubmit = submit@{ dir, text, reason, note ->
      if (busy) return@submit
      val minor = when (val a = HeroMoney.amount(text)) {
        is HeroMoney.Amount.Bad -> {
          error = if (a.code == HeroMoney.AMOUNT_REQUIRED) StrSafe.amountRequired else StrSafe.invalidAmount
          return@submit
        }
        is HeroMoney.Amount.Ok -> a.minor
      }
      busy = true
      error = null
      scope.launch {
        val now = System.currentTimeMillis()
        val result: Pair<String?, Boolean> = if (page == HomeHero.Page.TILL) {
          var w: HeroMoney.TillWrite? = null
          val ok = store.appendAudits { cur -> HeroMoney.tillWrite(cur, dir, minor, reason, note, me, now, todayIso(), key).also { w = it }.rows }
          val bad = w?.problem
          when {
            bad != null -> tillError(bad) to false
            !ok || w == null -> StrSafe.notSaved to false
            else -> null to w!!.again
          }
        } else {
          when (val r = SafeBookPref.record(store, key) { cur, k -> HeroMoney.safeEntry(cur, dir, minor, reason.orEmpty(), note, me, now, k) }) {
            is SafeBookPref.Saved.Ok -> null to r.again
            is SafeBookPref.Saved.Bad -> safeErrorText(r.failed) to false
            SafeBookPref.Saved.NotSaved -> StrSafe.notSaved to false
          }
        }
        busy = false
        val (message, again) = result
        if (message == null) {
          toasts.show(if (again) StrSafe.already else StrSafe.saved, V3Tone.SUCCESS)
          back()
        } else error = message
      }
    },
    onOpenDrawer = {
      back()
      nav.open(SalesPaths.DRAWER_OPEN)
    },
    onClose = { back() },
  )
}
