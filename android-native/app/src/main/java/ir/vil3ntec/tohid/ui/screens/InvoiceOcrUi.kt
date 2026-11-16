package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Decision
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Flag
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.InvoiceOcrRepository
import ir.vil3ntec.tohid.data.repo.InvoiceOcrSource
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskDialog
import ir.vil3ntec.tohid.ui.desk.DeskDialogAction
import ir.vil3ntec.tohid.ui.desk.DeskDialogSize
import ir.vil3ntec.tohid.ui.desk.DeskDialogText
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3LiveSpinner
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.money.IdPickerSheet
import ir.vil3ntec.tohid.ui.v3.money.PickOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  PUR-12 / AI-10 — «از عکسِ فاکتور» در «فاکتورِ خریدِ تازه»: گوشیِ نسخهٔ ۳ (`PurchaseFormScreen` ⇒ [InvoiceOcrPhoneButton] و
 *  [InvoiceOcrPhoneHost] با `V3Sheet`) و کامپیوتر (`DeskPurchasing.NewPurchaseInvoice` ⇒ [InvoiceOcrDeskButton] و
 *  [InvoiceOcrDeskHost] با `DeskDialog`). منطقِ خالص در `core/purchase/InvoiceOcr`؛ سرور فقط متن می‌دهد (Tesseract ِ خودِ صاحب).
 *
 *  جریان: عکس (دوربین/گالری؛ کامپیوتر پنجرهٔ فایل) ⇒ JPEG ِ کوچک با جهتِ درست ([invoiceJpeg]) ⇒ سرور ⇒ ردیف‌ها ⇒ بازبینی:
 *  هر ردیف کالای پیشنهادی، مقدار و فیِ ویرایش‌پذیر، «تأیید»/«رد». فقط ردیف‌های تأییدشده ([InvoiceOcr.accepted]) به پیش‌نویسِ
 *  همان فرم می‌روند و ثبت همان «ثبت» ِ همیشگی است — هیچ ثبت، انبار یا بدهی پیش از آن.
 *  ⛔ دکمه فقط وقتی سرور «روشن» و «اجازه دارید» گفت (پرچمِ خاموشِ `shop.purchase_ocr`، «پس از انتشار»)؛ نبودِ موتور یا اشتراک را
 *     خودِ برگه با جملهٔ ساده می‌گوید. آیفون همان `PhotoSourceSheet` ِ مشترک را دارد (`ios/IosPickers.kt`).
 */

/** حالِ برگه */
internal enum class OcrPhase { CHOOSE, SENDING, REVIEW, EMPTY, UNAVAILABLE, NEEDS_PLAN, FAILED }

/** عکس‌گیر: (باز است، بسته شد، بایت‌های JPEG یا null) — آزمون عکسِ آماده می‌دهد */
internal typealias InvoicePhotoPicker = @Composable (open: Boolean, onDismiss: () -> Unit, onBytes: (ByteArray?) -> Unit) -> Unit

internal val LocalInvoicePhotoPicker: ProvidableCompositionLocal<InvoicePhotoPicker> =
  staticCompositionLocalOf { { open, onDismiss, onBytes -> InvoicePhotoSource(open, onDismiss, onBytes) } }

/** کامپیوتر: پنجرهٔ فایلِ سیستم؛ گوشی و آیفون: برگهٔ «دوربین یا گالری» ِ مشترک */
@Composable
internal fun InvoicePhotoSource(open: Boolean, onDismiss: () -> Unit, onBytes: (ByteArray?) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val deliver: (Uri) -> Unit = { uri -> scope.launch { onBytes(withContext(Dispatchers.IO) { runCatching { invoiceJpeg(context, uri) }.getOrNull() }) } }
  if (Platform.DESKTOP) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onDismiss(); if (uri != null) deliver(uri) }
    LaunchedEffect(open) {
      if (open) runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { onDismiss() }
    }
  } else {
    PhotoSourceSheet(open = open, onDismiss = onDismiss, note = StrInvoiceOcr.pickNote, onPicked = deliver)
  }
}

/** کالاهای فعالِ دفتر ⇒ نمایهٔ تطبیق */
internal fun ocrIndex(products: List<Product>): InvoiceOcr.Index =
  InvoiceOcr.Index(products.filter { it.active }.map { InvoiceOcr.CatalogItem(it.id, it.name, it.barcodes, it.purchasePrice) })

