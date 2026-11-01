package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrRetPolicy
import ir.vil3ntec.tohid.core.returns.ReturnPolicy
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

// i18n: سه‌زبانه

/*
 *  سیاستِ برگشت (RET-09): کادرهای تنظیمات و هشدارِ فرمِ برگشت. خودشان نمی‌خوانند و نمی‌نویسند —
 *  مقدار از صدازننده (`ReturnPolicyPrefs`، `ReturnFlow.policyCheck`).
 */

/** مهلت (روز) و اقلامِ غیرقابلِ برگشت — درونِ بخشِ «برگشت و تعویض» وقتی جریانِ تازه روشن است */
@Composable
fun ReturnPolicySettings(days: String, blocked: String, onDays: (String) -> Unit, onBlocked: (String) -> Unit) {
  val c = Shop.colors
  Spacer(Modifier.height(12.dp))
  Text(StrRetPolicy.daysNote, style = MaterialTheme.typography.labelSmall, color = c.muted)
  Spacer(Modifier.height(6.dp))
  NumberField(value = days, onValueChange = { v -> onDays(v.filter { it in '0'..'9' }) }, label = StrRetPolicy.days, modifier = Modifier.fillMaxWidth())
  Spacer(Modifier.height(12.dp))
  Text(StrRetPolicy.blockedNote, style = MaterialTheme.typography.labelSmall, color = c.muted)
  Spacer(Modifier.height(6.dp))
  OutlinedTextField(
    value = blocked,
    onValueChange = onBlocked,
    label = { Text(StrRetPolicy.blocked) },
    minLines = 2,
    modifier = Modifier.fillMaxWidth(),
  )
}

/**
 *  هشدارِ «بیرون از سیاست» در فرمِ برگشت. شاگرد پایین‌تر «تأییدِ مدیر» را می‌بیند (`ReturnFlow.gate`)؛
 *  مدیر و صاحب «استثنا» را.
 *
 *  @param names نامِ قلم‌های غیرقابلِ برگشت
 *  @param staff کاربر شاگرد است (استثنا با تأییدِ مدیر، نه خودش)
 */
@Composable
fun ReturnPolicyNotice(verdict: ReturnPolicy.Verdict, policy: ReturnPolicy.Policy, names: List<String>, staff: Boolean) {
  if (verdict.ok) return
  Column {
    Spacer(Modifier.height(8.dp))
    if (verdict.late) StatusPill(StrRetPolicy.late(verdict.ageDays ?: 0, policy.days), PillTone.WARNING)
    if (verdict.blockedIds.isNotEmpty()) {
      if (verdict.late) Spacer(Modifier.height(4.dp))
      StatusPill(StrRetPolicy.blockedItems(names.joinToString("، ")), PillTone.WARNING)
    }
    if (!staff) {
      Spacer(Modifier.height(4.dp))
      Text(StrRetPolicy.exception, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
}
