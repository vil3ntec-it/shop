package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.CustomerProfileForm
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  پروندهٔ مشتری در صفحه‌های قدیم (گوشی: `DebtorsScreen`؛ کامپیوتر: `DeskCustomersScreen`) —
 *  CRM-02 (شمارهٔ دوم)، CRM-10 (ادغامِ تکراری‌ها)، CRM-11 (برچسب و صافیِ برچسب).
 *
 *  همه فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض **خاموش**)؛ خاموش ⇒ همان
 *  صفحه‌های امروز. منطق در `CustomerProfileForm`، `CustomerCrm` و `CustomerMerge` است؛ ادغام فقط از
 *  درِ `ShopStore.mergeCustomers` (روی همگام‌سازیِ v3 بسته، با همان دلیل). همان رفتارِ برگه‌های نسخهٔ ۳ (#393).
 */

/** دو کادرِ پرونده در فرمِ مشتری: شمارهٔ دوم و برچسب‌ها */
@Composable
internal fun CrmProfileFields(phone2: String, onPhone2: (String) -> Unit, tags: String, onTags: (String) -> Unit) {
  OutlinedTextField(
    phone2, onPhone2, label = { Text("شمارهٔ دوم (اختیاری)") },
    singleLine = true, modifier = Modifier.fillMaxWidth(),
  )
  Spacer(Modifier.height(10.dp))
  OutlinedTextField(
    tags, onTags, label = { Text("برچسب‌ها (با «،» جدا؛ اختیاری)") },
    singleLine = true, modifier = Modifier.fillMaxWidth(),
  )
}

/** صافیِ برچسب بالای فهرست — فقط وقتی دست‌کم یک مشتریِ فعال برچسب دارد */
@Composable
internal fun CrmTagChips(d: ShopData, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
  val tags = remember(d) { CustomerCrm.allTags(d) }
  if (tags.isEmpty()) return
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("همهٔ برچسب‌ها") })
    tags.forEach { (tag, count) ->
      FilterChip(
        selected = selected == tag,
        onClick = { onSelect(if (selected == tag) null else tag) },
        label = { Text("#$tag ($count)") },
      )
    }
  }
}

/** ابزارِ فهرست با CRM روشن: صافیِ برچسب و «ادغامِ مشتریانِ تکراری (n)» */
@Composable
internal fun CrmListTools(d: ShopData, tag: String?, onTag: (String?) -> Unit, onMerge: () -> Unit) {
  CrmTagChips(d, tag, onTag)
  val dupes = remember(d) { CustomerMerge.duplicates(d).size }
  if (dupes > 0) TextButton(onClick = onMerge) { Text("ادغامِ مشتریانِ تکراری ($dupes)") }
  Spacer(Modifier.height(6.dp))
}

/** شمارهٔ دوم و برچسب‌های مشتری زیرِ نامش — فقط با «گزارش‌های مشتری» روشن */
@Composable
internal fun CrmExtraLine(debtor: Debtor) {
  val on = ShopPolicy.crmReports(LocalContext.current)
  val line = if (on) CustomerProfileForm.extraLine(debtor) else ""
  if (line.isBlank()) return
  Spacer(Modifier.height(4.dp))
  Text(line, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
}

/** پنجرهٔ پروندهٔ کامپیوتر: شمارهٔ دوم و برچسب‌های یک مشتری */
@Composable
internal fun CrmProfileDialog(debtor: Debtor, onDismiss: () -> Unit, onSave: (phone2: String, tags: String) -> Unit) {
  var phone2 by remember(debtor.id) { mutableStateOf(debtor.phone2.orEmpty()) }
  var tags by remember(debtor.id) { mutableStateOf(CustomerProfileForm.tagsText(debtor)) }
  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text("پروندهٔ «${debtor.name}»", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Spacer(Modifier.height(14.dp))
          CrmProfileFields(phone2, { phone2 = it }, tags, { tags = it })
          Spacer(Modifier.height(18.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
            Button(onClick = { onSave(phone2, tags) }, modifier = Modifier.weight(1f)) { Text("ذخیره") }
          }
        }
      }
    }
  }
}

