package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  رسیدها.
 *
 *  دو چیز که فروشنده آخرِ ماه دنبالشان می‌گردد: پولی که از قرض‌داران
 *  گرفته، و خرجی که کرده — هر دو به تفکیکِ ماه و سالِ خورشیدی.
 *
 *  ماه‌ها از روی خودِ داده ساخته می‌شوند، نه از تقویم: سالی که هیچ رسیدی
 *  ندارد در فهرست نمی‌آید تا کاربر بی‌جهت بگردد.
 */
@Composable
fun ReceiptsScreen(d: ShopData) {
  var tab by rememberSaveable { mutableStateOf("receipts") }
  var who by rememberSaveable { mutableStateOf<String?>(null) }
  var year by rememberSaveable { mutableStateOf<Int?>(null) }
  var month by rememberSaveable { mutableStateOf<Int?>(null) }

  data class Row(
    val id: String,
    val title: String,
    val note: String,
    val amount: Double,
    val date: String,
    val who: String,
    val year: Int,
    val month: Int,
  )

  val all = remember(d, tab) {
    if (tab == "receipts") {
      d.transactions.filter { it.type == "receive" }.mapNotNull { t ->
        val j = Jalali.ofIso(t.date) ?: return@mapNotNull null
        Row(
          id = t.id,
          title = d.debtors.find { it.id == t.debtorId }?.name ?: "قرض‌دار حذف‌شده",
          note = t.notes,
          amount = t.amount,
          date = t.date,
          who = t.debtorId,
          year = j.year,
          month = j.month,
        )
      }
    } else {
      d.expenses.mapNotNull { x ->
        val j = Jalali.ofIso(x.date) ?: return@mapNotNull null
        Row(
          id = x.id,
          title = x.title.ifBlank { "مصرف" },
          note = x.category,
          amount = x.amount,
          date = x.date,
          who = "",
          year = j.year,
          month = j.month,
        )
      }
    }.sortedByDescending { it.date }
  }

  val years = remember(all) { all.map { it.year }.distinct().sortedDescending() }
  val shown = all.filter {
    (who == null || it.who == who) && (year == null || it.year == year) && (month == null || it.month == month)
  }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text("رسیدها", style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
      Text(
        "دریافتی‌های هر قرض‌دار به تفکیک ماه و سال",
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(14.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("receipts" to "رسیدهای قرض‌داران", "expenses" to "مصارف").forEach { (id, label) ->
          FilterChip(
            selected = tab == id,
            onClick = { tab = id; who = null },
            label = { Text(label) },
          )
        }
      }
      Spacer(Modifier.height(12.dp))

      StatTile(
        label = "جمع",
        value = "${money(shown.sumOf { it.amount })} افغانی",
        tint = if (tab == "receipts") Shop.colors.success else Shop.colors.danger,
        hint = "${plain(shown.size)} ردیف",
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
    }

    // قرض‌دار — فقط در تبِ رسیدها معنی دارد
    if (tab == "receipts" && d.debtors.isNotEmpty()) {
      item {
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          FilterChip(selected = who == null, onClick = { who = null }, label = { Text("همه") })
          d.debtors.forEach { debtor ->
            FilterChip(
              selected = who == debtor.id,
              onClick = { who = if (who == debtor.id) null else debtor.id },
              label = { Text(debtor.name) },
            )
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }

    if (years.isNotEmpty()) {
      item {
        /*
         *  سال و ماه، دو کادرِ کشویی کنارِ هم.
         *
         *  قبلاً دوازده ماه یک ردیفِ افقیِ اسکرول‌شونده بودند: نصفشان
         *  بیرونِ صفحه می‌ماند، پیدا کردنِ «عقرب» یعنی کشیدن، و معلوم هم
         *  نبود چندتای دیگر مانده. کادرِ کشویی همه را یک‌جا نشان می‌دهد
         *  و جای ثابتی می‌گیرد.
         */
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PickerBox(
            label = "سال",
            value = year?.let { plain(it) } ?: "همهٔ سال‌ها",
            options = listOf("همهٔ سال‌ها") + years.map { plain(it) },
            onPick = { index ->
              year = if (index == 0) null else years[index - 1]
              if (year == null) month = null
            },
            modifier = Modifier.weight(1f),
          )
          PickerBox(
            label = "ماه",
            value = month?.let { JALALI_MONTHS[it - 1] } ?: "همهٔ ماه‌ها",
            options = listOf("همهٔ ماه‌ها") + JALALI_MONTHS,
            onPick = { index -> month = if (index == 0) null else index },
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(14.dp))
      }
    }

    if (shown.isEmpty()) {
      item {
        Panel {
          EmptyNote(
            if (tab == "receipts") "رسیدی با این فیلترها پیدا نشد."
            else "مصرفی با این فیلترها پیدا نشد."
          )
        }
      }
    } else {
      items(shown, key = { it.id }) { row ->
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Shop.colors.surface)
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text(
              "${formatDate(row.date)}${if (row.note.isNotBlank()) " — ${row.note}" else ""}",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted,
            )
          }
          Text(
            "${money(row.amount)} افغانی",
            style = MaterialTheme.typography.titleSmall,
            color = if (tab == "receipts") Shop.colors.success else Shop.colors.danger,
          )
        }
        Spacer(Modifier.height(6.dp))
      }
    }
  }
}

/** نام ماه‌های خورشیدی — همان فهرستی که نسخهٔ وب دارد */
val JALALI_MONTHS = listOf(
  "حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله",
  "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت",
)

/**
 *  کادرِ کشویی برای فیلترهای این صفحه.
 *
 *  انتخاب با **شماره** است نه با متن: دو گزینه ممکن است روزی یک نام
 *  پیدا کنند و آن‌وقت انتخاب با متن، اشتباهی را برمی‌گرداند که پیدا
 *  کردنش سخت است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBox(
  label: String,
  value: String,
  options: List<String>,
  onPick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = open,
    onExpandedChange = { open = it },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      shape = RoundedCornerShape(Radius.sm),
      trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      options.forEachIndexed { index, option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = { onPick(index); open = false },
        )
      }
    }
  }
}
