package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.data.LoyaltyPoints
import ir.vil3ntec.tohid.data.LoyaltyPref
import ir.vil3ntec.tohid.data.LoyaltyShop
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.launch
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  PRM-13 — امتیازِ وفاداری در صندوقِ گوشی و کامپیوتر (`Sale`، `QuickSale`، `DeskSale`)، پروندهٔ مشتری
 *  (با اصلاحِ دستیِ صاحب/مدیر) و تنظیمات. فقط با قاعدهٔ در کار (`LoyaltyPref.effective`: قاعدهٔ یکسانِ دکان یا همین
 *  دستگاه؛ پیش‌فرض خاموش ⇒ [rememberLoyaltyTill] `null`، سبد و ثبت همان امروز). قاعده‌ها در هستهٔ `core/promo/Loyalty`
 *  و `data/LoyaltyPoints`؛ این‌جا فقط نمایش و انتخابِ «چند امتیاز».
 */
class LoyaltyTill(
  val view: LoyaltyPoints.View,
  val rule: Loyalty.Rule,
  /** بیشترین امتیازِ قابلِ مصرف روی همین سبد */
  val cap: Long,
  /** امتیازِ انتخاب‌شده (تا [cap] بریده) */
  val points: Long,
  private val applied: LoyaltyPoints.Applied?,
  val setPoints: (Long) -> Unit,
) {
  /** سبدِ پس از امتیاز؛ `null` ⇒ بی مصرف (همان سبد) */
  val cart: List<SalesEngine.CartLine>? get() = applied?.cart
  val plan: LoyaltyPoints.Plan? get() = applied?.plan
  /** مبلغِ کم‌شده به افغانی */
  val off: Double get() = PayFlow.major(applied?.plan?.valueMinor ?: 0L)
}

/** خاموش یا بی مشتری ⇒ `null` */
@Composable
fun rememberLoyaltyTill(d: ShopData, priced: List<SalesEngine.CartLine>, debtorId: String?): LoyaltyTill? {
  val context = LocalContext.current
  //  قاعدهٔ یکسانِ دکان (پرچمِ خاموش ⇒ هیچ درخواستی جز همین پرسش، حداکثر هر ۱۰ دقیقه)
  LaunchedEffect(Unit) { LoyaltyShop.kick(context) }
  val cfg = remember(d) { LoyaltyPref.effective(context) }
  if (!cfg.on || debtorId == null) return null
  val rule = cfg.rule
  var points by remember(debtorId) { mutableStateOf(0L) }
  val now = remember(d, debtorId) { System.currentTimeMillis() }
  val view = remember(d, debtorId, rule) { LoyaltyPoints.view(d, rule, debtorId, now) }
  val cap = remember(d, priced, debtorId, rule) { LoyaltyPoints.capFor(d, priced, rule, debtorId, now) }
  val use = points.coerceIn(0, cap)
  val applied = remember(d, priced, debtorId, use, rule) { if (use > 0) LoyaltyPoints.apply(d, priced, rule, debtorId, use, now) else null }
  return LoyaltyTill(view, rule, cap, if (applied == null) 0 else use, applied) { points = it }
}

object LoyaltyAtTill {
  /** ثبتِ صندوق: مصرف و کسبِ همین فاکتور در همان دفتر (`LoyaltyPoints.post`)؛ خاموش ⇒ همان نتیجه */
  fun post(context: android.content.Context, r: SalesEngine.Result, till: LoyaltyTill?, by: String?): SalesEngine.Result {
    if (r !is SalesEngine.Result.Ok) return r
    return points(context, till).post(r, by)
  }

  /** امتیازِ همین فاکتور برای ثبت (`TillSale`): قاعدهٔ در کار (`LoyaltyPref.effective`) و برنامهٔ مصرفِ [till] */
  fun points(context: android.content.Context, till: LoyaltyTill?): ir.vil3ntec.tohid.data.TillSale.Points {
    val cfg = LoyaltyPref.effective(context)
    return ir.vil3ntec.tohid.data.TillSale.Points(cfg.on, till?.rule ?: cfg.rule, till?.plan, till?.cart)
  }

  /**
   *  ردیف‌های خلاصهٔ پرداخت با امتیاز: «جمع» پیش از امتیاز و یک ردیفِ منفیِ «مصرفِ امتیاز». بی مصرف ⇒ همان [rows].
   *  با تخفیفِ خودکار «جمع» همان جمعِ پیش از همه است؛ بی آن جمعِ ردیف‌ها (پس از امتیاز) به پیش از امتیاز برمی‌گردد.
   */
  fun rows(rows: List<PosSummaryRow>, promo: PromoEngine.Outcome?, till: LoyaltyTill?): List<PosSummaryRow> {
    val off = till?.off ?: 0.0
    if (off <= 0 || rows.isEmpty()) return rows
    val withPromo = promo != null && promo.discount > 0
    val head = if (withPromo) rows.take(2) else listOf(rows[0].copy(amount = rows[0].amount + off))
    val tail = if (withPromo) rows.drop(2) else rows.drop(1)
    return head + PosSummaryRow(StrLoyalty.receiptUsed(till!!.points), off, negative = true) + tail
  }
}

