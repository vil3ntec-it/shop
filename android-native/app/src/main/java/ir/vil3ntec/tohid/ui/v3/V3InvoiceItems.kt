package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  یک قلمِ فاکتور برای نمایش. متن‌ها از پیش ساخته‌اند — پول و مقدار را صدازننده با
 *  موتورِ خودش قالب می‌کند (`DebtBook.show`، `Money`…)؛ کیت به حساب دست نمی‌زند.
 */
data class V3InvoiceLine(
  val name: String,
  /** «۲ کیلو × ۱۵۰ افغانی» */
  val detail: String,
  /** مبلغِ ردیف، «۳۰۰ افغانی» */
  val total: String,
  /** یادداشتِ کوتاه کنارِ `detail` با «·» (مثلاً «مرجوعی ۱») */
  val note: String? = null,
)

/**
 *  اقلامِ یک فاکتور («این چی‌ها رو برده»): سرخطِ اختیاری بیرونِ کارت، هر قلم نام و
 *  «مقدار × قیمت» در دو خط با مبلغ در پایانِ ردیف، و پانویسِ اختیاری (تخفیف، پرداختِ
 *  همان لحظه) — همان کارتِ اقلامِ برگهٔ تراکنشِ قرض‌دار (#354). هر جایی که اقلامِ
 *  فاکتور نشان داده می‌شود (قرض، تاریخچهٔ فروش، اعلان) همین است.
 */
@Composable
fun V3InvoiceItems(
  items: List<V3InvoiceLine>,
  modifier: Modifier = Modifier,
  title: String? = null,
  footer: String? = null,
) {
  val c = PhoneV3.colors
  Column(modifier) {
    if (title != null) {
      Text(title, style = v3Text(15, FontWeight.Bold, 21), color = c.ink, modifier = Modifier.padding(horizontal = 4.dp))
      Spacer(Modifier.height(6.dp))
    }
    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
      items.forEachIndexed { i, l ->
        if (i > 0) V3Divider(start = 14.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(l.name, style = v3Text(14, FontWeight.Medium, 20), color = c.ink)
            Text(
              listOfNotNull(l.detail.takeIf { it.isNotBlank() }, l.note).joinToString(" · "),
              style = v3Text(12, FontWeight.Normal, 17),
              color = c.inkMuted,
            )
          }
          Spacer(Modifier.width(12.dp))
          Text(l.total, style = v3Text(14, FontWeight.SemiBold, 20), color = c.ink)
        }
      }
      if (footer != null) {
        V3Divider(start = 14.dp)
        Text(footer, style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
      }
    }
  }
}
