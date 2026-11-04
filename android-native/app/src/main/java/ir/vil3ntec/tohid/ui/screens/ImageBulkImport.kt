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
import androidx.compose.material3.AlertDialog
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
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrImagePages
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrSale
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
import kotlinx.coroutines.CancellationException
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
  //  تیکِ «جایگزینی» با کالای عکس‌دار ⇒ پیش از شروع، شمارِ عکس‌هایی که جایگزین می‌شوند
  var confirmReplace by remember { mutableIntStateOf(0) }

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
      message = StrImagePages.preparingFiles(uris.size)
      try {
        job = ImageBulk.add(context, job, uris, store.data.value) { i, n -> message = StrImagePages.preparingPhoto(i, n) }
        message = null
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        message = StrImagePages.readHalfDone
      }
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
          } catch (c: CancellationException) {
            throw c
          } catch (f: Exception) {
            if (ImageBank.transient(f)) { message = StrImagePages.netLostStopped; break }
            r.status = BulkJob.Status.FAILED
            r.note = f.message ?: StrOnline.error
          } catch (_: OutOfMemoryError) {
            r.status = BulkJob.Status.FAILED
            r.note = StrImagePages.lowMemory
          }
          ImageBulk.save(context, j)
          ImageBulk.tidy(context, j)
          tick++
        }
      } finally {
        if (j.rows.any { it.status == BulkJob.Status.WORKING }) j.reopen()
        ImageBulk.save(context, j)
        ImageBulk.tidy(context, j)
        running = false
        tick++
      }
    }
  }

  /** شروع؛ با «جایگزینی» اول تأیید که چند عکسِ فعلی جایگزین می‌شود */
  fun start() {
    val j = job ?: return
    val n = if (j.replace) j.rows.count { r ->
      r.status == BulkJob.Status.READY && !r.local && store.data.value.products.any { it.id == r.productId && it.photo }
    } else 0
    if (n > 0) confirmReplace = n else run()
  }

  if (confirmReplace > 0) {
    AlertDialog(
      onDismissRequest = { confirmReplace = 0 },
      containerColor = colors.surfaceSolid,
      title = { Text(StrImagePages.replaceCount(confirmReplace), color = colors.text) },
      text = {
        Text(
          StrImagePages.replaceNote(confirmReplace),
          color = colors.muted, style = MaterialTheme.typography.bodySmall,
        )
      },
      confirmButton = { Button(onClick = { confirmReplace = 0; run() }) { Text(StrImagePages.replaceBtn) } },
      dismissButton = { TextButton(onClick = { confirmReplace = 0 }) { Text(Str.cancel) } },
    )
  }

  AdaptiveSheet(onDismissRequest = { stop = true; onDismiss() }, containerColor = colors.bg) {
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
      Text(StrImagePages.bulkTitle, style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        StrImagePages.bulkHelp,
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
      Text(
        if (ImageBank.enabled.value == true) StrImagePages.shrinkBank
        else StrImagePages.shrinkSync,
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
      val j = job
      if (resumed && j != null && j.startable() && !running) {
        Spacer(Modifier.height(6.dp))
        StatusPill(StrImagePages.prevHalfDone(j.counts().waiting), PillTone.WARNING)
      }
      message?.let { Spacer(Modifier.height(6.dp)); StatusPill(it, PillTone.WARNING) }
      Spacer(Modifier.height(Space.xs))
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pick.launch("*/*") }, enabled = !running) { Text(StrImagePages.choosePhotos) }
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !running) {
        val jj = job ?: BulkJob("job-${System.currentTimeMillis()}").also { job = it }
        jj.replace = !jj.replace
        jj.rows.forEach { if (it.status == BulkJob.Status.HAS_PHOTO || it.status == BulkJob.Status.READY) it.status = BulkJob.Status.NEW }
        jj.plan(photoOf); ImageBulk.save(context, jj); tick++
      }) {
        Checkbox(checked = job?.replace == true, onCheckedChange = null, enabled = !running)
        Text(StrImagePages.replaceExisting, style = MaterialTheme.typography.bodySmall, color = colors.text)
      }
      if (j != null && j.rows.isNotEmpty()) {
        val c = j.counts()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
          StatusPill(Str.okCount(c.done), PillTone.SUCCESS)
          StatusPill(Str.failedCount(c.failed), PillTone.DANGER)
          StatusPill(StrImagePages.dupCount(c.duplicate), PillTone.NEUTRAL)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatusPill(StrImagePages.ambiguousCount(c.ambiguous), PillTone.WARNING)
          StatusPill(StrImagePages.unmatchedCount(c.unmatched), PillTone.WARNING)
          StatusPill(StrImagePages.hasPhotoCount(c.hasPhoto), PillTone.NEUTRAL)
          StatusPill(StrSale.leftQty(c.waiting), PillTone.NEUTRAL)
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
        Text(StrImagePages.noFileYet, style = MaterialTheme.typography.bodySmall, color = colors.muted)
      }
      Spacer(Modifier.height(Space.sm))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) OutlinedButton(onClick = { stop = true }) { Text(StrNav.stop) }
        else if (j != null && j.startable()) {
          Button(onClick = { start() }) {
            Text(if (j.rows.any { it.status == BulkJob.Status.DONE || it.status == BulkJob.Status.PENDING_BANK }) StrImagePages.continueImport else StrImagePages.startImport)
          }
        }
        if (!running && j != null && j.rows.isNotEmpty()) {
          TextButton(onClick = { ImageBulk.clear(context); job = null; resumed = false; tick++ }) { Text(StrImagePages.clearList) }
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
    "barcode" to StrImagePages.howFileBarcode, "name" to StrImagePages.howFullName, "csv_barcode" to StrImagePages.howCsvBarcode,
    "csv_name" to StrImagePages.howCsvName, "manual" to StrImagePages.howManual,
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
      Text(StrImagePages.whichProduct, style = MaterialTheme.typography.labelSmall, color = colors.text)
      r.candidates.mapNotNull { id -> d.products.firstOrNull { it.id == id } }.forEach { c ->
        TextButton(onClick = { onChoose(c.id) }) { Text(c.name + (c.barcodes.firstOrNull()?.let { " — $it" } ?: "")) }
      }
    }
    if (!running && r.status == BulkJob.Status.UNMATCHED) {
      var code by remember(r.key) { mutableStateOf("") }
      var miss by remember(r.key) { mutableStateOf(false) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = code, onValueChange = { code = it; miss = false }, singleLine = true,
          label = { Text(StrImagePages.barcodeOrExact) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = {
          val m = BulkMatch.match(code.trim() + ".jpg", ImageBulk.items(d))
          if (m.status == BulkMatch.Status.MATCHED) onChoose(m.productId) else miss = true
        }) { Text(StrImagePages.matchBtn) }
      }
      if (miss) Text(StrImagePages.noSuchProduct, style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    if (!running && r.status == BulkJob.Status.FAILED) TextButton(onClick = onRetry) { Text(Str.again) }
  }
}