/** «سرور گفت نه» برای خودِ قابلیت (پرچم، اجازه، سرورِ کهنه) — دکمه پنهان می‌شود */
private fun ocrRefused(f: ApiFailure): Boolean =
  f.code == "purchase_ocr_disabled" || f.code == "permission_denied" || f is ApiFailure.NotFound ||
    f is ApiFailure.Unauthorized || f is ApiFailure.SessionExpired || f is ApiFailure.NotConfigured

@Stable
internal class InvoiceOcrController(
  private val src: InvoiceOcrSource,
  /** کالاهای دفترِ همین دستگاه (تازه‌ترین) */
  private val products: () -> List<Product>,
) {
  var config by mutableStateOf<InvoiceOcr.Config?>(null)
    private set
  var open by mutableStateOf(false)
    private set
  var picking by mutableStateOf(false)
  var phase by mutableStateOf(OcrPhase.CHOOSE)
    private set
  var review by mutableStateOf<InvoiceOcr.Review?>(null)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  /** دکمه دیده شود؟ — فقط با `enabled` و `permitted` ِ صریحِ سرور */
  val visible: Boolean get() = config?.visible == true

  /** خطای گذرا حالِ پیشین را نگه می‌دارد */
  suspend fun load() {
    src.config().onSuccess { config = it }.onFailure { if (ocrRefused(it)) config = null }
  }

  /** زدنِ دکمه: نبودِ اشتراک یا موتور همان‌جا گفته می‌شود؛ وگرنه عکس‌گیر */
  fun start() {
    open = true
    review = null
    error = null
    val c = config
    phase = when {
      c != null && !c.paid -> OcrPhase.NEEDS_PLAN
      c != null && !c.available -> OcrPhase.UNAVAILABLE
      else -> OcrPhase.CHOOSE
    }
    picking = phase == OcrPhase.CHOOSE
  }

  /** دوباره عکس */
  fun again() { review = null; error = null; phase = OcrPhase.CHOOSE; picking = true }

  /** [jpeg] null ⇒ عکس باز نشد */
  suspend fun send(jpeg: ByteArray?) {
    open = true
    if (jpeg == null) { phase = OcrPhase.FAILED; error = StrInvoiceOcr.unreadable; return }
    phase = OcrPhase.SENDING
    src.read(jpeg)
      .onSuccess { lines ->
        val catalog = products()
        val r = withContext(Dispatchers.Default) { InvoiceOcr.review(InvoiceOcr.parse(lines), ocrIndex(catalog)) }
        review = r
        phase = if (r.rows.isEmpty()) OcrPhase.EMPTY else OcrPhase.REVIEW
      }
      .onFailure { f ->
        when {
          f.code == "ocr_not_installed" || f.code == "ocr_lang_missing" -> { config = config?.copy(available = false); phase = OcrPhase.UNAVAILABLE }
          f.code == "subscription_expired" || f.code == "subscription_required" -> { config = config?.copy(paid = false); phase = OcrPhase.NEEDS_PLAN }
          ocrRefused(f) -> { config = null; close() }
          f is ApiFailure.Invalid && f.code.startsWith("image_") -> { phase = OcrPhase.FAILED; error = f.userMessage }
          else -> { phase = OcrPhase.FAILED; error = f.userMessage }
        }
      }
  }

  fun edit(change: (InvoiceOcr.Review) -> InvoiceOcr.Review) { review = review?.let(change) }

  /** نامِ کالای دفتر */
  fun nameOf(id: String): String? = products().firstOrNull { it.id == id }?.name

  /** کالاهای فعالِ دیگر برای انتخابِ دستی (کامپیوتر: بیشینه ۲۰۰، به ترتیبِ نام) */
  fun others(except: Set<String>, limit: Int = 200): List<Product> =
    products().asSequence().filter { it.active && it.id !in except }.sortedBy { it.name }.take(limit).toList()

  fun close() { open = false; picking = false; review = null; error = null; phase = OcrPhase.CHOOSE }

  /** ردیف‌های تأییدشده — برگه بسته می‌شود */
  fun take(): List<InvoiceOcr.Accepted> {
    val out = review?.let(InvoiceOcr::accepted).orEmpty()
    close()
    return out
  }
}

/** کنترلِ «از عکسِ فاکتور» ِ یک فرم؛ [products] دفترِ همین دستگاه */
@Composable
internal fun rememberInvoiceOcr(products: List<Product>): InvoiceOcrController {
  val context = LocalContext.current
  val latest = rememberUpdatedState(products)
  val ctl = remember(context) {
    InvoiceOcrController(InvoiceOcrRepository(Backend.api(context))) { latest.value }
  }
  LaunchedEffect(ctl) { ctl.load() }
  return ctl
}

