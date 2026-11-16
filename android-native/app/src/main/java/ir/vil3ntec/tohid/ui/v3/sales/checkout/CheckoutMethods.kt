package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.screens.v3Text

/**
 *  e-paymethods — «روشِ پرداخت» ِ تسویهٔ گوشیِ نسخهٔ ۳ (با پنلِ ترکیبی، `ShopPolicy.payV2`): همهٔ روش‌های دکان به‌صورتِ کاشی
 *  (`core/pay/TillMethods`) — نقد، کارت (کارت‌خوان)، حساب‌پی (QR/NFC)، کیف پول، حواله، نسیه — سه در هر سطر. کارِ هر کاشی را
 *  `CheckoutRoute` می‌دهد ([onPick]) و حساب همه `PayFlow` است؛ این‌جا فقط کشیدن و «انتخاب‌شده» از همان پنل.
 *  حساب‌پیِ در انتظار ⇒ کاشیِ خاموش و زیرش زمانِ فعال شدن؛ راه‌اندازی‌نشده ⇒ هیچ کاشی و فقط برای صاحب [onSetup].
 */
@Composable
internal fun CheckoutMethods(
  tiles: List<TillMethods.Tile>,
  /** کاشیِ «انتخاب‌شده» (گامِ پرداخت: کاشیِ باز) */
  selected: (TillMethods.Tile) -> Boolean,
  busy: Boolean,
  onPick: (TillMethods.Tile) -> Unit,
  onSetup: (() -> Unit)? = null,
  title: String = StrTillPay.methodsTitle,
) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth()) {
    Text(title, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(bottom = 6.dp))
    tiles.chunked(3).forEach { line ->
      //  کاشی‌های یک سطر هم‌قد (با زیرنویس و بی آن)
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        line.forEach { t -> MethodTile(t, selected(t), enabled = t.enabled && !busy, Modifier.weight(1f).fillMaxHeight()) { onPick(t) } }
        repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
      }
    }
    tiles.firstOrNull { it.kind == TillMethods.Kind.HESABPAY && it.until != null }?.until?.let { until ->
      Text(StrTillPay.hesabPending(SaleTime.stamp(until)), style = V3Type.footnote, color = PhoneV3Roles.toneIcon(c, ir.vil3ntec.tohid.ui.theme.V3Tone.WARNING))
      Spacer(Modifier.height(4.dp))
    }
    if (onSetup != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrTillPay.hesabSetupNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.weight(1f))
        V3TextButton(StrTillPay.hesabSetup, onSetup)
      }
    }
  }
}

/** نام و زیرنویس و آیکنِ هر کاشی */
internal fun tileLabel(k: TillMethods.Kind): Pair<String, String?> = when (k) {
  TillMethods.Kind.CASH -> StrTillPay.tileCash to null
  TillMethods.Kind.CARD -> StrTillPay.tileCard to StrTillPay.tileCardSub
  TillMethods.Kind.HESABPAY -> StrTillPay.tileHesab to StrTillPay.tileHesabSub
  TillMethods.Kind.WALLET -> StrTillPay.tileWallet to null
  TillMethods.Kind.TRANSFER -> StrTillPay.tileTransfer to null
  TillMethods.Kind.CREDIT -> StrTillPay.tileCredit to StrTillPay.tileCreditSub
}

internal fun tileIcon(k: TillMethods.Kind): ImageVector = when (k) {
  TillMethods.Kind.CASH -> Icons.Outlined.Payments
  TillMethods.Kind.CARD -> Icons.Outlined.CreditCard
  TillMethods.Kind.HESABPAY -> Icons.Outlined.QrCode2
  TillMethods.Kind.WALLET -> Icons.Outlined.AccountBalanceWallet
  TillMethods.Kind.TRANSFER -> Icons.Outlined.SwapHoriz
  TillMethods.Kind.CREDIT -> Icons.Outlined.RequestQuote
}

@Composable
private fun MethodTile(t: TillMethods.Tile, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val (name, sub) = tileLabel(t.kind)
  val shape = RoundedCornerShape(14.dp)
  val bg = if (selected) PhoneV3Palette.selected(c) else c.card
  val edge = if (selected) PhoneV3Roles.focus(c) else PhoneV3Roles.inputBorder(c)
  val ink = if (selected) PhoneV3Roles.brandText(c) else c.ink
  val pending = t.kind == TillMethods.Kind.HESABPAY && t.until != null
  Column(
    modifier
      .heightIn(min = 64.dp)
      .clip(shape)
      .background(bg)
      .border(if (selected) 1.5.dp else 1.dp, edge, shape)
      .v3Pressable(enabled = enabled, label = name, role = Role.Button, onClick = onClick)
      .semantics {
        this.selected = selected
        if (pending) stateDescription = StrTillPay.tileSoon
      }
      .alpha(if (enabled) 1f else 0.5f)
      .padding(horizontal = 6.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(tileIcon(t.kind), contentDescription = null, tint = if (selected) ink else c.icon, modifier = Modifier.size(22.dp))
    Spacer(Modifier.height(4.dp))
    Text(name, style = v3Text(14, FontWeight.SemiBold, 18), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    val line = if (pending) StrTillPay.tileSoon else sub
    if (line != null) Text(line, style = v3Text(11, FontWeight.Normal, 14), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
  }
}
