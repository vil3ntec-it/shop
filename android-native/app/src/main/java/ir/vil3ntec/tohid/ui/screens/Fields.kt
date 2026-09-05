package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کادرهای مشترکِ فرم‌ها.
 *
 *  یک جا نوشته شده‌اند تا همه‌جای برنامه یک‌شکل رفتار کنند: عدد همه‌جا
 *  همان‌طور پذیرفته شود، و تاریخ همه‌جا همان‌طور نوشته و نشان داده شود.
 */

/**
 * کادرِ عدد.
 *
 * رقمِ فارسی و عربی به لاتین برمی‌گردد، چون بعضی صفحه‌کلیدها فارسی
 * می‌فرستند و `toDouble` آن را نمی‌شناسد — عددی که کاربر دیده و نوشته
 * نباید بی‌صدا صفر حساب شود.
 */
@Composable
fun NumberField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { raw -> onValueChange(latinDigits(raw).filter { it.isDigit() || it == '.' }) },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}

/** مثل بالا، با نمایشِ مبلغ به شکلِ خوانا زیرِ کادر */
@Composable
fun AmountField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier.fillMaxWidth(),
) {
  val parsed = value.toDoubleOrNull()
  Column(modifier) {
    NumberField(value, onValueChange, label, Modifier.fillMaxWidth())
    if (parsed != null && parsed > 0) {
      Spacer(Modifier.height(4.dp))
      Text(
        "${money(parsed)} ؋",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
      )
    }
  }
}

/**
 *  تاریخ — همان چیزی که کاربر می‌بیند، همان چیزی که می‌نویسد.
 *
 *  ── چه اشکالی را می‌بندد ───────────────────────────────────────────
 *  این کادر راهنمای `۱۴۰۵/۰۶/۰۶` نشان می‌داد — تاریخِ **خورشیدی** با
 *  اسلش — ولی فیلتر فقط رقم و خط‌تیره را نگه می‌داشت و مقدارِ ذخیره‌شده
 *  باید **میلادیِ** `YYYY-MM-DD` می‌بود.
 *
 *  یعنی کسی که همان چیزی را می‌زد که راهنما نشانش داده بود، رشتهٔ
 *  بی‌معنیِ `14050606` ذخیره می‌کرد — و آن ردیف بی‌صدا از هر گزارشِ
 *  بازه‌ای بیرون می‌افتاد. دو دکمهٔ «امروز» و «دیروز» درست کار می‌کردند و
 *  همین پنهانش کرده بود.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  حالا کادر خورشیدی می‌گیرد و خورشیدی نشان می‌دهد؛ تبدیل به میلادی
 *  همین‌جا انجام می‌شود و در فایل همان `YYYY-MM-DD`ِ همیشگی می‌نشیند —
 *  پس داده بین نسخهٔ وب و این نسخه همچنان یکی است. کسی که میلادی بنویسد
 *  هم پذیرفته می‌شود؛ `Jalali.parseTyped` از روی سالِ چهاررقمی می‌فهمد.
 */
@Composable
fun DateField(value: String, onValueChange: (String) -> Unit) {
  //  کادر متنِ خودش را دارد تا وسطِ تایپ کردن، هر ضربهٔ کلید تاریخِ
  //  نیمه‌کاره را به بیرون نفرستد
  var text by remember { mutableStateOf(if (value.isBlank()) "" else formatDate(value)) }

  //  مقدار از بیرون عوض شد (دکمهٔ امروز، یا باز شدنِ فرم روی رکوردِ
  //  دیگر) — کادر دنبالش می‌آید، ولی نه وقتی خودش همان را نشان می‌دهد
  LaunchedEffect(value) {
    if (Jalali.parseTyped(text) != value) {
      text = if (value.isBlank()) "" else formatDate(value)
    }
  }

  val parsed = remember(text) { Jalali.parseTyped(text) }
  val broken = text.isNotBlank() && parsed == null

  Text("تاریخ", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = text,
      onValueChange = { raw ->
        text = latinDigits(raw).filter { c -> c.isDigit() || c == '/' || c == '-' }
        Jalali.parseTyped(text)?.let(onValueChange)
      },
      placeholder = { Text("۱۴۰۵/۰۶/۰۶") },
      singleLine = true,
      isError = broken,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      supportingText = {
        Text(
          if (broken) "تاریخ خوانده نشد — مثل ۱۴۰۵/۰۶/۰۶ بنویسید" else "تاریخ خورشیدی",
          style = MaterialTheme.typography.labelSmall,
        )
      },
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = { onValueChange(todayIso()) }) { Text("امروز") }
    TextButton(onClick = { onValueChange(yesterdayIso()) }) { Text("دیروز") }
  }
}

