package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ExpiryPref
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  S22 — درونِ بخشِ «کالای منقضی» ِ رابطِ امروز (گوشی و کامپیوتر): روزِ هشدارِ نزدیک به انقضا (`ExpiryPref`، پیش‌فرض
 *  ۳۰ = همان امروز). انتخابِ «با مجوزِ مدیر» کنارِ «فقط هشدار | فروخته نشود» در خودِ بخش است.
 */
@Composable
fun ExpiryDaysSettingsWired(context: android.content.Context) {
  var days by remember { mutableStateOf(ExpiryPref.nearDays(context).toString()) }
  val bad = days.toIntOrNull()?.let { it !in 1..ir.vil3ntec.tohid.data.ExpiryEngine.MAX_NEAR_DAYS } ?: true
  Spacer(Modifier.height(8.dp))
  Text(StrSettings.expiryApprovalNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  Spacer(Modifier.height(8.dp))
  OutlinedTextField(
    value = days,
    onValueChange = { v -> days = ir.vil3ntec.tohid.numericInput(v).filter { it.isDigit() }.take(3); days.toIntOrNull()?.let { ExpiryPref.setNearDays(context, it) } },
    label = { Text(StrSettings.expiryNearDays) },
    singleLine = true,
    isError = bad,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = Modifier.fillMaxWidth(),
  )
}
