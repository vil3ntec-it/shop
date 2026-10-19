package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.BulkMatch
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.data.ImageBulk
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.launch

/**
 *  «ورودِ گروهیِ عکس‌ها» برای صاحب/مدیرِ دکان (v3 ۳۶.۸) — همتای `openBulk`ِ وب.
 *
 *  نامِ فایل = بارکد یا نامِ دقیقِ کالا، یا CSV (`barcode`/`name` + `image_filename`).
 *  مبهم و بی‌تطبیق حدس زده نمی‌شوند؛ کالای عکس‌دار بی تیکِ «جایگزینی» دست
 *  نمی‌خورد؛ نتیجهٔ هر ردیف جدا؛ بستن یا بسته‌شدنِ برنامه ⇒ «ادامهٔ ورود».
 */
@Composable
fun ImageBulkImportSheet(store: ShopStore, d: ShopData, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  var job by remember { mutableStateOf<BulkJob?>(null) }
  //  `BulkJob` درونش عوض می‌شود؛ این عدد صفحه را تازه می‌کند
  var tick by remember { mutableIntStateOf(0) }
  var running by remember { mutableStateOf(false) }
  var stop by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var resumed by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (ImageBank.enabled.value == null) ImageBank.refresh(context)
    job = ImageBulk.load(context)?.also { j ->
      j.plan { id -> store.data.value.products.firstOrNull { it.id == id }?.photo }
      resumed = j.resumed()
    }
    tick++
  }
  val photoOf: (String) -> Boolean? = { id -> store.data.value.products.firstOrNull { it.id == id }?.photo }

  val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    scope.launch {
      message = "در حالِ خواندنِ ${uris.size} فایل…"
      job = ImageBulk.add(context, job, uris, store.data.value)
      message = null
      tick++
    }
  }

  fun run() {
    val j = job ?: return
    running = true; stop = false; resumed = false; message = null
    scope.launch {
      try {
        while (!stop) {
          val r = j.next() ?: break
          if (r.status == BulkJob.Status.READY) r.status = BulkJob.Status.WORKING
          tick++
          try {
            ImageBulk.process(context, store, j, r)
          } catch (f: Exception) {
            if (ImageBank.transient(f)) { message = "اینترنت قطع شد — ورود متوقف شد؛ بعداً «ادامهٔ ورود» را بزنید"; break }
            r.status = BulkJob.Status.FAILED
            r.note = f.message ?: "خطا"
          }
          ImageBulk.save(context, j)
          tick++
        }
      } finally {
        if (j.rows.any { it.status == BulkJob.Status.WORKING }) j.reopen()
        ImageBulk.save(context, j)
        running = false
        tick++
      }
    }
  }

  AdaptiveSheet(onDismissRequest = { stop = true; onDismiss() }, containerColor = colors.bg) {
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
      Text("ورودِ گروهیِ عکسِ کالاها", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        "نامِ هر فایل را بارکد یا نامِ دقیقِ کالا بگذارید (مثلاً 6261234567890.jpg)، یا یک CSV با ستون‌های barcode/name و image_filename کنارشان انتخاب کنید. فایلِ مبهم یا بی‌تطبیق حدس زده نمی‌شود.",
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
      Text(
        if (ImageBank.enabled.value == true) "هر عکس روی دستگاه کوچک می‌شود (۱۲۰۰ و ۴۰۰ پیکسل، بی EXIF/مکان)، عکسِ کالا می‌شود و در بانکِ تصاویرِ همین دکان هم ثبت می‌شود."
        else "هر عکس روی دستگاه کوچک می‌شود (بی EXIF/مکان) و عکسِ کالا می‌شود؛ مثلِ همیشه با حساب همگام می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
      val j = job
      if (resumed && j != null && j.startable() && !running) {
        Spacer(Modifier.height(6.dp))
        StatusPill("ورودِ قبلی نیمه‌کاره ماند — ${j.counts().waiting} ردیف مانده", PillTone.WARNING)
      }
      message?.let { Spacer(Modifier.height(6.dp)); StatusPill(it, PillTone.WARNING) }
      Spacer(Modifier.height(Space.xs))
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pick.launch("*/*") }, enabled = !running) { Text("انتخابِ عکس‌ها (و CSV)") }
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !running) {
        val jj = job ?: BulkJob("job-${System.currentTimeMillis()}").also { job = it }
        jj.replace = !jj.replace
        jj.rows.forEach { if (it.status == BulkJob.Status.HAS_PHOTO || it.status == BulkJob.Status.READY) it.status = BulkJob.Status.NEW }
        jj.plan(photoOf); ImageBulk.save(context, jj); tick++
      }) {
        Checkbox(checked = job?.replace == true, onCheckedChange = null, enabled = !running)
        Text("عکسِ کالاهایی که از قبل عکس دارند هم جایگزین شود", style = MaterialTheme.typography.bodySmall, color = colors.text)
      }
      if (j != null && j.rows.isNotEmpty()) {
        val c = j.counts()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
          StatusPill("${c.done} موفق", PillTone.SUCCESS)
          StatusPill("${c.failed} ناموفق", PillTone.DANGER)
          StatusPill("${c.duplicate} تکراری", PillTone.NEUTRAL)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatusPill("${c.ambiguous} مبهم", PillTone.WARNING)
          StatusPill("${c.unmatched} بی‌تطبیق", PillTone.WARNING)
          StatusPill("${c.hasPhoto} عکس دارد", PillTone.NEUTRAL)
          StatusPill("${c.waiting} مانده", PillTone.NEUTRAL)
        }
        Spacer(Modifier.height(Space.xs))
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          j.rows.forEach { r ->
            BulkRowCard(r, d, running,
              onChoose = { id -> j.choose(r.key, id, photoOf); ImageBulk.save(context, j); tick++ },
              onRetry = { r.status = if (r.local) BulkJob.Status.PENDING_BANK else BulkJob.Status.READY; r.note = ""; ImageBulk.save(context, j); tick++ },
            )
          }
        }
      } else {
        Spacer(Modifier.height(Space.xs))
        Text("هنوز فایلی انتخاب نشده.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
      }
      Spacer(Modifier.height(Space.sm))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) OutlinedButton(onClick = { stop = true }) { Text("توقف") }
        else if (j != null && j.startable()) {
          Button(onClick = { run() }) {
            Text(if (j.rows.any { it.status == BulkJob.Status.DONE || it.status == BulkJob.Status.PENDING_BANK }) "ادامهٔ ورود" else "شروعِ ورود")
          }
        }
        if (!running && j != null && j.rows.isNotEmpty()) {
          TextButton(onClick = { ImageBulk.clear(context); job = null; resumed = false; tick++ }) { Text("پاک کردنِ فهرست") }
        }
      }
    }
  }
}

