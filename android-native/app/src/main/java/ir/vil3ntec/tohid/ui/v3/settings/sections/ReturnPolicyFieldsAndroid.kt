package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrRetPolicy
import ir.vil3ntec.tohid.core.returns.ReturnPolicy
import ir.vil3ntec.tohid.data.ReturnPolicyPrefs
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote

/**
 *  RET-09 در «برگشت و تعویض» ِ نسخهٔ ۳ (با جریانِ تازهٔ روشن) — مهلتِ برگشت به روز و اقلامِ غیرقابلِ برگشت؛ همان
 *  `ReturnPolicyPrefs.setDays` و `setBlocked` ِ `ReturnPolicySettingsWired` (خالی = بی سیاست؛ متنِ کادر همان نوشتهٔ
 *  کاربر می‌ماند، ذخیره پاک‌شده است). کادرِ روز فقط رقمِ لاتین می‌گیرد، مثلِ رابطِ امروز.
 */
internal fun LazyListScope.returnPolicyItems() {
  item(key = "policy") {
    val context = LocalContext.current
    val start = remember { ReturnPolicyPrefs.of(context) }
    var days by remember { mutableStateOf(if (start.days > 0) start.days.toString() else "") }
    var blocked by remember { mutableStateOf(ReturnPolicy.formatRules(start.blocked)) }
    SettingsNote(StrRetPolicy.daysNote)
    SettingsBlock {
      V3TextField(
        value = days,
        onValueChange = { v -> days = v.filter { it in '0'..'9' }; ReturnPolicyPrefs.setDays(context, days) },
        label = StrRetPolicy.days,
        modifier = Modifier.fillMaxWidth(),
        kind = V3FieldKind.NUMBER,
      )
    }
    SettingsNote(StrRetPolicy.blockedNote, Modifier.padding(top = 8.dp))
    SettingsBlock {
      V3TextField(
        value = blocked,
        onValueChange = { v -> blocked = v; ReturnPolicyPrefs.setBlocked(context, v) },
        label = StrRetPolicy.blocked,
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
      )
    }
  }
}
