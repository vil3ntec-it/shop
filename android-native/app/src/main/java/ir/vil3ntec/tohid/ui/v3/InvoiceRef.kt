package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CustomerAlias
import ir.vil3ntec.tohid.data.CustomerEngine
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/*
 *  «روی فاکتور که بزنم همان فاکتور را نشانم بدهد» (خواستِ صاحب‌کار، ۱۴۰۵/۰۸/۲۴، `claude/e-rows-63q`): هر «فاکتور #۱۲» ِ
 *  نوشته — شرحِ ردیفِ حساب («فروش نسیه — فاکتور #12»)، سرِ اقلام، «بابتِ …»، ردیفِ فاکتور، رسیدِ برگشت — یک جزء است
 *  ([InvoiceRef]) که همان صفحهٔ فاکتورِ «نمایشِ فاکتور» (`sales/{id}`) را باز می‌کند. نسیه و نقدی یکی‌اند.
 *   • فاکتور با شناسه اگر داده دارد ([InvoicePiece.saleId])، وگرنه با شمارهٔ نمایشی در همین دکان ([InvoiceRefs.resolve]):
 *     چند فاکتور با یک شماره ⇒ فاکتورِ همین مشتری.
 *   • شرحی که نوشتهٔ آزاد و شماره دارد: فقط همان «فاکتور #n» پیوند است ([InvoiceRefs.split]).
 *   • پیدا نشد ⇒ «· پیدا نشد» ِ آرام کنارِ همان نوشته، بی خطا و بی بستنِ برگه.
 *  پیدا کردن هنگامِ زدن است، نه هنگامِ کشیدن: ردیف‌های فهرست دفتر را برای هر شرح نمی‌گردند.
 */

/** یک تکه از نوشته: ساده، یا ارجاعِ فاکتور */
@Immutable
data class InvoicePiece(
  val text: String,
  /** شمارهٔ نمایشیِ فاکتور («12»، «B1-12»)؛ null ⇒ نوشتهٔ ساده */
  val label: String? = null,
  /** فاکتورِ شناخته (شناسه)؛ null ⇒ هنگامِ زدن با [label] پیدا می‌شود */
  val saleId: String? = null,
) {
  val isRef: Boolean get() = label != null
}

/** منطقِ خالصِ ارجاع‌ها — بی Compose، زیرِ آزمون (`InvoiceRefsTest`) */
object InvoiceRefs {

  fun plain(text: String): InvoicePiece = InvoicePiece(text)

  /** «فاکتور #۱۲» ِ برنامه (سه‌زبانه)، یا نوشتهٔ کوتاهِ دلخواه («#۱۲» ِ زیرعنوانِ ردیف) */
  fun ref(label: String, saleId: String? = null, text: String = StrMoney.invoiceNo(label)): InvoicePiece = InvoicePiece(text, label, saleId)

  /**
   *  شرحِ داده ⇒ تکه‌ها؛ فقط «فاکتور #n» ها پیوندند (همان الگوی دفتر، `CustomerEngine.invoiceRefsOf`).
   *  [known]: شماره ⇒ شناسهٔ فاکتوری که دفتر برای همین ردیف از پیش می‌شناسد (بردِ فاکتوردارِ `DebtBook`).
   */
  fun split(text: String, known: Map<String, String> = emptyMap()): List<InvoicePiece> {
    if (text.isEmpty()) return emptyList()
    val hits = CustomerEngine.invoiceRefsOf(text).ifEmpty { localRefs(text) }
    if (hits.isEmpty()) return listOf(InvoicePiece(text))
    val out = ArrayList<InvoicePiece>(hits.size * 2 + 1)
    var at = 0
    for ((range, label) in hits) {
      if (range.first > at) out += InvoicePiece(text.substring(at, range.first))
      out += InvoicePiece(text.substring(range), label, known[label])
      at = range.last + 1
    }
    if (at < text.length) out += InvoicePiece(text.substring(at))
    return out
  }

