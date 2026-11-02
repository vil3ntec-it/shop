package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.GiftCards
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.GiftCardPrint
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  PAY-24 — کارتِ هدیه در صندوقِ گوشی و کامپیوتر (یک خط کنارِ `SpecialOrderBar` در `Sale` و `DeskSale`):
 *  «فروشِ کارتِ هدیه» و «ماندهٔ کارتِ هدیه». فقط با تنظیمِ همین دستگاه (`GiftCardPref`، پیش‌فرض خاموش ⇒ هیچ). پرداخت با
 *  کارت و اعتبار در پنلِ پرداختِ ترکیبی است (`PaySplitSection`)؛ این‌جا هیچ فاکتوری ثبت نمی‌شود. فروشِ کارت فقط با
 *  `GiftCardPref.issue` روی دفترِ همین لحظهٔ دیسک و کلیدی که پنجره هنگامِ باز شدن می‌سازد؛ فقط نقشی که فاکتور می‌نویسد.
 *  PRM-14: در «مانده» شارژِ دوباره (همان نقش و همان راه، `GiftCardPref.recharge`)، باطل با دلیل (فقط با `sales.cancel`،
 *  `GiftCardPref.void`) و چاپِ کارت (`GiftCardPrint`)؛ «بدهیِ کارت‌ها» فقط برای نقشی که عددِ مالی می‌بیند.
 */
@Composable
fun GiftCardBar(d: ShopData, store: ShopStore, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { GiftCardPref.on(context) }
  if (!on) return
  val canSell = remember { ShopRole.canWrite(context, "sales") }
  //  باطل همان اجازهٔ لغوِ فاکتور است؛ بدهی عددِ مالی است
  val canVoid = remember { canSell && ShopRole.canCancelSale(context) }
  val canMoney = remember { ShopRole.canSeeMoney(context) }
  var selling by remember { mutableStateOf(false) }
  var checking by remember { mutableStateOf(false) }
  var owed by remember { mutableStateOf(false) }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (canSell) OutlinedButton(onClick = { selling = true }) { Text(StrGiftCard.sell) }
    TextButton(onClick = { checking = true }) { Text(StrGiftCard.check) }
    if (canMoney) TextButton(onClick = { owed = true }) { Text(StrGiftCard.liability) }
  }

  if (selling) GiftCardSellDialog(store, toast) { selling = false }
  if (checking) GiftCardCheckDialog(d, store, canSell, canVoid, toast) { checking = false }
  if (owed) GiftCardLiabilityDialog(d) { owed = false }
}

/** برگهٔ چاپیِ کارت با نامِ دکان و تاریخِ امروز ⇒ پیام */
private suspend fun printCard(context: android.content.Context, code: String, amountMinor: Long?): String {
  val shop = runCatching { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("store_name", "") }.getOrNull()
  val sheet = GiftCards.sheet(code, amountMinor, shop, displayDate(ir.vil3ntec.tohid.todayIso())) ?: return GiftCards.message(GiftCard.Code.BAD_CODE)
  return GiftCardPrint.print(context, sheet)
}

private val random = java.security.SecureRandom()

private fun wholeAfghani(text: String): Long? =
  text.toLongOrNull()?.takeIf { it in 1..GiftCard.MAX_AMOUNT / 100 }?.let { it * 100 }

@Composable
private fun GiftCardSellDialog(store: ShopStore, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { newId() }
  var code by remember { mutableStateOf(GiftCard.display(GiftCard.make { random.nextInt(it) })) }
  var amount by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  //  فروخته شد ⇒ همین پنجره «چاپِ کارت» را پیش می‌آورد
  var sold by remember { mutableStateOf<Pair<String, Long>?>(null) }

  sold?.let { (c, m) ->
    AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      title = { Text(StrGiftCard.sell) },
      text = { Text(StrGiftCard.sold(GiftCard.display(c), money(PayFlow.major(m))), color = Shop.colors.text) },
      confirmButton = {
        TextButton(enabled = !busy, onClick = { busy = true; scope.launch { toast(printCard(context, c, m)); busy = false } }) { Text(StrGiftCard.printCard) }
      },
      dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrGiftCard.close) } },
    )
    return
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrGiftCard.sell) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
          value = code, onValueChange = { code = it.take(GiftCard.CODE_LEN + 4); error = null }, label = { Text(StrGiftCard.code) },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { code = GiftCard.display(GiftCard.make { random.nextInt(it) }); error = null }) { Text(StrGiftCard.newCode) }
        }
        Text(StrGiftCard.codeHint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = amount, onValueChange = { amount = ir.vil3ntec.tohid.numericInput(it).filter(Char::isDigit).take(9); error = null },
          label = { Text(StrGiftCard.amount) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Text(StrGiftCard.cashNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        val c = GiftCard.normalize(code)
        val m = wholeAfghani(amount)
        if (c == null) { error = GiftCards.message(GiftCard.Code.BAD_CODE); return@TextButton }
        if (m == null) { error = GiftCards.message(GiftCard.Code.INVALID_AMOUNT); return@TextButton }
        busy = true
        scope.launch {
          when (val r = GiftCardPref.issue(store, key, c, m, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), me.ifBlank { null })) {
            is GiftCardPref.Saved.Ok -> if (r.again) onDismiss() else { sold = c to m; toast(StrGiftCard.sold(GiftCard.display(c), money(PayFlow.major(m)))) }
            is GiftCardPref.Saved.Bad -> error = if (r.code == "UNREAD") DebtEntry.NOT_REREAD else GiftCards.message(r.code)
            GiftCardPref.Saved.NotSaved -> Unit
          }
          busy = false
        }
      }) { Text(StrGiftCard.save) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrGiftCard.cancel) } },
  )
}

