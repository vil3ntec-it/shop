package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import ir.vil3ntec.tohid.core.pos.PriceWhy
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.data.PriceAsk
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.components.pos.PosLineNote
import ir.vil3ntec.tohid.ui.components.pos.PosLineSerials
import ir.vil3ntec.tohid.ui.components.pos.PosNoteField
import ir.vil3ntec.tohid.ui.components.pos.PriceWhyPanel
import ir.vil3ntec.tohid.ui.components.pos.PosSerialField
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.sales.approval.PinApprovalSheet

/**
 *  برگهٔ «تعداد و قیمتِ این ردیف» (`sale/line/{i}`، نگه داشتنِ ردیفِ سبد): واحد (اگر کالا
 *  واحدِ فروشِ دوم دارد؛ با «واحدِ بزرگ در صندوق» همهٔ واحدها و بسته‌ها با قیمتِ لیست، POS-09)، «تعداد (کیلوگرم)»، «قیمت هر واحد» با یادِ قیمتِ خودِ کالا، «موجودی در
 *  محل‌ها» (POS-16، فقط با «محل‌های نگه‌داری» ِ روشن و محلی جز محلِ اصلی)، جمعِ
 *  شمارندهٔ همین ردیف، «ثبت» و «برداشتن از سبد». خطا زیرِ همان کادر و لرزشِ کوتاه.
 *
 *  S45: با «قیمت و تخفیفِ ردیف» ِ روشن ([policy]) همان قاعده و پیام‌های کادرِ ردیفِ کامپیوتر زیرِ «قیمت هر واحد» (`LineModel.check`)؛
 *  ردِ قاعدهٔ جهت یا سقفِ قلمِ آزاد ⇒ «درخواستِ تأیید» ([priceAsk]) با همان برگهٔ تأییدِ مدیر (`PinApprovalSheet`)، و پس از تأیید
 *  «تأیید شد: …» و «ثبت» باز؛ رد بی تأیید ⇒ «ثبت» نمی‌نشیند (لرزش).
 *
 *  @param onSave نوشته‌های فرم، حالتِ آغاز و تأییدِ همان قیمت — صدازننده `LineModel.apply` را روی سبدِ همان لحظه می‌زند
 */
