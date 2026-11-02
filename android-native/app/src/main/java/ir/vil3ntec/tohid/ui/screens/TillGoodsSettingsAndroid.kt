package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
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
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.ui.theme.Shop

/** عنوانِ بخش — صفحهٔ نسخهٔ ۳ (`SettingsLayout.TILL_GOODS`) */
const val TILL_GOODS_TITLE = "ثبتِ سریع و واحدِ بزرگ در صندوق"
const val TILL_GOODS_NOTE =
  "ثبتِ سریع (S03): بارکدِ ناشناس در صندوق ⇒ «ثبتِ سریعِ کالا» با همان بارکد، و کالا همان‌جا به فاکتور می‌رود؛ فقط برای نقشی که کالا " +
    "ثبت می‌کند — صندوق‌دار فقط «قلمِ آزاد». واحدِ بزرگ (S05): Ctrl+U و برگهٔ ردیف واحدهای کالا (واحدِ دوم و بسته‌ها) را با قیمتِ " +
    "بسته از لیستِ قیمت نشان می‌دهند؛ اگر قیمتِ بسته نوشته نشده، اندازه × قیمتِ واحد."
const val TILL_GOODS_QUICK = "ثبتِ سریعِ کالا"
const val TILL_GOODS_UNITS = "واحدِ بزرگ (Ctrl+U) و قیمتِ بسته"
const val TILL_GOODS_PART = "فروشِ بخشی از بسته (مثلاً ۱٫۵ کارتن)"
const val TILL_GOODS_PART_OK = "مجاز"
const val TILL_GOODS_PART_NO = "فقط بستهٔ کامل"

/** S03/S05 — بخشِ «ثبتِ سریع و واحدِ بزرگ در صندوق» در تنظیماتِ گوشی و کامپیوترِ رابطِ امروز (`TillGoodsPref`، همه خاموش) */
@Composable
fun TillGoodsSettingsWired(context: android.content.Context) {
  var quick by remember { mutableStateOf(TillGoodsPref.quickRegister(context)) }
  var units by remember { mutableStateOf(TillGoodsPref.units(context)) }
  var whole by remember { mutableStateOf(TillGoodsPref.wholePacks(context)) }
  val onOff = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn)
  SettingsSection(
    icon = Icons.Filled.QrCodeScanner,
    title = TILL_GOODS_TITLE,
    subtitle = if (quick || units) StrShell.switchOn else StrShell.switchOff,
    tint = if (quick || units) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(TILL_GOODS_NOTE, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    Text(TILL_GOODS_QUICK, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    SegmentedChoice(options = onOff, selected = if (quick) "on" else "off", onSelect = { quick = it == "on"; TillGoodsPref.setQuickRegister(context, quick) })
    Spacer(Modifier.height(8.dp))
    Text(TILL_GOODS_UNITS, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    SegmentedChoice(options = onOff, selected = if (units) "on" else "off", onSelect = { units = it == "on"; TillGoodsPref.setUnits(context, units) })
    if (units) {
      Spacer(Modifier.height(8.dp))
      Text(TILL_GOODS_PART, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
      SegmentedChoice(
        options = listOf("part" to TILL_GOODS_PART_OK, "whole" to TILL_GOODS_PART_NO),
        selected = if (whole) "whole" else "part",
        onSelect = { whole = it == "whole"; TillGoodsPref.setWholePacks(context, whole) },
      )
    }
  }
}
