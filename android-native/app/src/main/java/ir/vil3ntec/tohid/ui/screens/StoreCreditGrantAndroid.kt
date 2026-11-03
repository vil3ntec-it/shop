package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.StoreCredit
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.GiftCards
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  PRM-15 — «دادنِ اعتبارِ هدیه» (اعتبارِ فروشگاه بی کارت) در پروندهٔ مشتری: حسابِ گوشیِ نسخهٔ ۳ (`DebtorFileExtras`) و
 *  پروندهٔ کامپیوتر (`DeskCustomers`)، همان یک جزء. فقط پشتِ همان تنظیمِ «کارتِ هدیه و اعتبارِ فروشگاه» (`GiftCardPref`،
 *  پیش‌فرض خاموش)، مشتریِ فعال (نه ادغام‌شده) و نقشی که تخفیف دارد و دفترِ قرض را می‌نویسد؛ ثبت فقط با تصمیمِ «مجاز» ِ
 *  `sales.discount` برای **همین مبلغ** (سقفِ مبلغِ نقش هم) — قاعده در `StoreCredit.grantProblem` (core) و
 *  `GiftCards.grantCredit`؛ نوشتن با `GiftCardPref.grant` (دفترِ همین لحظهٔ دیسک، کلیدِ پنجره ⇒ دو ضربه یک ردیف).
 *  پنجره ماندهٔ امروز و پس از اعطا را نشان می‌دهد (بدهی اول کم می‌شود).
 */
@Composable
fun StoreCreditGrantButton(store: ShopStore, d: ShopData, debtorId: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { GiftCardPref.on(context) }
  if (!on) return
  val debtor = d.debtors.find { it.id == debtorId } ?: return
  if (!CustomerCrm.isActive(debtor)) return
  val may = remember { ShopRole.mayDiscount(context) && ShopRole.canWrite(context, "transactions") }
  if (!may) return
  var open by remember(debtorId) { mutableStateOf(false) }
  OutlinedButton(onClick = { open = true }, modifier = modifier) { Text(StrGiftCard.grantTitle) }
  if (open) StoreCreditGrantDialog(store, d, debtor, onMessage) { open = false }
}

/** مبلغِ افغانیِ درست ⇒ پول؛ وگرنه null */
private fun wholeAfghani(text: String): Long? = text.toLongOrNull()?.takeIf { it in 1..GiftCard.MAX_AMOUNT / 100 }?.let { it * 100 }

@Composable
private fun StoreCreditGrantDialog(store: ShopStore, d: ShopData, debtor: Debtor, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { newId() }
  var amount by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val minor = wholeAfghani(amount)
  val (now, after) = GiftCards.grantPreview(d, debtor.id, minor ?: 0L)
  val shown = { m: Long -> money(PayFlow.major(m)) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    containerColor = Shop.colors.surfaceSolid,
    title = { Text("${StrGiftCard.grantTitle} — ${debtor.name}", color = Shop.colors.text) },
    text = {
      Column {
        OutlinedTextField(
          value = amount, onValueChange = { amount = ir.vil3ntec.tohid.numericInput(it).filter(Char::isDigit).take(9); error = null },
          label = { Text(StrGiftCard.amount) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.take(GiftCard.MAX_TEXT); error = null },
          label = { Text(StrGiftCard.grantReason) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          if (now > 0) StrGiftCard.debtNow(shown(now)) else StrGiftCard.creditLeft(shown(-now)),
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        if (minor != null) {
          Text(
            if (after > 0) StrGiftCard.afterDebt(shown(after)) else StrGiftCard.afterCredit(shown(-after)),
            style = MaterialTheme.typography.bodySmall, color = Shop.colors.text,
          )
        }
        Text(StrGiftCard.grantNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        val m = minor ?: run { error = GiftCards.message(StoreCredit.INVALID_AMOUNT); return@TextButton }
        if (reason.trim().length < GiftCard.REASON_MIN) { error = GiftCards.message(GiftCard.Code.REASON_REQUIRED); return@TextButton }
        //  همان هستهٔ مجوزِ سرور: «نیازمندِ تأیید» یا سقفِ مبلغِ کمتر ⇒ این‌جا نه
        val decision = ShopRole.decide(context, "sales.discount", mapOf("amount" to m.toDouble())).decision
        busy = true
        scope.launch {
          when (val r = GiftCardPref.grant(store, key, debtor.id, m, reason.trim(), decision, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), me.ifBlank { null })) {
            is GiftCardPref.Saved.Ok -> { onDismiss(); if (!r.again) toast(StrGiftCard.granted(debtor.name, shown(m))) }
            is GiftCardPref.Saved.Bad -> error = if (r.code == "UNREAD") DebtEntry.NOT_REREAD else GiftCards.message(r.code)
            GiftCardPref.Saved.NotSaved -> Unit
          }
          busy = false
        }
      }) { Text(StrGiftCard.grantSave) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrGiftCard.cancel) } },
  )
}