@Composable
private fun BulkRowCard(r: BulkJob.Row, d: ShopData, running: Boolean, onChoose: (String) -> Unit, onRetry: () -> Unit) {
  val colors = Shop.colors
  val p = d.products.firstOrNull { it.id == r.productId }
  val tone = when (r.status) {
    BulkJob.Status.DONE -> PillTone.SUCCESS
    BulkJob.Status.FAILED, BulkJob.Status.NOT_IMAGE -> PillTone.DANGER
    BulkJob.Status.AMBIGUOUS, BulkJob.Status.UNMATCHED -> PillTone.WARNING
    else -> PillTone.NEUTRAL
  }
  val how = mapOf(
    "barcode" to "بارکد در نامِ فایل", "name" to "نامِ کاملِ کالا", "csv_barcode" to "بارکدِ ردیفِ CSV",
    "csv_name" to "نامِ ردیفِ CSV", "manual" to "انتخابِ شما",
  )
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, colors.border), RoundedCornerShape(10.dp))
      .background(colors.surface).padding(10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.file, style = MaterialTheme.typography.bodySmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      StatusPill(r.status.label, tone)
    }
    if (p != null) Text("${p.name} — ${how[r.how].orEmpty()}", style = MaterialTheme.typography.labelSmall, color = colors.muted)
    if (r.note.isNotEmpty()) Text(r.note, style = MaterialTheme.typography.labelSmall, color = colors.muted)
    if (!running && r.status == BulkJob.Status.AMBIGUOUS) {
      Text("کدام کالا؟", style = MaterialTheme.typography.labelSmall, color = colors.text)
      r.candidates.mapNotNull { id -> d.products.firstOrNull { it.id == id } }.forEach { c ->
        TextButton(onClick = { onChoose(c.id) }) { Text(c.name + (c.barcodes.firstOrNull()?.let { " — $it" } ?: "")) }
      }
    }
    if (!running && r.status == BulkJob.Status.UNMATCHED) {
      var code by remember(r.key) { mutableStateOf("") }
      var miss by remember(r.key) { mutableStateOf(false) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = code, onValueChange = { code = it; miss = false }, singleLine = true,
          label = { Text("بارکد یا نامِ دقیق") }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = {
          val m = BulkMatch.match(code.trim() + ".jpg", ImageBulk.items(d))
          if (m.status == BulkMatch.Status.MATCHED) onChoose(m.productId) else miss = true
        }) { Text("تطبیق") }
      }
      if (miss) Text("کالایی با این بارکد یا نام پیدا نشد (یا بیش از یکی بود)", style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    if (!running && r.status == BulkJob.Status.FAILED) TextButton(onClick = onRetry) { Text("دوباره") }
  }
}
