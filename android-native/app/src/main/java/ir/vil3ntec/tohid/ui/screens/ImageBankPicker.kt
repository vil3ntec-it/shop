package ir.vil3ntec.tohid.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  «انتخاب از بانکِ تصاویر» (v3 ۳۷.۵، ۳۷.۶) — گوشی و کامپیوتر. همتای وب:
 *  `pick`ِ `license/image-bank.js`.
 *
 *  • جست‌وجو با بارکد یا نام؛ نتیجه با عکسِ کوچک، نام، برند، وزن/حجم، بارکد
 *    و نشانِ تطبیق: «تطبیقِ دقیق» فقط برای بارکد/SKUِ دقیق، وگرنه «محتمل»؛
 *    «چند نوع — فرق در …» وقتی نامِ یکسان نوع‌های متفاوت دارد. بی امتیازِ عددی.
 *  • تطبیقِ غیرِ دقیق بی تیکِ «همین کالای من است» انتخاب نمی‌شود؛ بارکدِ
 *    متفاوت اصلاً انتخاب نمی‌شود. سرور باز خودش می‌سنجد (`needs_confirmation`).
 *  • انتخاب روی سرور ثبت می‌شود و بعد نسخهٔ نمایش برمی‌گردد (`onChosen`).
 */
@Composable
fun ImageBankPicker(
  productId: String,
  name: String,
  barcode: String,
  hasPhoto: Boolean,
  onDismiss: () -> Unit,
  /** IMG-BANK-10: بی نتیجه ⇒ دکمهٔ «عکسِ خودم را می‌گذارم» (null = بی دکمه) */
  onUploadOwn: (() -> Unit)? = null,
  onChosen: (ByteArray) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  val digits = ir.vil3ntec.tohid.core.image.BulkMatch.digitsOf(barcode)
  var query by remember { mutableStateOf(if (digits.length >= 8) barcode else name) }
  var hasImage by remember { mutableStateOf(true) }
  val items = remember { mutableStateListOf<ImageBank.Hit>() }
  var next by remember { mutableStateOf<String?>(null) }
  var state by remember { mutableStateOf("") }
  var stateTone by remember { mutableStateOf(PillTone.NEUTRAL) }
  var loading by remember { mutableStateOf(false) }
  var open by remember { mutableStateOf<ImageBank.Hit?>(null) }
  /** آخرین جست‌وجوی کامل‌شده هیچ نتیجه‌ای نداشت (نه «هنوز نپرسیده»، نه خطا) */
  var empty by remember { mutableStateOf(false) }

  fun search(more: Boolean) {
    val text = query.trim()
    if (text.isEmpty()) { state = "بارکد را اسکن یا نامِ کالا را بنویسید"; stateTone = PillTone.NEUTRAL; items.clear(); next = null; empty = false; return }
    loading = true
    empty = false
    state = "در حالِ جست‌وجو…"; stateTone = PillTone.NEUTRAL
    scope.launch {
      try {
        val page = ImageBank.search(context, text, hasImage, if (more) next else null)
        if (!more) items.clear()
        items += page.items
        next = page.next
        empty = items.isEmpty()
        val note = if (page.barcodeValid == false) "این بارکد معتبر نیست — نتیجه‌ها با نام است. " else ""
        state = note + if (items.isEmpty()) "چیزی پیدا نشد. می‌توانید عکسِ خودتان را بگذارید." else "${items.size} نتیجه" + if (next != null) " (بیشتر هم هست)" else ""
        stateTone = if (page.barcodeValid == false) PillTone.WARNING else PillTone.NEUTRAL
      } catch (f: ApiFailure) {
        stateTone = PillTone.DANGER
        state = when {
          f is ApiFailure.Offline || f is ApiFailure.Timeout -> "اینترنت قطع است یا سرور در دسترس نیست — بانکِ تصاویر بی اینترنت کار نمی‌کند."
          f is ApiFailure.Forbidden -> { ImageBank.enabled.value = false; "بانکِ تصاویر برای این دکان روشن نیست." }
          else -> "خطای سرور: ${f.userMessage}"
        }
      } catch (c: CancellationException) {
        throw c
      } catch (e: Exception) {
        stateTone = PillTone.DANGER
        state = "پاسخِ سرور خوانده نشد"
      } finally {
        loading = false
      }
    }
  }
  LaunchedEffect(Unit) { search(false) }

  AdaptiveSheet(onDismissRequest = onDismiss, containerColor = colors.bg) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
      Text("انتخاب از بانکِ تصاویر", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(Space.xs))
      val hit = open
      if (hit == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("بارکد یا نامِ کالا") }, modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(Space.xs))
          Button(onClick = { search(false) }, enabled = !loading) { Text("جست‌وجو") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hasImage = !hasImage; search(false) }) {
          Checkbox(checked = hasImage, onCheckedChange = { hasImage = it; search(false) })
          Text("فقط کالاهایی که عکسِ تأییدشده دارند", style = MaterialTheme.typography.bodySmall, color = colors.text)
        }
        if (state.isNotEmpty()) {
          if (stateTone == PillTone.NEUTRAL) Text(state, style = MaterialTheme.typography.bodySmall, color = colors.muted)
          else StatusPill(state, stateTone)
          if (stateTone == PillTone.DANGER) TextButton(onClick = { search(false) }) { Text("دوباره") }
        }
        Spacer(Modifier.height(Space.xs))
        //  IMG-BANK-10: بی نتیجه هیچ کالای نامرتبطی «نتیجه» نمی‌شود؛ راهِ روشن همین است
        if (empty && !loading && onUploadOwn != null) {
          OutlinedButton(onClick = onUploadOwn) { Text("عکسِ خودم را می‌گذارم") }
        }
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items.forEach { h -> HitCard(h) { open = h } }
          if (next != null) OutlinedButton(onClick = { search(true) }, enabled = !loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("نتیجه‌های بیشتر")
          }
        }
      } else {
        HitDetail(hit, productId, name, barcode, hasPhoto, onBack = { open = null }, onChosen = onChosen)
      }
    }
  }
}

