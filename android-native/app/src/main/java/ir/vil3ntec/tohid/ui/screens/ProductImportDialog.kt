package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.product.ProductImportPlan.Status
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  ورودِ کالا از Excel/CSV با پیش‌نمایش — PRD-20، فقط با `ShopPolicy.productTools` (گوشی و کامپیوتر).
 *
 *  همهٔ سنجش در `ProductImportPlan` ِ core؛ این‌جا فقط شمارها، فهرست‌ها و دو تیک:
 *  • «کالاهای موجود را هم به‌روز کن» (پیش‌فرض خاموش؛ برای شاگرد نیست) — بی آن هیچ کالای موجودی دست نمی‌خورد.
 *  • «تغییرها را دیدم» — به‌روزرسانی بی آن ثبت نمی‌شود.
 *  ردیف‌های رد با دلیل، و «فایلِ ردیف‌های رد» (xlsx) برای اصلاح و تلاشِ دوباره. ثبت یک بار: دکمه پس از زدن
 *  خاموش می‌شود و موتور هم پیش‌نمایشِ کهنه را رد می‌کند (`WarehouseEngine.importProducts`).
 */
@Composable
fun ProductImportDialog(
  d: ShopData,
  file: ProductImport.Picked,
  onDismiss: () -> Unit,
  onErrorReport: (ByteArray, String) -> Unit,
  onDone: (WarehouseEngine.Result) -> Unit,
) {
  val c = Shop.colors
  val context = LocalContext.current
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  val staff = remember { ShopRole.current(context) == ShopRole.STAFF }
  var update by remember { mutableStateOf(false) }
  val plan = remember(d, file, update) { ProductImport.planOf(d, file.rows, update) }
  var ack by remember(plan) { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  val previous = remember(d, plan.fingerprint) { ProductImport.previous(d, plan.fingerprint) }

  val fresh = plan.count(Status.NEW)
  val updates = plan.count(Status.UPDATE)
  val kept = plan.count(Status.KEPT)
  val errors = plan.rows.filter { it.status == Status.ERROR }
  val ready = !busy && plan.problem == null && plan.writes > 0 && (updates == 0 || ack)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("ورود کالا از فایل", color = c.text) },
    text = {
      Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        Text(file.name, style = MaterialTheme.typography.labelSmall, color = c.muted)
        Spacer(Modifier.height(6.dp))
        plan.problem?.let { Line(it, c.danger); return@Column }

        previous?.let { p ->
          Line("همین داده ${formatDate(p.date)} وارد شده بود — آنچه ثبت شده دوباره ثبت نمی‌شود.", c.warning)
          Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatusPill("${plain(fresh)} تازه", PillTone.SUCCESS)
          if (updates > 0) StatusPill("${plain(updates)} به‌روز", PillTone.INFO)
          if (kept > 0) StatusPill("${plain(kept)} در دکان، دست نمی‌خورد", PillTone.WARNING)
          val same = plan.count(Status.SAME)
          if (same > 0) StatusPill("${plain(same)} بی تغییر", PillTone.NEUTRAL)
          if (errors.isNotEmpty()) StatusPill("${plain(errors.size)} رد", PillTone.DANGER)
        }
        if (plan.ignored.isNotEmpty()) {
          Spacer(Modifier.height(4.dp))
          Line("ستون‌های نادیده: ${plan.ignored.joinToString("، ")}", c.muted)
        }

        if (!staff && (kept > 0 || updates > 0)) {
          Spacer(Modifier.height(6.dp))
          Tick(update, "کالاهای موجود را هم به‌روز کن") { update = it }
          Line(
            "فقط قیمت، دسته، حداقلِ موجودی و بارکدِ تازه؛ خانهٔ خالی، واحد و نام دست نمی‌خورند و چیزی پاک نمی‌شود. " +
              "هر تغییرِ قیمت با نامِ شما و نامِ فایل در سابقه می‌ماند.",
            c.muted,
          )
        }

        val changed = plan.rows.filter { it.status == Status.UPDATE || it.status == Status.KEPT }
        if (changed.isNotEmpty()) {
          Spacer(Modifier.height(6.dp))
          Line(
            if (updates > 0) "تغییرهای کالاهای موجود:" else "در دکان هست و فایل چیزِ دیگری می‌گوید — دست نمی‌خورد:",
            if (updates > 0) c.text else c.muted,
          )
          changed.take(50).forEach { r ->
            Line(
              "ردیف ${plain(r.line)} «${r.name}»: ${r.changes.joinToString("، ") { ProductImport.describe(it) }}",
              if (updates > 0) c.info else c.muted,
            )
            r.notes.forEach { Line(it, c.warning) }
          }
          More(changed.size, 50)
          if (updates > 0) Tick(ack, "تغییرها را دیدم؛ همین‌ها را بگذار") { ack = it }
        }
        //  واحدِ متفاوتِ کالایی که جز آن تغییری ندارد هم گفته شود
        plan.rows.filter { it.status == Status.SAME && it.notes.isNotEmpty() }.take(20).forEach { r ->
          Line("ردیف ${plain(r.line)} «${r.name}»: ${r.notes.joinToString("، ")}", c.warning)
        }

        if (errors.isNotEmpty()) {
          Spacer(Modifier.height(6.dp))
          Line("${plain(errors.size)} ردیف ثبت نمی‌شود:", c.text)
          errors.take(50).forEach { r ->
            Line("ردیف ${plain(r.line)}${if (r.name.isNotBlank()) " «${r.name.take(60)}»" else ""}: ${r.reason}", c.danger)
          }
          More(errors.size, 50)
          TextButton(onClick = {
            val base = file.name.substringBeforeLast('.').ifBlank { "کالا" }
            onErrorReport(ProductImport.errorWorkbook(plan), "$base - ردیف‌های رد.xlsx")
          }) { Text("دریافتِ فایلِ ردیف‌های رد (برای اصلاح و ورودِ دوباره)") }
        }

        //  PRD-19: ثبت می‌شوند، ولی شاید همان کالای دیگرند
        if (plan.similar.isNotEmpty()) {
          Spacer(Modifier.height(6.dp))
          Line("${plain(plan.similar.size)} ردیف ثبت می‌شود ولی نامش مشابه است — اگر همان کالاست، از فایل بردارید:", c.warning)
          plan.similar.take(50).forEach { sm ->
            Line("ردیف ${plain(sm.line)} «${sm.name}» ≈ «${sm.other}»${if (sm.inFile) " (در همین فایل)" else ""}", c.warning)
          }
        }
      }
    },
    confirmButton = {
      Button(
        enabled = ready,
        onClick = {
          busy = true
          onDone(
            WarehouseEngine.importProducts(
              d, file.rows, plan, file.name, me, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId,
            )
          )
        },
      ) {
        Text(
          when {
            updates > 0 && fresh > 0 -> "ثبت ${plain(fresh)} کالا و به‌روزرسانیِ ${plain(updates)}"
            updates > 0 -> "به‌روزرسانیِ ${plain(updates)} کالا"
            else -> "ثبت ${plain(fresh)} کالا"
          }
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
  )
}

/** سابقهٔ واردسازی (PRD-20): هر ورود با تاریخ، کاربر، نامِ فایل و شمارها — از دفتر، پس همهٔ دستگاه‌های دکان */
@Composable
fun ProductImportHistoryDialog(entries: List<AuditEntry>, onDismiss: () -> Unit) {
  val c = Shop.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("سابقهٔ ورود از فایل", color = c.text) },
    text = {
      Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        if (entries.isEmpty()) Line("هنوز کالایی از فایل وارد نشده", c.muted)
        entries.take(200).forEach { e ->
          Text(
            formatDate(e.date) + (e.by?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = c.muted,
          )
          Line(e.notes, c.text)
          Spacer(Modifier.height(6.dp))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
  )
}

@Composable
private fun Line(text: String, color: Color) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun More(total: Int, shown: Int) {
  if (total > shown) Line("و ${plain(total - shown)} ردیفِ دیگر", Shop.colors.muted)
}

@Composable
private fun Tick(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
  }
}
