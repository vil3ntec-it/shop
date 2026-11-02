package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.ui.theme.Shop

/** عنوانِ بخشِ تنظیم — همان که ستونِ تنظیماتِ کامپیوتر (`DeskSettings` ِ `AppRoot`) نشان می‌دهد */
const val PURCHASE_FX_SETTINGS_TITLE = "خرید به ارزِ دیگر (فاکتورِ خرید)"

/** PUR-11 — بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش)؛ فقط فاکتورِ خریدِ کامپیوتر (`DeskPurchasing`) آن را می‌خواند */
@Composable
fun PurchaseFxSettingsWired(context: Context) {
  var on by remember { mutableStateOf(PurchaseFxPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.CurrencyExchange,
    title = PURCHASE_FX_SETTINGS_TITLE,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(
      "فاکتورِ خرید ارز (مثلاً USD) و نرخِ همان روز را می‌گیرد؛ فیِ هر ردیف با همان نرخ به افغانی می‌رود و بهای تمام‌شده، " +
        "انبار و حسابِ تأمین‌کننده افغانی می‌مانند. ارز، نرخ و فیِ اصلی روی سند می‌مانند.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Text(
      "پرداخت و بدهیِ تأمین‌کننده افغانیِ روزِ ثبت‌اند؛ حسابِ دوبارهٔ بدهی با نرخِ روزِ پرداخت نیست (تصمیمِ D-15).",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; PurchaseFxPref.set(context, on) },
    )
  }
}
