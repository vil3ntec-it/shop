package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStaffTime
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کارتِ «ورود و خروج»، «۷ روز و این ماه» و «پورسانتِ این ماه» در «مدیریت» (STF-07، STF-08). فقط نمایش است:
 *  داده و کارها از `StaffTimeAndroid.kt` می‌آیند و این‌جا هیچ تنظیم یا شبکه‌ای خوانده نمی‌شود.
 *  فقط وقتی پرچمِ `shop.staff_time` ِ دکان روشن است کشیده می‌شود.
 */
data class StaffTimeUi(
  val myOpenSince: String?,
  val queued: Int,
  val busy: Boolean,
  val message: String?,
  val manager: Boolean,
  val team: List<TeamRow>,
  val issues: Int,
  /** «۷ روز و این ماه» ِ هر کارمند (STF-07)؛ فقط برای صاحب و مدیر */
  val periods: List<PeriodRow>,
  val commission: List<SellerRow>?,
  val unassigned: String?,
  /** نرخ‌ها برای ویرایش — فقط صاحب (`null` ⇒ بی ویرایشگر) */
  val plan: Commission.Plan?,
  /** دسته‌های کالا برای «نرخِ دسته» (STF-08) */
  val categories: List<String> = emptyList(),
)

data class TeamRow(val memberId: String, val name: String, val worked: String, val open: Boolean)

/** [late]: زدن‌های همین ماه که سرور دیر گرفت (STF-07، «دیر ثبت‌شده»)؛ فقط برای صاحب و مدیر */
data class PeriodRow(val name: String, val week: String, val month: String, val issues: Int, val late: Int = 0)

data class SellerRow(val seller: String, val base: String, val commission: String)

@Composable
fun StaffTimeCard(
  ui: StaffTimeUi,
  onPunch: () -> Unit,
  onClockOutFor: (TeamRow) -> Unit,
  onSaveRates: (Commission.Plan) -> Unit,
) {
  Panel {
    Text(StrStaffTime.title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(ui.myOpenSince?.let { StrStaffTime.inSince(it) } ?: StrStaffTime.out, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onPunch, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
      Text(if (ui.myOpenSince != null) StrStaffTime.clockOut else StrStaffTime.clockIn)
    }
    if (ui.queued > 0) StaffNote(StrStaffTime.queued(ui.queued))
    ui.message?.let { StaffNote(it, warning = true) }

    if (ui.manager) {
      Spacer(Modifier.height(10.dp))
      Text(StrStaffTime.team, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      if (ui.team.isEmpty()) StaffNote(StrStaffTime.noPunches)
      ui.team.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(r.name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Text(if (r.open) "${r.worked} · ${StrStaffTime.stillIn}" else r.worked, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, fontWeight = FontWeight.Bold)
        }
        if (r.open) TextButton(onClick = { onClockOutFor(r) }, enabled = !ui.busy) { Text(StrStaffTime.clockOutFor(r.name)) }
      }
      if (ui.issues > 0) StaffNote(StrStaffTime.issues(ui.issues), warning = true)

      if (ui.periods.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(StrStaffTime.periods, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
        ui.periods.forEach { r ->
          Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(r.name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(StrStaffTime.weekWorked(r.week), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text(r.month, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, fontWeight = FontWeight.Bold)
            if (r.issues > 0) StaffNote(StrStaffTime.issues(r.issues), warning = true)
            if (r.late > 0) StaffNote(StrStaffTime.lateRecorded(r.late), warning = true)
          }
        }
      }
    }

    ui.commission?.let { rows ->
      Spacer(Modifier.height(10.dp))
      Text(StrStaffTime.commission, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      if (rows.isEmpty()) StaffNote(StrStaffTime.noSales)
      rows.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("${r.seller} · ${r.base}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Text(r.commission, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.primary, fontWeight = FontWeight.Bold)
        }
      }
      ui.unassigned?.let { StaffNote(StrStaffTime.unassigned(it)) }
      ui.plan?.let { StaffRatesEditor(it, rows.map { r -> r.seller }, ui.categories, ui.busy, onSaveRates) }
    }
  }
}

@Composable
private fun StaffRatesEditor(plan: Commission.Plan, sellers: List<String>, categories: List<String>, busy: Boolean, onSave: (Commission.Plan) -> Unit) {
  var open by remember { mutableStateOf(false) }
  TextButton(onClick = { open = !open }) { Text(StrStaffTime.rates) }
  if (open) StaffRatesFields(plan, sellers, categories, busy, onSave)
}

@Composable
private fun StaffRatesFields(plan: Commission.Plan, sellers: List<String>, categories: List<String>, busy: Boolean, onSave: (Commission.Plan) -> Unit) {
  var all by remember(plan) { mutableStateOf(Commission.percentOf(plan.defaultBp)) }
  fun fields(keys: List<String>, rates: Map<String, Int>) =
    mutableStateMapOf<String, String>().apply { keys.forEach { k -> rates[k]?.let { put(k, Commission.percentOf(it)) } } }
  val each = remember(plan, sellers) { fields(sellers, plan.sellerBp) }
  val byCat = remember(plan, categories) { fields(categories, plan.categoryBp) }
  var invalid by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    StaffRate(StrStaffTime.defaultRate, all) { all = it }
    sellers.forEach { s -> StaffRate(StrStaffTime.sellerRate(s), each[s].orEmpty()) { each[s] = it } }
    //  STF-08: نرخِ دسته بر نرخِ فروشنده و نرخِ همه پیشی دارد؛ کادرِ خالی ⇒ نرخِ فروشنده
    if (categories.isNotEmpty()) StaffNote(StrStaffTime.categoryHint)
    categories.forEach { c -> StaffRate(StrStaffTime.categoryRate(c), byCat[c].orEmpty()) { byCat[c] = it } }
    if (invalid) StaffNote(StrStaffTime.rateInvalid, warning = true)
    Button(enabled = !busy, onClick = {
      val def = Commission.bpOfPercent(all)
      //  کادرِ خالی ⇒ همان نرخِ همه (یا نرخِ فروشنده برای دسته)، نه صفر
      fun parsed(m: Map<String, String>) = m.filterValues { it.isNotBlank() }.mapValues { Commission.bpOfPercent(it.value) }
      val per = parsed(each)
      val cat = parsed(byCat)
      if (def == null || per.values.any { it == null } || cat.values.any { it == null }) { invalid = true; return@Button }
      invalid = false
      onSave(Commission.Plan(
        def,
        plan.sellerBp.filterKeys { it !in sellers } + per.mapValues { it.value!! },
        plan.categoryBp.filterKeys { it !in categories } + cat.mapValues { it.value!! },
      ))
    }) { Text(StrStaffTime.save) }
  }
}

@Composable
private fun StaffRate(label: String, value: String, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value, onValueChange = { if (it.length <= 6) onChange(it) }, label = { Text(label) }, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun StaffNote(text: String, warning: Boolean = false) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) Shop.colors.warning else Shop.colors.muted, modifier = Modifier.padding(top = 4.dp))
}
