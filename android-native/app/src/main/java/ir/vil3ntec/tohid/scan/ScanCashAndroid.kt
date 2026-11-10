package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanSaleRule
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.CashRoundPref
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ScanCashSale
import ir.vil3ntec.tohid.data.ScanSaleHold
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.data.TrainingMode
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.PromoLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/**
 *  «فروشِ نقدی با کارتِ فرمان» — مرزِ پلتفرم (منطق: `data/ScanCashSale.kt` و `core/hw/ScanSale.kt`).
 *
 *  تنظیمِ **همین دستگاه** (`tohid-ui` ⇐ `scan_cash_card`)، دستهٔ B، **پیش‌فرض خاموش**؛ فقط صاحب یا مدیر عوضش می‌کند و
 *  روشن کردنش تأیید می‌خواهد. رازِ کارت (۳۲ بایتِ `SecureRandom`) و نسلش همین‌جا می‌ماند و هرگز همگام یا فرستاده
 *  نمی‌شود؛ «کارتِ تازه» هر دو را عوض می‌کند و کارت‌های پیشین باطل می‌شوند.
 */
object ScanCashPref {
  private const val PREFS = "tohid-ui"
  const val KEY = "scan_cash_card"
  private const val SECRET = "scan_cash_secret"
  private const val GEN = "scan_cash_gen"

  val on = mutableStateOf(false)

  /** نسلِ کارتِ کنونی؛ ۰ ⇒ هنوز کارتی ساخته نشده */
  val gen = mutableStateOf(0)

  @Volatile private var loaded = false

  private fun prefs(context: Context) = (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    runCatching {
      val p = prefs(context)
      on.value = p.getBoolean(KEY, false)
      gen.value = p.getInt(GEN, 0)
    }
  }

  /** فقط صاحب یا مدیر (نقشِ کاربرِ صندوق هرگز بالاتر از حسابِ دستگاه) */
  fun canManage(context: Context): Boolean = ShopRole.current(context).let { it == ShopRole.OWNER || it == ShopRole.MANAGER }

  /** روشن ⇒ اگر کارتی نیست همان دم ساخته می‌شود (بی راز هیچ فرمانی پذیرفته نمی‌شود) */
  fun set(context: Context, value: Boolean) {
    load(context)
    if (value && !canManage(context)) return
    on.value = value
    runCatching { prefs(context).edit().putBoolean(KEY, value).apply() }
    if (value && key(context) == null) rotate(context)
    if (value) ScanCashRun.kick(context)
  }

  /** رازِ کنونی؛ `null` ⇒ هنوز کارتی نیست */
  fun key(context: Context): CommandCard.Key? = runCatching {
    val p = prefs(context)
    val s = p.getString(SECRET, null) ?: return@runCatching null
    CommandCard.Key(s, p.getInt(GEN, 0)).takeIf { it.usable }
  }.getOrNull()

  /** کارتِ تازه: رازِ تازه، نسلِ بعدی — همهٔ کارت‌های چاپ‌شدهٔ پیشین باطل */
  @Synchronized fun rotate(context: Context): CommandCard.Key {
    val random = ByteArray(CommandCard.SECRET_BYTES).also { SecureRandom().nextBytes(it) }
    val next = CommandCard.rotate(key(context), random)
    prefs(context).edit().putString(SECRET, next.secret).putInt(GEN, next.gen).commit()
    gen.value = next.gen
    return next
  }

  /** فرمانِ معتبرِ همین دستگاه برای یک کد — برای پرسشِ «N اسکن در انتظار» با کارتِ روشن */
  fun commandOf(context: Context): (String) -> CommandCard.Command? {
    load(context)
    val k = if (on.value) key(context) else null
    return { code -> CommandCard.parse(code, k) }
  }
}

/**
 *  کارگرِ پس‌زمینه: پس از هر اسکنی که در صف نشست (`DeviceLinks`، زنده یا بستهٔ حافظه) و با بالا آمدنِ کارگرها،
 *  فاکتورهای «نقد»‌خورده را ثبت یا نگه‌داشته می‌کند (`ScanCashSale.plan`). یکی در هر لحظه (`Mutex`)؛ ترتیب همان
 *  ترتیبِ صف. هر فروش جدا زیرِ قفلِ نوشتنِ دفتر (`ShopStore.update`) روی دفترِ همان لحظه؛ اسکن‌ها فقط **پس از**
 *  نوشتنِ دفتر یا نشستنِ فاکتورِ نگه‌داشته برداشته می‌شوند — قطعِ برق میانِ این دو با شناسهٔ پایدار دوباره ثبت نمی‌کند.
 */