private fun pills(h: ImageBank.Hit): List<Pair<String, PillTone>> = buildList {
  if (h.certain) add("تطبیقِ دقیق: ${ImageBank.KIND[h.kind].orEmpty()}" to PillTone.SUCCESS)
  else add("محتمل: ${ImageBank.KIND[h.kind] ?: "نامِ مشابه"}" to PillTone.WARNING)
  if ("brand_match" in h.reasons) add("برندِ مشابه" to PillTone.NEUTRAL)
  if (h.ambiguous) add("چند نوع — فرق در ${h.distinguish.joinToString("، ") { ImageBank.DIFF[it] ?: it }}" to PillTone.DANGER)
}

private fun spec(h: ImageBank.Hit): String = listOfNotNull(
  h.brand.ifBlank { null }, h.variant.ifBlank { null }, h.size.ifBlank { null },
  if (h.packCount > 1) "${h.packCount} عددی" else null, h.packaging.ifBlank { null },
).joinToString(" · ")

/**
 *  عکس‌های کوچکِ بانک — کشِ LRU برای همهٔ بازشدن‌ها (نه نقشهٔ بی‌سقفِ هر برگه).
 *  کلید = نشانی + اندازهٔ پیکسلی.
 */
private object BankThumbs {
  private const val MAX = 48
  private val map = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX
  }
  @Synchronized fun get(k: String): ImageBitmap? = map[k]
  @Synchronized fun put(k: String, v: ImageBitmap) { map[k] = v }
}

/** عکسِ بانک با نشستِ کاربر؛ رمزگشایی بیرون از نخِ اصلی و فقط به اندازهٔ کادر */
@Composable
private fun BankImage(url: String?, size: Dp, placeholder: String) {
  val context = LocalContext.current
  val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
  val key = url?.let { "$it@$px" }
  var img by remember(key) { mutableStateOf(key?.let(BankThumbs::get)) }
  LaunchedEffect(key) {
    if (url == null || key == null || img != null) return@LaunchedEffect
    img = try {
      val b = ImageBank.file(context, url)
      withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(b.inputStream(), null, bounds)
        val opts = BitmapFactory.Options().apply {
          inSampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) ImagePrep.sampleSize(bounds.outWidth, bounds.outHeight, px) else 1
        }
        BitmapFactory.decodeStream(b.inputStream(), null, opts)?.asImageBitmap()
      }?.also { BankThumbs.put(key, it) }
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      null
    }
  }
  Box(
    Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Shop.colors.surface2),
    contentAlignment = Alignment.Center,
  ) {
    val shown = img
    if (shown != null) Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    else Text(placeholder, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  }
}

@Composable
private fun HitCard(h: ImageBank.Hit, onOpen: () -> Unit) {
  val colors = Shop.colors
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, colors.border), RoundedCornerShape(12.dp))
      .background(colors.surface).clickable(onClick = onOpen).padding(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    val img = h.images.firstOrNull()
    BankImage(img?.thumb ?: img?.display, 64.dp, "بی عکس")
    Spacer(Modifier.width(Space.sm))
    Column(Modifier.weight(1f)) {
      Text(h.name, style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
      val s = spec(h)
      if (s.isNotEmpty()) Text(s, style = MaterialTheme.typography.bodySmall, color = colors.muted)
      if (h.gtin.isNotEmpty()) Text(h.gtin, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), color = colors.muted)
      Spacer(Modifier.height(4.dp))
      pills(h).forEach { (t, tone) -> StatusPill(t, tone); Spacer(Modifier.height(3.dp)) }
    }
  }
}

