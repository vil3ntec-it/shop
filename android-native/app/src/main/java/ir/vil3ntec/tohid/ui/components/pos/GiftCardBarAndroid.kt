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
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  PAY-24 — کارتِ هدیه در صندوقِ گوشی و کامپیوتر (یک خط کنارِ `SpecialOrderBar` در `SaleLegacy`، `Sale` و `DeskSale`):
 *  «فروشِ کارتِ هدیه» و «ماندهٔ کارتِ هدیه». فقط با تنظیمِ همین دستگاه (`GiftCardPref`، پیش‌فرض خاموش ⇒ هیچ). پرداخت با
 *  کارت و اعتبار در پنلِ پرداختِ ترکیبی است (`PaySplitSection`)؛ این‌جا هیچ فاکتوری ثبت نمی‌شود. فروشِ کارت فقط با
 *  `GiftCardPref.issue` روی دفترِ همین لحظهٔ دیسک و کلیدی که پنجره هنگامِ باز شدن می‌سازد؛ فقط نقشی که فاکتور می‌نویسد.
 */
@Composable
fun GiftCardBar(d: ShopData, store: ShopStore, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { GiftCardPref.on(context) }
  if (!on) return
  val canSell = remember { ShopRole.canWrite(context, "sales") }
  var selling by remember { mutableStateOf(false) }
  var checking by remember { mutableStateOf(false) }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (canSell) OutlinedButton(onClick = { selling = true }) { Text(StrGiftCard.sell) }
    TextButton(onClick = { checking = true }) { Text(StrGiftCard.check) }
  }

  if (selling) GiftCardSellDialog(store, toast) { selling = false }
  if (checking) GiftCardCheckDialog(d) { checking = false }
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
            is GiftCardPref.Saved.Ok -> { onDismiss(); if (!r.again) toast(StrGiftCard.sold(GiftCard.display(c), money(PayFlow.major(m)))) }
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
private fun GiftCardCheckDialog(d: ShopData, onDismiss: () -> Unit) {
  var code by remember { mutableStateOf("") }
  var asked by remember { mutableStateOf<String?>(null) }
  val view = remember(d) { GiftCards.view(d) }

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
              if (card.void) Text(StrGiftCard.isVoid, color = Shop.colors.danger)
              if (card.review) Text(StrGiftCard.review, color = Shop.colors.warning)
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrGiftCard.close) } },
  )
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `SettingsLegacy` کنارِ تنظیم‌های صندوق */
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
