package ir.vil3ntec.tohid.ui.v3.sales.quick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  نوارِ کوچکِ سبد زیرِ «فروشِ سریع» — همان خانوادهٔ نوارِ r3، ولی روشن: کارتِ مرزدار با
 *  سه عکسِ تازه‌ترین قلم‌ها، «۴ قلم» و جمعِ شمارنده، و «پرداخت». زدنِ نوار ⇐ سبدِ کامل
 *  (صندوق)؛ «پرداخت» ⇐ `sale/checkout`. جمع را صدازننده از موتور می‌دهد (`model.total`).
 */
@Composable
fun QuickCartStrip(model: CartSummary.Model, onOpen: () -> Unit, onPay: () -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val total = rememberCountUp(model.total)
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
      .v3Pressable(label = spoken, onClick = onOpen)
      .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      model.thumbs.forEach { t -> key(t.id) { V3ProductThumb(t.id, t.initials, size = 34.dp) } }
      if (model.more > 0) Text("+${plain(model.more)}", style = V3Type.subhead, color = c.inkMuted, maxLines = 1)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(StrSale.linesCount(plain(model.count)), style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
      Text(PhoneSaleText.amount(total), style = V3Type.headline, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(10.dp))
    V3Button(StrSale.pay, onPay, height = 44.dp, haptic = true)
  }
}