@Composable
private fun HitDetail(
  h: ImageBank.Hit,
  productId: String,
  name: String,
  barcode: String,
  hasPhoto: Boolean,
  onBack: () -> Unit,
  onChosen: (ByteArray) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  val a = remember(h) { ImageBank.assess(h, name, barcode) }
  var chosen by remember(h) { mutableStateOf(0) }
  var confirm by remember(h) { mutableStateOf(false) }
  //  سرور تأیید خواست (مثلاً مشخصه‌ای که این‌جا دیده نمی‌شد) ⇒ تیک لازم می‌شود
  var forceConfirm by remember(h) { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var problem by remember(h) { mutableStateOf<String?>(null) }
  val needs = a.needsConfirm || forceConfirm

  Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
    TextButton(onClick = onBack) { Text("بازگشت به نتیجه‌ها") }
    val img = h.images.getOrNull(chosen)
    BankImage(img?.display ?: img?.thumb, 220.dp, "این کالا در بانک عکسِ تأییدشده ندارد")
    if (h.images.size > 1) {
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        h.images.forEachIndexed { i, im ->
          Box(Modifier.border(BorderStroke(if (i == chosen) 2.dp else 0.dp, colors.primary), RoundedCornerShape(10.dp)).clickable { chosen = i }) {
            BankImage(im.thumb, 52.dp, "${i + 1}")
          }
        }
      }
    }
    Spacer(Modifier.height(Space.xs))
    Text(h.name, style = MaterialTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.Bold)
    listOf("برند" to h.brand, "طعم/مدل" to h.variant, "وزن/حجم" to h.size,
      "تعداد در بسته" to (if (h.packCount > 1) h.packCount.toString() else ""), "بسته‌بندی" to h.packaging, "بارکد" to h.gtin)
      .filter { it.second.isNotBlank() }
      .forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = colors.text) }
    Spacer(Modifier.height(4.dp))
    pills(h).forEach { (t, tone) -> StatusPill(t, tone); Spacer(Modifier.height(3.dp)) }
    a.why.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.warning) }
    Spacer(Modifier.height(6.dp))
    Text("قیمت، موجودی، قفسه و SKUِ شما از بانک کپی نمی‌شود؛ فقط عکس.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
    if (hasPhoto) Text("این کالا الان عکس دارد؛ با انتخاب، این عکس جایش را می‌گیرد.", style = MaterialTheme.typography.bodySmall, color = colors.warning)
    if (a.block.isNotEmpty()) StatusPill(a.block + " — انتخاب نمی‌شود", PillTone.DANGER)
    if (a.block.isEmpty() && needs) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { confirm = !confirm }) {
        Checkbox(checked = confirm, onCheckedChange = { confirm = it })
        Text("مشخصات را دیدم؛ این عکس واقعاً همین کالای من است", style = MaterialTheme.typography.bodySmall, color = colors.text)
      }
    }
    problem?.let { StatusPill(it, PillTone.DANGER) }
    Spacer(Modifier.height(Space.xs))
    Button(
      onClick = {
        val im = h.images.getOrNull(chosen) ?: return@Button
        busy = true; problem = null
        scope.launch {
          try {
            onChosen(ImageBank.chooseCentral(context, productId, im, name, barcode, confirm))
          } catch (f: ApiFailure) {
            problem = when (f.code) {
              "needs_confirmation" -> { forceConfirm = true; "تطبیق قطعی نیست — مشخصات را ببینید و تیک بزنید" }
              "barcode_mismatch" -> "بارکدِ کالای شما با کالای این عکس فرق دارد"
              "image_not_selectable", "version_not_approved" -> "این عکس دیگر در بانک قابلِ انتخاب نیست"
              else -> if (f is ApiFailure.Offline || f is ApiFailure.Timeout) "اینترنت قطع است — دوباره امتحان کنید" else f.userMessage
            }
          } catch (c: CancellationException) {
            throw c
          } catch (e: Exception) {
            problem = e.message ?: "عکس گرفته نشد"
          } finally {
            busy = false
          }
        }
      },
      enabled = !busy && h.images.isNotEmpty() && a.block.isEmpty() && (!needs || confirm),
      modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "در حالِ ثبت…" else "انتخاب این عکس") }
  }
}