/** نوارِ صندوق: مانده و سطحِ مشتری، و «مصرفِ امتیاز» */
@Composable
fun LoyaltyBar(till: LoyaltyTill?, modifier: Modifier = Modifier) {
  if (till == null) return
  var asking by remember { mutableStateOf(false) }
  val v = till.view
  Column(modifier.fillMaxWidth()) {
    Text(
      StrLoyalty.bar(v.balance, LoyaltyPoints.tierName(v.tier.key), LoyaltyPoints.multText(v.tier.multiplierPct)),
      style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted,
    )
    if (v.balance < 0) Text(StrLoyalty.negative, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
    soonText(v.soon, v.soonAt)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (till.points > 0) {
        Text(StrLoyalty.using(till.points, money(till.off)), style = MaterialTheme.typography.bodySmall, color = Shop.colors.success, modifier = Modifier.weight(1f))
        TextButton(onClick = { till.setPoints(0) }) { Text(StrLoyalty.stopUsing) }
      } else {
        OutlinedButton(onClick = { asking = true }, enabled = till.cap > 0) { Text(StrLoyalty.use) }
        if (till.cap <= 0) Text(StrLoyalty.nothingToUse, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    }
  }
  if (asking) {
    var text by remember { mutableStateOf(till.cap.toString()) }
    val n = normalizeDigits(text).trim().toLongOrNull()
    val ok = n != null && n in 1..till.cap
    AlertDialog(
      onDismissRequest = { asking = false },
      title = { Text(StrLoyalty.use) },
      text = {
        OutlinedTextField(
          value = text, onValueChange = { text = it.filter { c -> c.isDigit() }.take(10) },
          label = { Text(StrLoyalty.howMany(till.cap)) }, singleLine = true, isError = !ok,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
      },
      confirmButton = { TextButton(onClick = { till.setPoints(n ?: 0); asking = false }, enabled = ok) { Text(StrLoyalty.apply) } },
      dismissButton = { TextButton(onClick = { asking = false }) { Text(StrLoyalty.cancel) } },
    )
  }
}

/** «۱۲۰ امتیاز تا … می‌سوزد» — بی انقضا یا چیزی نزدیک ⇒ `null` */
internal fun soonText(soon: Long, at: Long?): String? =
  if (soon > 0 && at != null) StrLoyalty.soon(soon, formatDate(isoDay(at))) else null

/** روزِ تقویمیِ دستگاه برای لحظهٔ [at] (`YYYY-MM-DD`) */
private fun isoDay(at: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  return "${c.get(java.util.Calendar.YEAR)}-" + (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0') + "-" +
    c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
}

/**
 *  پروندهٔ مشتری: مانده (با انقضا)، سابقهٔ امتیاز و «اصلاحِ امتیاز» (فقط صاحب/مدیر، با [store]؛ دلیل لازم، فقط‌افزودنی،
 *  هرگز زیرِ صفر — `LoyaltyPoints.adjust`). خاموش و بی ردیف ⇒ هیچ.
 */
@Composable
fun LoyaltyHistory(d: ShopData, debtorId: String, modifier: Modifier = Modifier, store: ShopStore? = null, toast: (String) -> Unit = {}) {
  val context = LocalContext.current
  val cfg = remember(d) { LoyaltyPref.effective(context) }
  val rows = remember(d, debtorId) { LoyaltyPoints.history(d, debtorId) }
  if (!cfg.on && rows.isEmpty()) return
  val now = remember(d, debtorId) { System.currentTimeMillis() }
  val view = remember(d, debtorId, cfg) { LoyaltyPoints.view(d, cfg.rule, debtorId, now) }
  val may = remember { !ShopRole.isStaff(context) }
  var adjusting by remember(debtorId) { mutableStateOf(false) }
  Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(StrLoyalty.historyTitle(view.balance), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
      if (store != null && may) TextButton(onClick = { adjusting = true }) { Text(StrLoyalty.adjust) }
    }
    soonText(view.soon, view.soonAt)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning) }
    if (rows.isEmpty()) Text(StrLoyalty.empty, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    rows.take(HISTORY_SHOWN).forEachIndexed { i, r ->
      if (i > 0) HorizontalDivider(color = Shop.colors.border)
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(LoyaltyPoints.rowText(r), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          Text(formatDate(r.date), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        Text(
          (if (r.signed > 0) "+" else "") + r.signed,
          style = MaterialTheme.typography.bodyMedium,
          color = if (r.signed >= 0) Shop.colors.success else Shop.colors.warning,
        )
      }
    }
  }
  if (adjusting && store != null) LoyaltyAdjustDialog(store, debtorId, cfg.rule, may, toast) { adjusting = false }
}

/** «اصلاحِ امتیاز»: افزودن/کم‌کردن با دلیل؛ یک شناسهٔ کار برای هر بازشدن (دو ضربه ⇒ یک ردیف) */
@Composable
private fun LoyaltyAdjustDialog(store: ShopStore, debtorId: String, rule: Loyalty.Rule, may: Boolean, toast: (String) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val opId = remember { ir.vil3ntec.tohid.sync.v1.Ulid.next() }
  var add by remember { mutableStateOf(true) }
  var text by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val n = normalizeDigits(text).trim().toLongOrNull()
  val ok = n != null && n in 1..Loyalty.MAX_POINTS && reason.isNotBlank() && !busy
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(StrLoyalty.adjust) },
    text = {
      Column {
        SegmentedChoice(
          options = listOf("add" to StrLoyalty.adjustAdd, "remove" to StrLoyalty.adjustRemove),
          selected = if (add) "add" else "remove",
          onSelect = { add = it == "add" },
        )
        OutlinedTextField(
          value = text, onValueChange = { text = it.filter { c -> c.isDigit() }.take(10) },
          label = { Text(StrLoyalty.adjustPoints) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.take(LoyaltyPoints.REASON_MAX) },
          label = { Text(StrLoyalty.adjustReason) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning) }
      }
    },
    confirmButton = {
      TextButton(enabled = ok, onClick = {
        val delta = if (add) n!! else -n!!
        busy = true
        scope.launch {
          var out: LoyaltyPoints.Adjusted? = null
          val by = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
          val wrote = store.update { cur ->
            LoyaltyPoints.adjust(cur, debtorId, delta, reason, opId, rule, may, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), by)
              .also { out = it }.let { a -> (a as? LoyaltyPoints.Adjusted.Ok)?.takeIf { !it.again }?.data }
          }
          busy = false
          when (val a = out) {
            is LoyaltyPoints.Adjusted.Bad -> error = a.message
            is LoyaltyPoints.Adjusted.Ok -> if (wrote == false) error = StrLoyalty.errChanged else { toast(StrLoyalty.adjustDone); onClose() }
            null -> error = StrLoyalty.errChanged
          }
        }
      }) { Text(StrLoyalty.apply) }
    },
    dismissButton = { TextButton(onClick = onClose) { Text(StrLoyalty.cancel) } },
  )
}