@Composable
private fun GiftCardCheckDialog(d: ShopData, store: ShopStore, canSell: Boolean, canVoid: Boolean, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var code by remember { mutableStateOf("") }
  var asked by remember { mutableStateOf<String?>(null) }
  val view = remember(d) { GiftCards.view(d) }
  //  کارِ بازِ روی کارتِ پیداشده: شارژ یا باطل
  var recharging by remember { mutableStateOf<String?>(null) }
  var voiding by remember { mutableStateOf<GiftCard.Card?>(null) }
  var printing by remember { mutableStateOf(false) }

  recharging?.let { c -> GiftCardRechargeDialog(store, c, toast) { recharging = null } }
  voiding?.let { c -> GiftCardVoidDialog(store, c, canVoid, toast) { voiding = null } }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrGiftCard.check) },
    text = {
      Column {
        OutlinedTextField(
          value = code, onValueChange = { code = it.take(GiftCard.CODE_LEN + 4); asked = null }, label = { Text(StrGiftCard.code) },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { asked = code }) { Text(StrGiftCard.lookup) }
        asked?.let { q ->
          val c = GiftCard.normalize(q)
          val card = view.card(c)
          when {
            c == null -> Text(GiftCards.message(GiftCard.Code.BAD_CODE), color = Shop.colors.danger)
            card == null -> Text(GiftCards.message(GiftCard.Code.UNKNOWN_CARD), color = Shop.colors.danger)
            else -> Column {
              Text(GiftCard.display(card.code), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
              Text(StrGiftCard.balance(money(PayFlow.major(card.balanceMinor))), style = MaterialTheme.typography.titleMedium, color = Shop.colors.primary)
              val day = d.auditLog.firstOrNull { it.type == GiftCards.ISSUE && it.payRef == card.code }?.date.orEmpty()
              Text(StrGiftCard.issuedWith(money(PayFlow.major(card.issuedMinor)), if (day.isEmpty()) "—" else displayDate(day)), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
              Text(StrGiftCard.spentBack(money(PayFlow.major(card.redeemedMinor)), money(PayFlow.major(card.refundedMinor))), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
              if (card.rechargedMinor > 0) Text(StrGiftCard.rechargedTotal(money(PayFlow.major(card.rechargedMinor))), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
              if (card.void) Text(StrGiftCard.isVoid, color = Shop.colors.danger)
              if (card.review) Text(StrGiftCard.review, color = Shop.colors.warning)
              if (!card.void && canSell) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { recharging = card.code }) { Text(StrGiftCard.recharge) }
                TextButton(enabled = !printing, onClick = { printing = true; scope.launch { toast(printCard(context, card.code, null)); printing = false } }) { Text(StrGiftCard.printCard) }
                if (canVoid) TextButton(onClick = { voiding = card }) { Text(StrGiftCard.voidCard, color = Shop.colors.danger) }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrGiftCard.close) } },
  )
}

/** PRM-14 — شارژِ دوبارهٔ کارتِ [code]: نقد به دخل، با کلیدی که پنجره هنگامِ باز شدن می‌سازد (دو ضربه یک شارژ) */
@Composable
private fun GiftCardRechargeDialog(store: ShopStore, code: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { newId() }
  var amount by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrGiftCard.recharge) },
    text = {
      Column {
        Text(GiftCards.mask(code), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        OutlinedTextField(
          value = amount, onValueChange = { amount = ir.vil3ntec.tohid.numericInput(it).filter(Char::isDigit).take(9); error = null },
          label = { Text(StrGiftCard.amount) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Text(StrGiftCard.rechargeNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        val m = wholeAfghani(amount) ?: run { error = GiftCards.message(GiftCard.Code.INVALID_AMOUNT); return@TextButton }
        busy = true
        scope.launch {
          when (val r = GiftCardPref.recharge(store, key, code, m, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), me.ifBlank { null })) {
            is GiftCardPref.Saved.Ok -> { onDismiss(); if (!r.again) toast(StrGiftCard.recharged(GiftCards.mask(code), money(PayFlow.major(m)))) }
            is GiftCardPref.Saved.Bad -> error = if (r.code == "UNREAD") DebtEntry.NOT_REREAD else GiftCards.message(r.code)
            GiftCardPref.Saved.NotSaved -> Unit
          }
          busy = false
        }
      }) { Text(StrGiftCard.rechargeSave) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrGiftCard.cancel) } },
  )
}

/** باطل کردن با دلیل؛ ماندهٔ همان لحظه از بدهی کنار می‌رود (بی نقد) — فقط با اجازه */
@Composable
private fun GiftCardVoidDialog(store: ShopStore, card: GiftCard.Card, allowed: Boolean, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { newId() }
  var reason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrGiftCard.voidCard) },
    text = {
      Column {
        Text(GiftCards.mask(card.code), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.take(GiftCard.MAX_TEXT); error = null }, label = { Text(StrGiftCard.voidReason) },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Text(StrGiftCard.voidNote(money(PayFlow.major(card.balanceMinor.coerceAtLeast(0)))), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
      }
    },
    confirmButton = {
      TextButton(enabled = !busy && allowed, onClick = {
        if (reason.trim().length < GiftCard.REASON_MIN) { error = GiftCards.message(GiftCard.Code.REASON_REQUIRED); return@TextButton }
        busy = true
        scope.launch {
          when (val r = GiftCardPref.void(store, key, card.code, reason.trim(), allowed, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), me.ifBlank { null })) {
            is GiftCardPref.Saved.Ok -> { onDismiss(); if (!r.again) toast(StrGiftCard.voided(GiftCards.mask(card.code))) }
            is GiftCardPref.Saved.Bad -> error = if (r.code == "UNREAD") DebtEntry.NOT_REREAD else GiftCards.message(r.code)
            GiftCardPref.Saved.NotSaved -> Unit
          }
          busy = false
        }
      }) { Text(StrGiftCard.voidSave, color = Shop.colors.danger) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrGiftCard.cancel) } },
  )
}

