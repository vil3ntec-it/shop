package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.returns.ReturnPolicy
import ir.vil3ntec.tohid.data.ReturnPolicyPrefs

/** بخشِ سیاست در «برگشت و تعویض» با خواندن و نوشتنِ همین تنظیم — مرزِ پلتفرم */
@Composable
fun ReturnPolicySettingsWired(context: Context) {
  val start = remember { ReturnPolicyPrefs.of(context) }
  //  متنِ کادرها همان نوشتهٔ کاربر می‌ماند (خطِ خالیِ وسطِ نوشتن پاک نمی‌شود)؛ ذخیره پاک‌شده است
  var days by remember { mutableStateOf(if (start.days > 0) start.days.toString() else "") }
  var blocked by remember { mutableStateOf(ReturnPolicy.formatRules(start.blocked)) }
  ReturnPolicySettings(
    days = days,
    blocked = blocked,
    onDays = { days = it; ReturnPolicyPrefs.setDays(context, it) },
    onBlocked = { blocked = it; ReturnPolicyPrefs.setBlocked(context, it) },
  )
}