  /**
   *  نوشتهٔ ساختهٔ برنامه به زبانِ دیگر («Invoice #12»، «بل #12» ِ ستونِ سندِ «پایانِ روز») — شرحِ داده همیشه فارسی است
   *  و الگوی دفتر آن را می‌گیرد؛ این فقط برای نوشتهٔ رابطِ انگلیسی/پشتو است.
   */
  private fun localRefs(text: String): List<Pair<IntRange, String>> {
    val word = StrMoney.invoiceNo("0").substringBefore('#').trim()
    if (word.isEmpty() || !text.contains('#')) return emptyList()
    return Regex(Regex.escape(word) + "[ \u00A0]*#[ \u00A0]*((?:[A-Z][1-9][0-9]{0,3}-)?[0-9]+)", RegexOption.IGNORE_CASE)
      .findAll(text).map { it.range to it.groupValues[1] }.toList()
  }

  /**
   *  نوشتهٔ ساختهٔ برنامه («از فاکتور #12 · ۲۸ میزان») ⇒ همان نوشته، با «#12» و واژهٔ پیش از آن («فاکتور»، «invoice»،
   *  «بل») پیوند. شماره در نوشته نیست ⇒ همه ساده.
   */
  fun linkify(text: String, label: String, saleId: String?): List<InvoicePiece> {
    val mark = "#$label"
    var i = text.indexOf(mark)
    //  «#12» درونِ «#123» نه
    while (i >= 0 && i + mark.length < text.length && text[i + mark.length].isLetterOrDigit()) i = text.indexOf(mark, i + 1)
    if (i < 0) return listOf(InvoicePiece(text))
    var start = i
    var j = i - 1
    while (j >= 0 && text[j] == ' ') j--
    if (j >= 0 && text[j].isLetter()) {
      while (j >= 0 && text[j].isLetter()) j--
      start = j + 1
    }
    val end = i + mark.length
    return listOfNotNull(
      text.substring(0, start).takeIf { it.isNotEmpty() }?.let(::InvoicePiece),
      InvoicePiece(text.substring(start, end), label, saleId),
      text.substring(end).takeIf { it.isNotEmpty() }?.let(::InvoicePiece),
    )
  }

  /** همهٔ تکه‌ها با هم — نوشتهٔ ساده وقتی پیوندی کشیده نمی‌شود */
  fun text(pieces: List<InvoicePiece>): String = pieces.joinToString("") { it.text }

  /**
   *  شماره ⇒ فاکتور در همین دکان: یکتا ⇒ همان؛ چند فاکتور با یک شماره (دفترِ کهنهٔ بی سری از دو دستگاه) ⇒ فاکتورِ
   *  همین مشتری ([debtorId] با ادغام‌شده‌ها، `CustomerAlias`)، وگرنه تازه‌ترین؛ هیچ ⇒ null («پیدا نشد»).
   */
  fun resolve(d: ShopData, label: String, debtorId: String? = null): Sale? {
    val all = byLabel(d)[label].orEmpty()
    if (all.size <= 1) return all.firstOrNull()
    val own = debtorId?.let { CustomerAlias.of(d, it) }
    val mine = if (own == null) emptyList() else all.filter { it.debtorId in own }
    return mine.ifEmpty { all }.maxByOrNull { it.createdAt }
  }

  /** شناسهٔ فاکتورِ یک تکه: شناسهٔ داده اگر هنوز در دفتر هست، وگرنه با شماره ([resolve]) */
  fun find(d: ShopData, label: String, saleId: String?, debtorId: String?): String? =
    saleId?.takeIf { id -> d.sales.any { it.id == id } } ?: resolve(d, label, debtorId)?.id

  /** نمایهٔ «شماره ⇒ فاکتورها» یک بار برای هر `d.sales` — زدن‌های پشتِ هم دفتر را دوباره نمی‌گردند */
  @Volatile private var last: Pair<List<Sale>, Map<String, List<Sale>>>? = null