@Composable
fun LineSheet(
  info: LineInfo,
  onSave: (form: LineForm, start: LineForm, grant: PriceApproval.Grant?) -> Unit,
  onRemove: () -> Unit,
  onClose: () -> Unit,
  /** نوشته‌های آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  initial: LineForm? = null,
  /** S45 — سیاستِ قیمتِ ردیف (`LinePolicyPrefs.of`)؛ پیش‌فرض خاموش ⇒ همان برگهٔ پیشین */
  policy: LinePricing.Policy = LinePricing.Policy(),
  /** S45 — «درخواستِ تأیید» (`LinePolicyPrefs.ask`)؛ `null` ⇒ فقط پیامِ رد */
  priceAsk: PriceAsk? = null,
  /** POS-28 — سریال‌های کالای سریال‌دار (`posLineSerials`)؛ `null` (کالای دیگر یا قلمِ آزاد) ⇒ بی کادر */
  serials: PosLineSerials? = null,
  /** POS-22 — گام‌های «چرا این قیمت؟» (`PriceWhyLine.rows`)؛ `null` (تنظیمِ خاموش) ⇒ بی پنل */
  why: List<PriceWhy.Row>? = null,
  /** POS-11 — یادداشتِ ردیف (`posLineNote`)؛ `null` (تنظیمِ خاموش) ⇒ بی کادر */
  note: PosLineNote? = null,
) {
  val c = PhoneV3.colors
  val start = remember(info.id) { LineModel.start(info) }
  var form by remember(info.id) { mutableStateOf(initial ?: start) }
  var tried by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  var grant by remember(info.id) { mutableStateOf<PriceApproval.Grant?>(null) }
  var asking by remember { mutableStateOf(false) }
  var serialText by remember(info.id) { mutableStateOf(serials?.text.orEmpty()) }
  var noteText by remember(info.id) { mutableStateOf(note?.value.orEmpty()) }
  val gate = LineModel.check(policy, form, info, start, grant, canAsk = priceAsk != null)
  val unit = LineModel.shownUnit(form, info)
  val qtyError = LineModel.qtyError(form, info).takeIf { tried || form.qtyText != start.qtyText }
  val priceError = LineModel.priceError(form) ?: gate.edit.problem
  val total = rememberCountUp(LineModel.total(form, info, start))

  val ask = priceAsk
  val askCode = gate.askCode
  val typed = LineModel.typedPrice(form, info, start)
  if (asking && ask != null && askCode != null && typed != null) {
    //  برگهٔ تأیید جای همین برگه (نوشته‌ها در همین‌جا می‌مانند)؛ بستن ⇒ برگشت به ردیف
    PinApprovalSheet(
      StrTill.priceTitle, StrTill.priceSummary(info.name, PhoneSaleText.amount(info.basePrice), PhoneSaleText.amount(typed)),
      ask.selfRole, ask.selfName, ask.requesterUserId,
      onApproved = { by, role, reason -> grant = LineModel.grantOf(askCode, by, role, reason, ask.selfName, info, typed); asking = false },
      onClose = { asking = false },
    )
    return
  }

  V3Sheet(onDismiss = onClose, title = info.name, detents = false) { closer ->
    if (why != null) {
      //  POS-22: همان پنلِ `CartLineDialog` بالای برگه
      PriceWhyPanel(why) { money(it) }
      Spacer(Modifier.height(14.dp))
    }
    if (info.units != null) {
      //  POS-09 (S05): پایه، واحدِ دوم و بسته‌ها — فقط با «واحدِ بزرگ در صندوق» ِ همین دستگاه
      V3Segmented(LineModel.unitLabels(info), form.unitAt, { form = LineModel.switchTo(form, info, it) })
      Spacer(Modifier.height(14.dp))
    } else if (info.hasSaleUnit) {
      V3Segmented(
        listOf(info.unit.ifBlank { StrSale.unitFallback }, info.saleUnit),
        if (form.inSaleUnit) 1 else 0,
        { form = LineModel.switchUnit(form, info, toSale = it == 1) },
      )
      Spacer(Modifier.height(14.dp))
    }
    Column(Modifier.v3Shake(shake.takeIf { it > 0 })) {
      V3TextField(
        form.qtyText, { form = form.copy(qtyText = it) },
        if (unit.isNotBlank()) StrSale.qtyIn(unit) else StrSale.qtyLabel,
        Modifier.fillMaxWidth(),
        kind = V3FieldKind.NUMBER,
        required = true,
        error = qtyError,
        helper = LineModel.qtyHelper(form, info),
        suffix = unit.ifBlank { null },
      )
      Spacer(Modifier.height(12.dp))
      V3TextField(
        form.priceText, { form = form.copy(priceText = it, resetPrice = false, priceTouched = true) },
        StrSale.unitPrice,
        Modifier.fillMaxWidth(),
        kind = V3FieldKind.MONEY,
        error = priceError,
        helper = if (info.free) null else LineModel.packPriceText(form, info) ?: StrSale.basePriceHelper(PhoneSaleText.amount(info.basePrice)),
      )
      if (!info.free && info.unitPrice != null && !form.resetPrice) {
        V3TextButton(StrSale.backToItemPrice, { form = LineModel.resetPrice(form, info) }, Modifier.padding(top = 4.dp))
      }
      if (askCode != null && ask != null) V3TextButton(StrTill.askApproval, { asking = true }, Modifier.padding(top = 4.dp))
      gate.granted?.let { Text(StrTill.approvedBy(it.by), style = V3Type.footnote, color = c.positive, modifier = Modifier.padding(top = 4.dp)) }
      //  POS-28: همان کادرِ `CartLineDialog` (شمارِ «N از M» و تکرار همان‌جا؛ فروخته‌شدن را ثبت می‌سنجد)
      if (serials != null) { Spacer(Modifier.height(12.dp)); PosSerialField(serialText, { serialText = it }, LineModel.quantity(form, info)) }
      //  POS-11: همان کادرِ یادداشت با قالب‌های همین دستگاه
      if (note != null) { Spacer(Modifier.height(12.dp)); PosNoteField(noteText, { noteText = it }, StrSalePages.lineNote) }
    }
    if (info.places.isNotEmpty()) {
      //  POS-16 — موجودیِ همین کالا در هر محل (فقط خواندنی؛ جمعِ محل‌ها همان موجودیِ کالا)
      Spacer(Modifier.height(14.dp))
      Text(StrPlaces.atTill, style = V3Type.footnote, color = c.inkMuted)
      for (pq in info.places) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(pq.label, style = V3Type.callout, color = c.ink, modifier = Modifier.weight(1f))
          Text(LineModel.placeQtyText(pq, info.unit), style = V3Type.callout, color = if (pq.milli < 0) c.negative else c.ink)
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.lineTotal, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
      Text(PhoneSaleText.amount(total), style = V3Type.title, color = c.ink)
    }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrSale.submit,
      {
        tried = true
        if (LineModel.valid(form, info) && gate.edit.ok) closer.close { onSave(form, start, gate.granted); serials?.save(serialText); note?.save?.invoke(noteText) } else shake++
      },
      Modifier.fillMaxWidth(),
      haptic = true,
    )
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      V3TextButton(StrSale.removeFromCartAlt, { closer.close(onRemove) }, danger = true)
    }
  }
}