private const val HISTORY_SHOWN = 30

/** تنظیمات: روشن/خاموش (همین دستگاه) و قاعده (فقط صاحب یا مدیر) */
@Composable
fun LoyaltySettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(LoyaltyPref.on(context)) }
  val may = remember { !ShopRole.isStaff(context) }
  val rule0 = remember { LoyaltyPref.rule(context) }
  var every by remember { mutableStateOf(rule0.everyAfn.toString()) }
  var point by remember { mutableStateOf(rule0.pointAfn.toString()) }
  var share by remember { mutableStateOf(rule0.maxSharePct.toString()) }
  var silver by remember { mutableStateOf(rule0.silverAfn.toString()) }
  var gold by remember { mutableStateOf(rule0.goldAfn.toString()) }
  var note by remember { mutableStateOf<String?>(null) }
  SettingsSection(
    icon = Icons.Filled.Loyalty,
    title = StrLoyalty.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrLoyalty.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; LoyaltyPref.set(context, on) },
    )
    Spacer(Modifier.height(8.dp))
    Text(StrLoyalty.tiersNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    if (!may) Text(StrLoyalty.ownerOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    LoyaltyField(StrLoyalty.everyAfn, every, may) { every = it }
    LoyaltyField(StrLoyalty.pointAfn, point, may) { point = it }
    LoyaltyField(StrLoyalty.maxShare, share, may) { share = it }
    LoyaltyField(StrLoyalty.silverAfn, silver, may) { silver = it }
    LoyaltyField(StrLoyalty.goldAfn, gold, may) { gold = it }
    if (may) OutlinedButton(onClick = {
      val r = Loyalty.Rule(
        everyAfn = normalizeDigits(every).toLongOrNull() ?: 0, pointAfn = normalizeDigits(point).toLongOrNull() ?: 0,
        maxSharePct = normalizeDigits(share).toIntOrNull() ?: 0, silverAfn = normalizeDigits(silver).toLongOrNull() ?: 0,
        goldAfn = normalizeDigits(gold).toLongOrNull() ?: 0,
      )
      note = if (LoyaltyPref.setRule(context, r)) StrLoyalty.saved else StrLoyalty.badRule
    }, modifier = Modifier.padding(top = 8.dp)) { Text(StrLoyalty.save) }
    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
    LoyaltyShopSettings(context) // انقضا و قاعدهٔ یکسانِ دکان (پرچمِ خاموشِ `shop.loyalty_rule` ⇒ فقط انقضای همین دستگاه)
  }
}

@Composable
private fun LoyaltyField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(13)) }, label = { Text(label) },
    singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  )
}