/** عکس‌گیرِ مشترکِ هر دو میزبان */
@Composable
private fun OcrPicker(ctl: InvoiceOcrController) {
  val scope = rememberCoroutineScope()
  LocalInvoicePhotoPicker.current(ctl.picking, { ctl.picking = false }) { bytes -> scope.launch { ctl.send(bytes) } }
}

private fun flagText(f: Flag): String = when (f) {
  Flag.MISMATCH, Flag.AMBIGUOUS -> StrInvoiceOcr.flagMismatch
  Flag.ORDER_GUESSED -> StrInvoiceOcr.flagOrder
  Flag.LOW_CONFIDENCE -> StrInvoiceOcr.flagLow
  Flag.NO_MATCH -> StrInvoiceOcr.flagNoMatch
  Flag.WEAK_MATCH -> StrInvoiceOcr.flagWeak
}

/** ردیفِ «جمعِ تأییدشده / جمعِ چاپی» */
private fun totals(v: InvoiceOcr.Review): String = buildString {
  append(StrInvoiceOcr.confirmedTotal(money(InvoiceOcr.confirmedTotal(v))))
  v.printedTotal?.let { append(" · "); append(StrInvoiceOcr.printedTotal(money(it))) }
}

/* ══════════════════════════ گوشی ══════════════════════════ */

/** دکمهٔ فرمِ گوشی — فقط وقتی سرور روشن و اجازه گفت */
@Composable
internal fun InvoiceOcrPhoneButton(ctl: InvoiceOcrController, modifier: Modifier = Modifier) {
  if (!ctl.visible) return
  V3Button(StrInvoiceOcr.button, ctl::start, modifier, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.PhotoCamera)
}

