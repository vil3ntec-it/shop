package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کادرهای ورودیِ صفحه‌های «محصول جدید» و «ورود کالا به انبار».
 *
 *  **فقط همین دو صفحه از این‌ها استفاده می‌کنند.** بقیهٔ برنامه دست‌نخورده
 *  می‌ماند و کادرهای خودش را دارد؛ اگر روزی اینجا چیزی عوض شود، جای دیگری
 *  تکان نمی‌خورد.
 *
 *  چرا `BasicTextField` و نه `OutlinedTextField`: در حالتِ خطی، برچسب
 *  وسطِ خطِ بالا می‌نشیند و کادر را می‌شکند، و ارتفاعش هم دستِ خودمان
 *  نیست. اینجا برچسب **بالای** کادر است و خودِ کادر یک مستطیلِ تمیزِ
 *  سفید با حاشیهٔ نازک — همان چیزی که در تصویر مرجع است.
 *
 *  حالتِ فوکوس با دو چیز نشان داده می‌شود: حاشیه پررنگ‌تر و کمی ضخیم‌تر.
 *  فقط رنگ کافی نیست؛ کسی که چشمش رنگ را خوب تشخیص نمی‌دهد هم باید
 *  بفهمد کدام کادر باز است.
 */
@Composable
fun EntryField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  trailing: @Composable (() -> Unit)? = null,
) {
  val colors = Shop.colors
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  val line by animateColorAsState(
    targetValue = if (focused) colors.fieldFocus else colors.fieldBorder,
    animationSpec = tween(if (Motion.enabled) 160 else 0),
    label = "fieldBorder",
  )
  val width by animateDpAsState(
    targetValue = if (focused) 1.8.dp else 1.dp,
    animationSpec = tween(if (Motion.enabled) 160 else 0),
    label = "fieldWidth",
  )

  Column(modifier) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = if (focused) colors.fieldFocus else colors.muted,
    )
    Spacer(Modifier.height(6.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(colors.fieldBg)
        .border(width, line, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
          Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.muted2)
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          interactionSource = interaction,
          keyboardOptions = keyboardOptions,
          textStyle = LocalTextStyle.current.merge(
            MaterialTheme.typography.bodyMedium.copy(color = colors.text)
          ),
          cursorBrush = SolidColor(colors.fieldFocus),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (trailing != null) {
        Spacer(Modifier.width(8.dp))
        trailing()
      }
    }
  }
}

/** همان کادر، با صفحه‌کلیدِ عددی — برای قیمت، مقدار و حداقل موجودی */
@Composable
fun EntryNumberField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String = "۰",
) {
  EntryField(
    // فقط رقم و یک نقطه؛ حروف در کادرِ قیمت معنی ندارند و بعداً
    // موقع تبدیل به عدد بی‌صدا صفر می‌شدند.
    // رقمِ فارسی و عربی **دور ریخته نمی‌شود**، به لاتین برمی‌گردد — دقیقاً
    // همان کاری که کادرِ عددِ بقیهٔ برنامه می‌کند؛ وگرنه کسی که با
    // صفحه‌کلیدِ فارسی می‌نویسد، هرچه بزند کادر خالی می‌ماند.
    value = value,
    onValueChange = { fresh ->
      onValueChange(entryLatinDigits(fresh).filter { it.isDigit() || it == '.' })
    },
    label = label,
    modifier = modifier,
    placeholder = placeholder,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
  )
}

/**
 *  کادرِ خالی با برچسب، برای چیزی که خودش نوشتنی نیست — مثلِ ردیفِ
 *  تراشه‌های «انتخاب محصول».
 *
 *  فقط یک قاب است: هرچه داخلش بود همان‌طور کار می‌کند و رفتارش عوض
 *  نمی‌شود؛ کاربر فقط می‌بیند این قسمت هم یک خانهٔ فرم است، نه چند دکمهٔ
 *  شناور روی زمینه.
 */
@Composable
fun EntryFieldBox(
  label: String,
  modifier: Modifier = Modifier,
  trailing: @Composable (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val colors = Shop.colors
  Column(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.muted,
        modifier = Modifier.weight(1f),
      )
      if (trailing != null) trailing()
    }
    Spacer(Modifier.height(6.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(colors.fieldBg)
        .border(1.dp, colors.fieldBorder, RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.CenterStart,
    ) { content() }
  }
}

/**
 *  تاریخِ همهٔ ردیف‌ها.
 *
 *  **رفتارش مو‌به‌مو همان `DateField` مشترک است** — همان فیلترِ ورودی،
 *  همان دو دکمهٔ «امروز» و «دیروز»، همان تاریخِ خورشیدیِ زیرِ کادر. فقط
 *  ظاهرِ کادر عوض شده تا با بقیهٔ کادرهای این دو صفحه یکی باشد.
 *
 *  عمداً اینجا نوشته شده و `DateField` دست نخورده: آن یکی در چند صفحهٔ
 *  دیگر هم هست و نباید با این تغییر تکان بخورد.
 */
@Composable
fun EntryDateField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String = "تاریخ",
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EntryField(
        value = value,
        onValueChange = { onValueChange(entryLatinDigits(it).filter { c -> c.isDigit() || c == '-' }) },
        label = label,
        placeholder = "۱۴۰۵-۰۶-۰۶",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { onValueChange(todayIso()) }) { Text("امروز") }
      TextButton(onClick = { onValueChange(entryYesterdayIso()) }) { Text("دیروز") }
    }
    if (value.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(
        formatDate(value),
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
      )
    }
  }
}

/** رقمِ فارسی و عربی به لاتین. نسخهٔ خودیِ همین فایل تا چیزی مشترک نشود. */
private fun entryLatinDigits(text: String): String = text.map { c ->
  when (c) {
    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
    '\u066B', '\u066C' -> '.'
    else -> c
  }
}.joinToString("")

private fun entryYesterdayIso(): String {
  val c = java.util.Calendar.getInstance()
  c.add(java.util.Calendar.DAY_OF_MONTH, -1)
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}
