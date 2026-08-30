package ir.vil3ntec.tohid.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** کادرِ سفیدِ گرد که هر بخش داخلش می‌نشیند */
@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
  val c = Admin.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(c.surface)
      .border(1.dp, c.border, RoundedCornerShape(18.dp))
      .padding(16.dp),
    content = content,
  )
}

@Composable
fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = Admin.colors.muted,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
  )
}

@Composable
fun Field(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  visual: VisualTransformation = VisualTransformation.None,
  singleLine: Boolean = true,
) {
  val c = Admin.colors
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    singleLine = singleLine,
    keyboardOptions = keyboardOptions,
    visualTransformation = visual,
    shape = RoundedCornerShape(14.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = c.primary,
      unfocusedBorderColor = c.border,
      focusedTextColor = c.text,
      unfocusedTextColor = c.text,
      focusedLabelColor = c.primary,
      unfocusedLabelColor = c.muted,
      cursorColor = c.primary,
    ),
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled && !busy,
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Admin.colors.primary, contentColor = Color.White),
    modifier = modifier.height(50.dp),
  ) {
    if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    else Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
  val c = Admin.colors
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = tint ?: c.text),
    border = androidx.compose.foundation.BorderStroke(1.dp, tint?.copy(alpha = 0.5f) ?: c.border),
    modifier = modifier.height(46.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge)
  }
}

/** برچسبِ رنگیِ وضعیت */
@Composable
fun StatusChip(text: String, color: Color) {
  Box(
    Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(color.copy(alpha = 0.16f))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun Row2(label: String, value: String) {
  val c = Admin.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text(value, style = MaterialTheme.typography.bodySmall, color = c.text, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun ClickRow(title: String, subtitle: String, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
  val c = Admin.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.Medium)
      if (subtitle.isNotBlank()) {
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
    trailing()
  }
}

@Composable
fun ErrorNote(message: String?) {
  message ?: return
  Text(
    message,
    style = MaterialTheme.typography.bodySmall,
    color = Admin.colors.danger,
    modifier = Modifier.padding(vertical = 8.dp),
  )
}

/* ------------------------------ کمکی‌ها ------------------------------ */

/** رقم‌های فارسی — عددِ لاتین وسطِ متنِ فارسی بد می‌نشیند */
fun String.fa(): String = map { c -> if (c in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[c - '0'] else c }.joinToString("")
fun Int.fa(): String = toString().fa()
fun Long.fa(): String = toString().fa()

/**
 *  تاریخِ شمسی از زمانِ **سرور**.
 *
 *  ساعتِ گوشی فقط برای نشان دادن به کار می‌رود؛ هیچ تصمیمی — نه شروع،
 *  نه پایانِ اشتراک — به آن گرفته نمی‌شود. آن تصمیم‌ها همه روی سرورند.
 */
fun jalali(millis: Long): String {
  if (millis <= 0) return "—"
  val cal = Calendar.getInstance().apply { timeInMillis = millis }
  var gy = cal.get(Calendar.YEAR)
  val gm = cal.get(Calendar.MONTH) + 1
  val gd = cal.get(Calendar.DAY_OF_MONTH)

  val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
  var jy = if (gy <= 1600) 0 else 979
  gy -= if (gy <= 1600) 621 else 1600
  val gy2 = if (gm > 2) gy + 1 else gy
  var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + monthDays[gm - 1]

  jy += 33 * (days / 12053); days %= 12053
  jy += 4 * (days / 1461); days %= 1461
  if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }

  val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
  val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
  return "$jy/${jm.toString().padStart(2, '0')}/${jd.toString().padStart(2, '0')}".fa()
}

/** «۱۲ روز مانده» یا «۳ روز گذشته» */
fun daysBetween(from: Long, to: Long): Int =
  ((to - from) / (24L * 3600 * 1000)).toInt()
