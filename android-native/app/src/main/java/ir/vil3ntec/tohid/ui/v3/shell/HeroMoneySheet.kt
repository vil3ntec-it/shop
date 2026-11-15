package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHero
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel

/**
 *  برگهٔ دکمهٔ «+» ِ کادرِ بالای خانه: «اضافه / برداشت»، مبلغ، دلیل (همان دلیل‌های برگهٔ صندوق و گاوصندوق، نخستین
 *  پیش‌فرض) و یادداشت. فقط کادر — ثبت در `HeroMoneyRoute` (`HeroMoney`). در حینِ ثبت دکمه «در حالِ ثبت» است و برگه
 *  بسته نمی‌شود؛ صندوقِ بسته ⇒ فقط پیام و «باز کردنِ صندوق».
 *  گاوصندوق با [currency]: کادرِ کوچکِ «ارز» کنارِ مبلغ (`SafeCurrencyPill`) و کد/نشانهٔ همان ارز در کادرِ مبلغ؛ «+ ارزِ
 *  دیگر» فرمِ کوچکِ زیرِ مبلغ را باز می‌کند. صندوق (`TILL`) همیشه افغانی و بی این کادر.
 */
@Composable
fun HeroMoneySheet(
  page: HomeHero.Page,
  shiftClosed: Boolean,
  busy: Boolean,
  error: String?,
  onSubmit: (dir: HeroMoney.Dir, amount: String, reason: String?, note: String) -> Unit,
  onOpenDrawer: () -> Unit,
  onClose: () -> Unit,
  start: HeroMoney.Dir = HeroMoney.Dir.ADD,
  startAmount: String = "",
  currency: SafeCurrencyChoice? = null,
) {
  val c = PhoneV3.colors
  var dir by remember { mutableStateOf(start) }
  val reasons = HeroMoney.reasons(page, dir)
  var reason by remember(dir) { mutableStateOf(reasons.firstOrNull()) }
  var amount by remember { mutableStateOf(startAmount) }
  var note by remember { mutableStateOf("") }
  var adding by remember { mutableStateOf(false) }
  V3Sheet(onDismiss = { if (!busy) onClose() }, title = if (page == HomeHero.Page.TILL) StrHero.tillSheet else StrHero.safeSheet, dismissible = !busy, detents = false) {
    if (shiftClosed) {
      V3Message(V3Tone.WARNING, StrSafe.shiftClosed, action = StrSale.openDrawer, onAction = onOpenDrawer)
      Spacer(Modifier.height(12.dp))
      return@V3Sheet
    }
    val dirs = HeroMoney.Dir.entries
    V3Segmented(listOf(StrHero.add, StrHero.take), dirs.indexOf(dir), { if (!busy) dir = dirs[it] })
    Spacer(Modifier.height(12.dp))
    val cur = currency?.takeIf { page == HomeHero.Page.SAFE }
    if (cur == null) V3TextField(amount, { amount = it }, StrSafe.amount, kind = V3FieldKind.MONEY)
    else {
      Row(verticalAlignment = Alignment.Top) {
        V3TextField(
          amount, { amount = it }, if (cur.code == SafeCurrency.AFN) StrSafe.amount else StrSafeCur.amountIn(cur.code), Modifier.weight(1f),
          kind = V3FieldKind.MONEY, suffix = SafeCurrencies.symbol(cur.code),
        )
        Spacer(Modifier.width(8.dp))
        SafeCurrencyPill(cur, enabled = !busy, onAddRequest = { adding = true })
      }
      if (adding) {
        Spacer(Modifier.height(12.dp))
        SafeCurrencyAddForm(cur, onDone = { adding = false })
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(StrSale.reasonWord, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      reasons.forEach { r -> V3Chip(reasonLabel(page, r), r == reason, { if (!busy) reason = r }) }
    }
    Spacer(Modifier.height(12.dp))
    V3TextField(note, { note = it.take(SafeCore.MAX_TEXT) }, StrHero.noteOptional)
    error?.let {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.ERROR, it)
    }
    Spacer(Modifier.height(16.dp))
    V3Button(
      if (dir == HeroMoney.Dir.ADD) StrHero.add else StrHero.take,
      { if (!busy) onSubmit(dir, amount, reason, note) },
      Modifier.fillMaxWidth(),
      enabled = !busy,
      loading = busy,
      haptic = true,
    )
  }
}

/** نامِ دلیل: صندوق همان برچسب‌های برگهٔ «ورود/خروجِ نقد»، گاوصندوق همان برچسب‌های پنجرهٔ گاوصندوق */
private fun reasonLabel(page: HomeHero.Page, r: String): String =
  if (page == HomeHero.Page.TILL) DrawerModel.moveLabel(r)
  else when (r) {
    "deposit" -> StrSafe.reasonDeposit
    "owner_withdrawal" -> StrSafe.reasonOwnerWithdrawal
    "petty_expense" -> StrSafe.reasonPettyExpense
    SafeCore.OTHER -> StrSafe.reasonOther
    else -> r
  }