/** برگهٔ بازبینیِ گوشی (`V3Sheet`)؛ [onAccept] ردیف‌های تأییدشده را به فرم می‌برد */
@Composable
internal fun InvoiceOcrPhoneHost(ctl: InvoiceOcrController, products: List<Product>, onAccept: (List<InvoiceOcr.Accepted>) -> Unit) {
  OcrPicker(ctl)
  var pickFor by remember { mutableStateOf<Int?>(null) }
  val row = pickFor
  if (row != null) {
    val current = ctl.review?.rows?.getOrNull(row)?.productId
    IdPickerSheet(
      title = StrInvoiceOcr.product,
      options = products.filter { it.active || it.id == current }.map { PickOption(it.id, it.name, it.barcodes.firstOrNull()) },
      selected = current,
      onPick = { id -> ctl.edit { InvoiceOcr.pick(it, row, id) } },
      onDismiss = { pickFor = null },
    )
    return
  }
  if (!ctl.open || ctl.picking) return
  val c = PhoneV3.colors
  V3Sheet(onDismiss = ctl::close, title = StrInvoiceOcr.title) {
    when (ctl.phase) {
      OcrPhase.SENDING -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        V3LiveSpinner(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(StrInvoiceOcr.busy, style = V3Type.body, color = c.inkMuted)
      }
      OcrPhase.UNAVAILABLE -> V3Message(V3Tone.WARNING, StrInvoiceOcr.unavailableTitle, body = StrInvoiceOcr.unavailable)
      OcrPhase.NEEDS_PLAN -> V3Message(V3Tone.INFO, StrInvoiceOcr.needsPlan)
      OcrPhase.EMPTY -> V3Message(V3Tone.INFO, StrInvoiceOcr.nothing, action = StrInvoiceOcr.again, onAction = ctl::again)
      OcrPhase.FAILED -> V3Message(V3Tone.ERROR, ctl.error.orEmpty(), action = StrInvoiceOcr.again, onAction = ctl::again)
      OcrPhase.CHOOSE -> {
        V3Message(V3Tone.INFO, StrInvoiceOcr.pickNote)
        Spacer(Modifier.height(12.dp))
        V3Button(StrInvoiceOcr.takePhoto, ctl::again, Modifier.fillMaxWidth())
      }
      OcrPhase.REVIEW -> {
        val v = ctl.review ?: return@V3Sheet
        Text(StrInvoiceOcr.notice, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(10.dp))
        v.rows.forEachIndexed { i, r -> PhoneOcrRow(ctl, i, r, onOther = { pickFor = i }); Spacer(Modifier.height(10.dp)) }
        if (v.dropped > 0) Text(StrInvoiceOcr.dropped(v.dropped), style = V3Type.footnote, color = c.inkMuted)
        Text(totals(v), style = V3Type.subhead, color = c.ink, modifier = Modifier.padding(vertical = 8.dp))
        V3Button(StrInvoiceOcr.addRows(v.confirmed), { onAccept(ctl.take()) }, Modifier.fillMaxWidth(), enabled = v.confirmed > 0)
        Spacer(Modifier.height(8.dp))
        V3Button(StrInvoiceOcr.again, ctl::again, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
    }
    if (ctl.phase != OcrPhase.REVIEW && ctl.phase != OcrPhase.SENDING) {
      Spacer(Modifier.height(12.dp))
      V3Button(StrInvoiceOcr.close, ctl::close, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneOcrRow(ctl: InvoiceOcrController, i: Int, r: InvoiceOcr.Row, onOther: () -> Unit) {
  val c = PhoneV3.colors
  V3Card(padding = PaddingValues(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.name, style = V3Type.headline, color = c.ink, modifier = Modifier.weight(1f), maxLines = 2)
      when (r.decision) {
        Decision.CONFIRMED -> V3StatusPill(V3Status.DELIVERED, StrInvoiceOcr.confirmed)
        Decision.SKIPPED -> V3StatusPill(V3Status.CANCELLED, StrInvoiceOcr.skipped)
        Decision.PENDING -> Unit
      }
    }
    Text(StrInvoiceOcr.read(r.raw), style = V3Type.footnote, color = c.inkMuted, maxLines = 2)
    if (r.decision != Decision.SKIPPED) {
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shown = r.choices.take(3)
        shown.forEach { m -> V3Chip(m.name, r.productId == m.id, { ctl.edit { InvoiceOcr.pick(it, i, m.id) } }) }
        //  کالای برگزیده از «کالای دیگر…» که در پیشنهادها نیست
        val picked = r.productId?.takeIf { id -> shown.none { it.id == id } }
        V3Chip(picked?.let(ctl::nameOf) ?: StrInvoiceOcr.otherProduct, picked != null, onOther)
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3TextField(r.qty, { t -> ctl.edit { InvoiceOcr.setQty(it, i, t) } }, StrMoney.quantity, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
        V3TextField(r.price, { t -> ctl.edit { InvoiceOcr.setPrice(it, i, t) } }, StrMoney.unitPrice, Modifier.weight(1f), kind = V3FieldKind.MONEY)
      }
      for (f in r.flags) Text(flagText(f), style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 6.dp))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (r.decision == Decision.PENDING) {
        V3Button(StrInvoiceOcr.confirm, { ctl.edit { InvoiceOcr.confirm(it, i) } }, Modifier.weight(1f), enabled = InvoiceOcr.canConfirm(r))
        V3Button(StrInvoiceOcr.skip, { ctl.edit { InvoiceOcr.skip(it, i) } }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      } else {
        V3Button(StrInvoiceOcr.undo, { ctl.edit { InvoiceOcr.undo(it, i) } }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      }
    }
  }
}

/* ══════════════════════════ کامپیوتر ══════════════════════════ */

/** دکمهٔ سرخطِ «فاکتورِ خریدِ تازه» ِ کامپیوتر */
@Composable
internal fun InvoiceOcrDeskButton(ctl: InvoiceOcrController) {
  if (!ctl.visible) return
  DeskButton(StrInvoiceOcr.button, ctl::start, icon = Icons.Outlined.PhotoCamera)
}

/** پنجرهٔ بازبینیِ کامپیوتر (`DeskDialog`) */
@Composable
internal fun InvoiceOcrDeskHost(ctl: InvoiceOcrController, onAccept: (List<InvoiceOcr.Accepted>) -> Unit) {
  OcrPicker(ctl)
  if (!ctl.open || ctl.picking) return
  val v = ctl.review
  val review = ctl.phase == OcrPhase.REVIEW && v != null
  DeskDialog(
    title = StrInvoiceOcr.title,
    onDismiss = ctl::close,
    icon = Icons.Outlined.PhotoCamera,
    width = if (review) DeskDialogSize.wide else DeskDialogSize.form,
    confirm = when {
      review -> DeskDialogAction(StrInvoiceOcr.addRows(v!!.confirmed), enabled = v.confirmed > 0) { onAccept(ctl.take()) }
      ctl.phase == OcrPhase.CHOOSE || ctl.phase == OcrPhase.EMPTY || ctl.phase == OcrPhase.FAILED -> DeskDialogAction(StrInvoiceOcr.takePhoto) { ctl.again() }
      else -> null
    },
    dismiss = DeskDialogAction(StrInvoiceOcr.close) { ctl.close() },
    extra = if (review) listOf(DeskDialogAction(StrInvoiceOcr.again) { ctl.again() }) else emptyList(),
  ) {
    val dc = Desk.colors
    when (ctl.phase) {
      OcrPhase.SENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
        V3LiveSpinner(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(DeskSpace.x2))
        DeskDialogText(StrInvoiceOcr.busy)
      }
      OcrPhase.UNAVAILABLE -> { DeskDialogText(StrInvoiceOcr.unavailableTitle, strong = true); DeskDialogText(StrInvoiceOcr.unavailable) }
      OcrPhase.NEEDS_PLAN -> DeskDialogText(StrInvoiceOcr.needsPlan)
      OcrPhase.EMPTY -> DeskDialogText(StrInvoiceOcr.nothing)
      OcrPhase.FAILED -> DeskDialogText(ctl.error.orEmpty(), color = dc.negative)
      OcrPhase.CHOOSE -> DeskDialogText(StrInvoiceOcr.pickNote)
      OcrPhase.REVIEW -> if (v != null) {
        DeskDialogText(StrInvoiceOcr.notice)
        v.rows.forEachIndexed { i, r -> DeskOcrRow(ctl, i, r) }
        if (v.dropped > 0) DeskDialogText(StrInvoiceOcr.dropped(v.dropped))
        DeskDialogText(totals(v), strong = true)
      }
    }
  }
}

@Composable
private fun ColumnScope.DeskOcrRow(ctl: InvoiceOcrController, i: Int, r: InvoiceOcr.Row) {
  val dc = Desk.colors
  Column(Modifier.fillMaxWidth().deskCard(dc).padding(DeskSpace.x3), verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.name, style = DeskType.bodyStrong, color = dc.ink, modifier = Modifier.weight(1f), maxLines = 1)
      when (r.decision) {
        Decision.CONFIRMED -> Text(StrInvoiceOcr.confirmed, style = DeskType.label, color = dc.positive)
        Decision.SKIPPED -> Text(StrInvoiceOcr.skipped, style = DeskType.label, color = dc.inkMuted)
        Decision.PENDING -> Unit
      }
    }
    Text(StrInvoiceOcr.read(r.raw), style = DeskType.secondary, color = dc.inkSecondary, maxLines = 1)
    if (r.decision != Decision.SKIPPED) {
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
        //  پیشنهادها اول، سپس کالاهای فعالِ دیگر (هیچ کالای تازه‌ای ساخته نمی‌شود)
        val options = r.choices.map { DeskOption<String?>(it.id, it.name) } +
          remember(r.choices) { ctl.others(r.choices.mapTo(HashSet()) { it.id }) }.map { DeskOption<String?>(it.id, it.name) }
        DeskDropdown(
          r.productId, options, { id -> ctl.edit { InvoiceOcr.pick(it, i, id) } }, Modifier.weight(1f),
          shown = if (r.productId == null) StrInvoiceOcr.pickProduct else null,
        )
        DeskTextField(
          r.qty, { t -> ctl.edit { InvoiceOcr.setQty(it, i, ir.vil3ntec.tohid.numericInput(t)) } }, Modifier.width(110.dp),
          textAlign = TextAlign.End, description = StrMoney.quantity, placeholder = StrMoney.quantity,
        )
        DeskTextField(
          r.price, { t -> ctl.edit { InvoiceOcr.setPrice(it, i, ir.vil3ntec.tohid.numericInput(t)) } }, Modifier.width(130.dp),
          textAlign = TextAlign.End, description = StrMoney.unitPrice, placeholder = StrMoney.unitPrice,
        )
      }
      for (f in r.flags) Text(flagText(f), style = DeskType.secondary, color = dc.warning)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      if (r.decision == Decision.PENDING) {
        DeskButton(StrInvoiceOcr.confirm, { ctl.edit { InvoiceOcr.confirm(it, i) } }, kind = DeskButtonKind.PRIMARY, enabled = InvoiceOcr.canConfirm(r))
        DeskButton(StrInvoiceOcr.skip, { ctl.edit { InvoiceOcr.skip(it, i) } }, kind = DeskButtonKind.GHOST)
      } else {
        DeskButton(StrInvoiceOcr.undo, { ctl.edit { InvoiceOcr.undo(it, i) } }, kind = DeskButtonKind.GHOST)
      }
    }
  }
}
