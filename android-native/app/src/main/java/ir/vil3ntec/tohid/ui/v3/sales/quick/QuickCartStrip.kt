package ir.vil3ntec.tohid.ui.v3.sales.quick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartDockPreview

/** خلاصهٔ سبدِ مشترک در فروش سریع، با راه مستقیم به صندوق و پرداخت. */
@Composable
fun QuickCartStrip(model: CartSummary.Model, onOpen: () -> Unit, onPay: () -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(28.dp)
  val navInset = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val spoken = StrSale.cartSpoken(plain(model.count), PhoneSaleText.amount(model.total))
  Row(
    modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navInset + 12.dp)
      .shadow(8.dp, shape, clip = false)
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CartDockPreview(model, Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).v3Pressable(label = spoken, onClick = onOpen).padding(vertical = 4.dp))
    Spacer(Modifier.width(10.dp))
    V3Button(StrSale.pay, onPay, height = 46.dp, cornerRadius = 23.dp, haptic = true)
  }
}