  private fun byLabel(d: ShopData): Map<String, List<Sale>> {
    last?.let { if (it.first === d.sales) return it.second }
    val map = HashMap<String, MutableList<Sale>>()
    for (s in d.sales) s.invoiceLabel()?.let { map.getOrPut(it) { ArrayList(1) } += s }
    return map.also { last = d.sales to it }
  }
}

/** پیدا کردن و باز کردنِ فاکتور برای ارجاع‌ها — پوستهٔ نسخهٔ ۳ (`V3RouteHost`) با دفتر و ناوبریِ خودش می‌گذارد */
class InvoiceLinks(
  val find: (label: String, saleId: String?, debtorId: String?) -> String?,
  val open: (saleId: String) -> Unit,
) {
  companion object {
    fun of(d: ShopData, open: (String) -> Unit): InvoiceLinks = InvoiceLinks({ l, id, who -> InvoiceRefs.find(d, l, id, who) }, open)
  }
}

/** null (پیش‌نمایش، صفحهٔ بیرون از پوسته) ⇒ ارجاع نوشتهٔ ساده است، نه دکمهٔ بی‌اثر */
val LocalInvoiceLinks = compositionLocalOf<InvoiceLinks?> { null }

/**
 *  «فاکتور #۱۲» ِ زدنی: نوشتهٔ برند (پیوندِ آرام، بی قاب)؛ زدن ⇒ همان فاکتور؛ پیدا نشد ⇒ «· پیدا نشد» ِ آرام.
 *  [debtorId]: مشتریِ همین نوشته برای شمارهٔ دوپهلو. زدنش به زدنِ ردیفِ دربرگیرنده نمی‌رسد.
 */
@Composable
fun InvoiceRef(piece: InvoicePiece, modifier: Modifier = Modifier, style: TextStyle = V3Type.footnote, debtorId: String? = null) {
  val c = PhoneV3.colors
  val label = piece.label
  val links = LocalInvoiceLinks.current
  if (label == null || links == null) {
    Text(piece.text, modifier, style = style, color = c.inkMuted)
    return
  }
  var missing by remember(label, piece.saleId) { mutableStateOf(false) }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(
      piece.text,
      style = style,
      color = if (missing) c.inkMuted else PhoneV3Roles.brandText(c),
      maxLines = 1,
      modifier = Modifier.v3Pressable(label = StrSale.viewInvoice, role = Role.Button) {
        val id = links.find(label, piece.saleId, debtorId)
        if (id == null) missing = true else links.open(id)
      },
    )
    if (missing) {
      Text(
        V3_ROW_DOT + StrRows.invoiceMissing,
        style = style,
        color = c.inkMuted,
        maxLines = 1,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

/**
 *  نوشتهٔ تکه‌تکه: ساده‌ها به رنگِ [color]، ارجاع‌ها [InvoiceRef]. بی ارجاع (یا بیرون از پوسته) ⇒ همان یک `Text` ِ همیشه.
 *  با ارجاع تکه‌ها در یک `FlowRow` می‌نشینند تا پیوند هیچ‌وقت زیرِ «…» ِ بریدن نرود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceRefText(
  pieces: List<InvoicePiece>,
  modifier: Modifier = Modifier,
  style: TextStyle = V3Type.footnote,
  color: Color = PhoneV3.colors.inkMuted,
  debtorId: String? = null,
  arrangement: Arrangement.Horizontal = Arrangement.Start,
  textAlign: TextAlign? = null,
  maxLines: Int = Int.MAX_VALUE,
) {
  if (pieces.none { it.isRef } || LocalInvoiceLinks.current == null) {
    Text(InvoiceRefs.text(pieces), modifier, style = style, color = color, textAlign = textAlign, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    return
  }
  FlowRow(modifier, horizontalArrangement = arrangement) {
    pieces.forEach { p ->
      if (p.isRef) InvoiceRef(p, style = style, debtorId = debtorId)
      else Text(p.text, style = style, color = color, textAlign = textAlign)
    }
  }
}