/** انتخاب از فهرست، با امکانِ نوشتنِ مقدارِ تازه */
@Composable
fun ChipPicker(
  title: String,
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  placeholder: String,
) {
  var adding by remember { mutableStateOf(false) }
  var fresh by remember { mutableStateOf("") }

  Text(title, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))

  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // مقدارِ فعلی ممکن است در فهرست نباشد (از نسخهٔ قبلی آمده)
    val all = if (selected.isNotBlank() && !options.contains(selected)) options + selected else options
    all.forEach { option ->
      FilterChip(
        selected = selected == option,
        onClick = { onSelect(if (selected == option) "" else option) },
        label = { Text(option) },
      )
    }
    AssistChip(onClick = { adding = !adding }, label = { Text("+ تازه") })
  }

  if (adding) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = fresh,
        onValueChange = { fresh = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      Button(onClick = {
        val v = fresh.trim()
        if (v.isNotEmpty()) {
          onSelect(v)
          fresh = ""
          adding = false
        }
      }) { Text("افزودن") }
    }
  }
}

@Composable
fun CategoryPicker(options: List<String>, selected: String, onSelect: (String) -> Unit) =
  ChipPicker("دسته‌بندی", options, selected, onSelect, "دسته‌بندی تازه")

/**
 *  انتخابِ یکی از چند چیزِ نام‌دار — تأمین‌کننده، محصول، قرض‌دار.
 *
 *  ── چه اشکالی را می‌بندد ───────────────────────────────────────────
 *  این یک `DropdownMenu`ِ ساده بود که **همهٔ** گزینه‌ها را پشتِ سرِ هم
 *  می‌ریخت. برای انتخابِ قرض‌دار جای دیگری یک پنجرهٔ جستجودار ساخته شده
 *  بود، ولی صفحهٔ خرید و تأمین‌کننده از همین فهرستِ بی‌انتها استفاده
 *  می‌کرد. از حدودِ پنجاه کالا به بعد آزاردهنده می‌شد و با سیصد کالا
 *  عملاً غیرقابلِ استفاده.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  حالا یک پنجره با کادرِ جستجو باز می‌شود و فهرست با هر حرف کوتاه‌تر
 *  می‌شود. تا ده گزینه، جستجو اصلاً نشان داده نمی‌شود — کادرِ خالی روی
 *  فهرستِ کوتاه فقط شلوغی است.
 */
@Composable
fun <T> NamedPicker(
  title: String,
  options: List<T>,
  selectedId: String?,
  idOf: (T) -> String,
  nameOf: (T) -> String,
  emptyNote: String,
  onSelect: (String) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  val current = options.find { idOf(it) == selectedId }

  Text(title, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))
  OutlinedButton(
    onClick = { if (options.isNotEmpty()) open = true },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(current?.let(nameOf) ?: if (options.isEmpty()) emptyNote else "انتخاب کنید")
  }

  if (open) {
    SearchablePicker(
      title = title,
      options = options,
      idOf = idOf,
      nameOf = nameOf,
      onClose = { open = false },
      onPick = { onSelect(it); open = false },
    )
  }
}

/** پنجرهٔ انتخاب با جستجو — تنها جایی که فهرستِ بلند قابلِ تحمل می‌شود */
@Composable
fun <T> SearchablePicker(
  title: String,
  options: List<T>,
  idOf: (T) -> String,
  nameOf: (T) -> String,
  onClose: () -> Unit,
  onPick: (String) -> Unit,
) {
  var search by remember { mutableStateOf("") }
  val term = search.trim()
  val shown = remember(options, term) {
    if (term.isBlank()) options
    else options.filter { nameOf(it).contains(term, ignoreCase = true) }
  }

  Dialog(onDismissRequest = onClose) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(Radius.lg),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))

        if (options.size > 10) {
          OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("جستجو") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(10.dp))
        }

        if (shown.isEmpty()) {
          Text(
            "چیزی با این جستجو پیدا نشد",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
        } else {
          LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown, key = { idOf(it) }) { option ->
              Text(
                nameOf(option),
                style = MaterialTheme.typography.bodyMedium,
                color = Shop.colors.text,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onPick(idOf(option)) }
                  .padding(vertical = 12.dp),
              )
              HorizontalDivider(color = Shop.colors.border)
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("انصراف") }
      }
    }
  }
}

private fun yesterdayIso(): String {
  val c = java.util.Calendar.getInstance()
  c.add(java.util.Calendar.DAY_OF_MONTH, -1)
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}