/**
 *  «ادغامِ تکراری‌ها» (CRM-10): گروه‌های `CustomerMerge.duplicates` — اول شمارهٔ یکسان (قوی)، بعد
 *  نامِ یکسان (شاید دو نفرِ جدا). سرِ هر گروه «ماندگار»؛ «ادغام» روی عضوِ دیگر ⇒ پیش‌نمایش
 *  (`CustomerMerge.preview`) و تأیید ⇒ `ShopStore.mergeCustomers`. هیچ ردیفی پاک نمی‌شود.
 */
@Composable
internal fun CustomerMergeDialog(d: ShopData, store: ShopStore, onDismiss: () -> Unit, toast: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  val groups = remember(d) { CustomerMerge.duplicates(d) }
  val blocked = remember { store.mergeBlockedReason() }
  var pending by remember { mutableStateOf<CustomerMerge.Preview?>(null) }
  var busy by remember { mutableStateOf(false) }
  val c = Shop.colors

  Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
    DialogEntry {
      Surface(color = c.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
          Text("ادغامِ مشتریانِ تکراری", style = MaterialTheme.typography.titleMedium, color = c.text)
          Spacer(Modifier.height(10.dp))
          val p = pending
          when {
            p != null -> {
              Text("«${p.drop.name}» در «${p.keep.name}» ادغام شود؟", style = MaterialTheme.typography.bodyLarge, color = c.text)
              Spacer(Modifier.height(8.dp))
              Text(
                "${p.transactions} ردیفِ حساب و ${p.sales} فاکتور به «${p.keep.name}» می‌رود و ماندهٔ او ${money(p.balanceAfter)} ؋ می‌شود. " +
                  "هیچ ردیفی پاک نمی‌شود؛ «${p.drop.name}» فقط از فهرست‌ها بیرون می‌رود.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
              )
              Spacer(Modifier.height(14.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pending = null }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("برگشت") }
                Button(
                  enabled = !busy,
                  onClick = {
                    busy = true
                    scope.launch {
                      when (val r = store.mergeCustomers(p.keep.id, p.drop.id)) {
                        is CustomerMerge.Result.Ok -> { toast("«${p.drop.name}» در «${p.keep.name}» ادغام شد"); pending = null }
                        is CustomerMerge.Result.Failed -> toast(r.message)
                      }
                      busy = false
                    }
                  },
                  modifier = Modifier.weight(1f),
                ) { Text("ادغام") }
              }
            }
            groups.isEmpty() -> Text("مشتریِ تکراری پیدا نشد.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            else -> {
              blocked?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(8.dp))
              }
              groups.forEachIndexed { gi, g ->
                if (gi > 0) HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                  if (g.strong) "شمارهٔ یکسان" else "نامِ یکسان — شاید دو نفرِ جدا",
                  style = MaterialTheme.typography.labelLarge, color = c.muted,
                )
                g.members.forEach { m ->
                  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                      Text(m.name, style = MaterialTheme.typography.bodyMedium, color = c.text)
                      val phones = CustomerCrm.phones(m).joinToString(" · ") { LocaleText.phoneDisplay(it) }
                      if (phones.isNotBlank()) Text(phones, style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                    when {
                      m.id == g.keep.id -> Text("ماندگار", style = MaterialTheme.typography.labelMedium, color = c.muted)
                      blocked == null -> TextButton(onClick = {
                        pending = (CustomerMerge.preview(d, g.keep.id, m.id) as? CustomerMerge.Result.Ok)?.preview
                      }) { Text("ادغام در ماندگار") }
                    }
                  }
                }
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("بستن") }
          }
        }
      }
    }
  }
}
