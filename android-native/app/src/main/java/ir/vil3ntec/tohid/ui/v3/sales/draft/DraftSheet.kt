package ir.vil3ntec.tohid.ui.v3.sales.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DraftRestore
import ir.vil3ntec.tohid.data.SaleDraftRules
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/**
 *  POS-13 — یک بار پس از باز شدنِ برنامه: پیش‌نویسی که از بارِ پیش مانده (`CartStore.recovered`).
 *  تصمیم با موتور است (`SaleDraftRules.decide`): ثبت‌شده ⇒ بی‌پرسش کنار و یک پیام؛ هیچ ⇒ کنار؛
 *  پیشنهاد ⇒ برگهٔ «فاکتورِ نیمه‌کاره». داده هرگز بی‌اجازه دور ریخته نمی‌شود: بستنِ برگه (برگشت،
 *  کشیدن) یعنی «بعداً» — ردیف‌ها در سبد می‌مانند، مثلِ پیش.
 */
@Composable
fun DraftPrompt(cartStore: CartStore, d: ShopData, loaded: Boolean, snackbar: SnackbarHostState) {
  val draft by cartStore.recovered.collectAsState()
  val pending = draft ?: return
  if (!loaded) return
  when (val decision = remember(pending, d) { SaleDraftRules.decide(pending, d) }) {
    DraftRestore.None -> LaunchedEffect(pending) { cartStore.resolveRecovery(null) }
    is DraftRestore.Posted -> LaunchedEffect(pending) {
      cartStore.resolveRecovery(null)
      snackbar.showSnackbar(StrSale.draftWasPosted)
    }
    is DraftRestore.Offer -> {
      val offer = decision.draft
      val total = remember(offer, d) { SalesEngine.cartTotal(d, offer.cart()) }
      DraftSheet(
        lines = offer.lines.size,
        total = total,
        dropped = decision.dropped,
        onContinue = {
          cartStore.resolveRecovery(offer)
          AppNav.go(SalesPaths.SALE)
        },
        onDiscard = { cartStore.resolveRecovery(null) },
        onLater = { cartStore.resolveRecovery(offer) },
      )
    }
  }
}

/** برگهٔ «فاکتورِ نیمه‌کاره پیدا شد»: مبلغِ درشتِ شمارنده، شرح و دو کار */
@Composable
fun DraftSheet(lines: Int, total: Double, dropped: Int, onContinue: () -> Unit, onDiscard: () -> Unit, onLater: () -> Unit) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(total)
  //  زدنِ یکی از دو کار ⇒ بستنِ برگه دیگر «بعداً» نیست
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) onLater() }, detents = false) { closer ->
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      V3IconTile(Icons.AutoMirrored.Outlined.ReceiptLong, hue = V3Hue.AMBER, size = 52.dp)
      Spacer(Modifier.height(12.dp))
      Text(StrSale.draftTitle, style = V3Type.title, color = c.ink, textAlign = TextAlign.Center)
      Spacer(Modifier.height(6.dp))
      Text(PhoneSaleText.amount(shown), style = V3Type.figure, color = c.ink)
      Spacer(Modifier.height(8.dp))
      Text(DraftModel.body(lines, total, dropped), style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrSale.draftContinue, { chosen[0] = true; closer.close(onContinue) }, Modifier.fillMaxWidth(), haptic = true)
      V3Button(StrSale.draftDiscard, { chosen[0] = true; closer.close(onDiscard) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    }
  }
}