/** گزارشِ بدهیِ کارت‌ها از ردیف‌های دفتر (`GiftCards.liability`) — جمع‌ها، برابری و کارت‌های بازِ نقاب‌دار */
@Composable
private fun GiftCardLiabilityDialog(d: ShopData, onDismiss: () -> Unit) {
  val r = remember(d) { GiftCards.liability(d) }
  fun amt(minor: Long) = money(PayFlow.major(minor))

  @Composable
  fun line(label: String, value: String, strong: Boolean = false) = Row(Modifier.fillMaxWidth()) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f))
    Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall, color = Shop.colors.text)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrGiftCard.liability) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (r.activeCount + r.voidCount == 0) { Text(StrGiftCard.noCards, color = Shop.colors.muted); return@Column }
        Text(StrGiftCard.liabilityTotal, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        Text(amt(r.outstandingMinor), style = MaterialTheme.typography.titleLarge, color = Shop.colors.primary)
        Text(StrGiftCard.cardCounts(r.activeCount, r.voidCount), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        Spacer(Modifier.height(8.dp))
        line(StrGiftCard.issuedWord, amt(r.issuedMinor))
        line("+ " + StrGiftCard.rechargedWord, amt(r.rechargedMinor))
        line("− " + StrGiftCard.redeemedWord, amt(r.redeemedMinor))
        line("+ " + StrGiftCard.refundedWord, amt(r.refundedMinor))
        line("− " + StrGiftCard.voidedWord, amt(r.voidedMinor))
        line("= " + StrGiftCard.netWord, amt(r.netMinor), strong = true)
        if (r.overdrawnMinor > 0) line(StrGiftCard.overdrawnWord, amt(r.overdrawnMinor))
        Text(
          if (r.reconciles) StrGiftCard.reconciled else StrGiftCard.notReconciled, style = MaterialTheme.typography.labelSmall,
          color = if (r.reconciles) Shop.colors.muted2 else Shop.colors.danger,
        )
        Spacer(Modifier.height(8.dp))
        //  کد خودش پول است ⇒ این‌جا هم فقط چهار نویسهٔ آخر
        r.open.forEach { c ->
          line(GiftCards.mask(c.code) + if (c.review) " ⚠" else "", amt(c.balanceMinor))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrGiftCard.close) } },
  )
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در تنظیماتِ نسخهٔ ۳ (`Settings.kt`) */
@Composable
fun GiftCardSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(GiftCardPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.CardGiftcard,
    title = StrGiftCard.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrGiftCard.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrGiftCard.needsPayPanel, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; GiftCardPref.set(context, on) },
    )
  }
}
