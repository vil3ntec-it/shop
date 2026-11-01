package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.AutoFeesPref
import ir.vil3ntec.tohid.data.ShopData

/** POS-36 — بخشِ تنظیمات با خواندن و نوشتنِ همین دستگاه (مرزِ پلتفرم، تا `Settings` یک خط بخواهد) */
@Composable
fun AutoFeesSettingsWired(context: android.content.Context, d: ShopData) {
  var rules by remember { mutableStateOf(AutoFeesPref.rules(context)) }
  AutoFeesSettings(rules, d) { rules = it; AutoFeesPref.set(context, it) }
}