object ScanCashRun {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()

  /** نوعِ ردیفِ سابقه برای کدِ فرمانِ نادرست یا کهنه */
  const val IGNORED = "scan_command_ignored"

  /** یادداشتِ همان ردیف — **داده**، همان در هر زبان */
  private const val IGNORED_NOTE = "کدِ فرمانِ نادرست از بارکدخوان نادیده ماند"

  /** آخرین کار — کارتِ «دستگاه‌ها» نشانش می‌دهد */
  val last = mutableStateOf<String?>(null)

  fun kick(context: Context) {
    val app = context.applicationContext ?: context
    ScanCashPref.load(app)
    if (!ScanCashPref.on.value) return
    scope.launch { runCatching { run(app) } }
  }

  /** کاربرِ صندوقِ همین دستگاه اجازهٔ فروش دارد — همان `canWrite("sales")` ِ صندوق؛ نقشِ ناشناس، پیک، فقط‌خواندنی یا صندوقِ قفل ⇒ نه */
  fun canSell(context: Context): Boolean {
    if (ShopRole.isCourier(context) || ShopRole.isReadOnly(context)) return false
    TillUser.load(context)
    if (TillUser.locked.value) return false
    val role = ShopRole.current(context)
    return PermCore.canWrite(role, "sales") && PermCore.canWrite(role, "saleItems")
  }

  private fun why(w: ScanSaleRule.Why, unknown: List<String>): String = when (w) {
    ScanSaleRule.Why.TILL_CLOSED -> StrDevices.whyTillClosed
    ScanSaleRule.Why.NO_PERMISSION -> StrDevices.whyNoPermission
    ScanSaleRule.Why.UNKNOWN_CODE -> StrDevices.whyUnknown(unknown.take(3).joinToString("، "))
    ScanSaleRule.Why.LONG_GAP -> StrDevices.whyLongGap
    //  به این‌جا نمی‌رسند (`ScanCashSale.plan` فقط فاکتورِ «نقد»‌خورده با کارتِ روشن)
    ScanSaleRule.Why.CARD_OFF, ScanSaleRule.Why.NOT_PAID -> StrDevices.cashCardTitle
  }

  /**
   *  تخفیفِ خودکارِ دکان برای همین سبد — همان `PromoEngine.evaluate` ِ صندوق (`PromoLive.rememberOutcome`) با حالِ
   *  ذخیره‌شدهٔ همین حساب، بی کدِ تخفیف (کدِ سقف‌دار پرسشِ سرور می‌خواهد). کلیدِ دکان خاموش ⇒ `null`.
   */
  private fun promoNow(app: Context, d: ShopData, cart: List<SalesEngine.CartLine>, today: String): PromoEngine.Outcome? = runCatching {
    PromoLive.loadCache(app)
    val snap = PromoEngine.forAccount(PromoLive.snap.value, PromoLive.ensure(app))
    val c = java.util.Calendar.getInstance()
    PromoEngine.evaluate(d, cart, snap, emptyList(), today, c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE))
  }.getOrNull()

  private suspend fun park(app: Context, item: HeldInvoice): Boolean =
    HeldInvoices.of(app).park(item) is HeldInvoiceStore.Outcome.Done

  suspend fun run(app: Context) = lock.withLock {
    ScanCashPref.load(app)
    //  OPS-07: حالتِ آموزشی ⇒ دفترِ واقعی دست نمی‌خورد؛ اسکن‌ها برای پرسش می‌مانند
    if (!ScanCashPref.on.value || TrainingMode.on) return@withLock
    val key = ScanCashPref.key(app) ?: return@withLock
    val pending = ScanInbox.load(app).pending()
    if (pending.isEmpty()) return@withLock
    val store = ShopStore(app)
    store.load()
    if (!store.loaded.value || store.unreadable.value != null) return@withLock
    val d = store.data.value
    val gate = ScanSaleRule.Gate(cardOn = true, tillOpen = CashShift.current(d) != null, canSell = canSell(app))
    val plan = ScanCashSale.plan(d, pending, gate) { CommandCard.parse(it, key) }
    if (plan.record.isEmpty() && plan.hold.isEmpty() && plan.drop.isEmpty()) return@withLock

    val till = ScanCashSale.Till(TillUser.me(app), CashRoundPref.step(app), ShopPolicy.blockExpired(app), ir.vil3ntec.tohid.data.SaleTaxPref.active(app))
    val series = runCatching { ir.vil3ntec.tohid.sync.DocSeries.current(app) }.getOrDefault("")
    val taken = LinkedHashSet(plan.drop)
    var recorded = 0
    var held = 0
    for (r in plan.record) {
      val now = System.currentTimeMillis()
      val today = ir.vil3ntec.tohid.todayIso()
      var refused: String? = null
      var already = false
      var after: ShopData? = null
      val token = ir.vil3ntec.tohid.print.Receipts.newToken(app)
      val wrote = store.update { cur ->
        when (val res = ScanCashSale.record(cur, r, till, today, now, { Ulid.next() }, series, token) { cart -> promoNow(app, cur, cart, today) }) {
          is SalesEngine.Result.Ok -> { after = res.data; res.data }
          is SalesEngine.Result.Failed -> { if (res.message == SalesEngine.ALREADY_POSTED) already = true else refused = res.message; null }
        }
      }
      val data = after
      when {
        wrote == true && data != null -> {
          taken += r.scanIds
          recorded++
          //  رسید پس از نوشتن و جدا از آن (چاپگرِ خاموش ثبت را نگه نمی‌دارد)
          runCatching { ir.vil3ntec.tohid.print.PrintSpooler.afterSale(app, data, r.id, receipt = true) }
        }
        already -> taken += r.scanIds
        else -> {
          //  ردِ صندوق (موجودی، کالای غیرفعال، منقضی، قفلِ نرم، دیسک) ⇒ راهِ «الف»
          val note = StrDevices.heldNote(refused?.let(StrDevices::whyRefused) ?: StrDevices.whyNotSaved)
          val item = HeldInvoice(r.id, 0, ScanCashSale.HELD_LABEL, r.cart.map { SaleDraft.Line(it.productId, it.quantity) }, note = note, heldAt = now)
          if (park(app, item)) { taken += r.scanIds; held++ }
        }
      }
    }
    for (h in plan.hold) {
      val inv = h.invoice ?: continue
      val item = ScanSaleHold.held(inv, System.currentTimeMillis(), ScanCashSale.HELD_LABEL, StrDevices.heldNote(why(h.why, h.unknown)))
      if (park(app, item)) { taken += h.scanIds; held++ }
    }
    if (recorded > 0) runCatching { ir.vil3ntec.tohid.sync.AutoSync.now(app, store) }
    logIgnored(app, store, plan.ignored.filter { it.id in taken }, till.seller)
    ScanInbox.take(taken)
    last.value = StrDevices.lastRun(recorded, held) +
      plan.ignored.count { it.id in taken }.let { n -> if (n > 0) " · " + StrDevices.ignoredCodes(n) else "" }
  }

  /** کدِ فضای‌نامِ فرمان که نادیده ماند ⇒ یک ردیفِ سابقه (کهنه یا نادرست، و چند تا) — فقط نقشی که سابقه می‌نویسد */
  private suspend fun logIgnored(app: Context, store: ShopStore, scans: List<QueuedScan>, by: String) {
    if (scans.isEmpty() || !ShopRole.canWrite(app, "auditLog")) return
    val key = ScanCashPref.key(app)
    val old = scans.count { CommandCard.read(it.code, key).verdict == CommandCard.Verdict.OLD_CARD }
    val now = System.currentTimeMillis()
    runCatching {
      store.appendAudit {
        AuditEntry(
          id = Ulid.next(), type = IGNORED, date = ir.vil3ntec.tohid.todayIso(), createdAt = now,
          notes = "$IGNORED_NOTE — ${scans.size} کد" + (if (old > 0) "، $old از کارتِ کهنه" else ""),
          by = by.trim().ifEmpty { null },
        )
      }
    }
  }
}
