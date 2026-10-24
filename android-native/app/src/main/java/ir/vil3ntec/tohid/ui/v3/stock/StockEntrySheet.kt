package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import kotlinx.coroutines.delay

/**
 *  `stock/entry?product=&barcode=` — برگهٔ «ورود کالا به انبار» (فرمِ نسخهٔ ۳ در برگه):
 *  کالا (انتخاب‌گرِ جست‌وجودار در همان برگه، با حرکتِ صفحه و تغییرِ ارتفاع)، مقدار
 *  (کارتن × در هر کارتن ⇄ واحد، با «= ۱۴۴ عدد»ِ زنده)، قیمتِ خرید با جمعِ زنده، و
 *  جزئیاتِ تاشوی محموله. ردِ کادر یا موتور فرم را می‌لرزاند؛ «ثبت ورود» تیکِ موفقیت دارد و
 *  برگه با حرکت بسته می‌شود. نوشتهٔ ذخیره‌نشده + بستن ⇐ «دور ریخته شود؟».
 *
 *  @param onSave نتیجهٔ موتور: null یعنی ثبت شد؛ وگرنه دلیلِ رد
 */
@Composable
fun StockEntrySheet(
  data: ShopData,
  product: String?,
  barcode: String?,
  onSave: (WarehouseEngine.EntryDraft) -> String?,
  onNewProduct: (barcode: String?) -> Unit,
  onClose: () -> Unit,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش)؛ null ⇒ از مسیر */
  start: EntryForm? = null,
) {
  val flow = rememberEntryFlow(data, product, barcode, start)
  //  کادرها خودشان پیمایش دارند و «ثبت ورود» زیرشان چسبان می‌ماند ⇒ برگه پیمایش نمی‌کند
  DraftFormSheet(title = flow.title, dirty = flow.dirty, onClose = onClose, scroll = false) { sheet ->
    if (flow.saved) LaunchedEffect(Unit) { delay(ENTRY_CLOSE_DELAY); sheet.finish(onClose) }
    val onNew = { sheet.finish { onNewProduct(flow.unknown) } }
    V3PageSwitch(flow.step, depth = { if (it == EntryStep.PICK) 1 else 0 }) { s ->
      when (s) {
        EntryStep.PICK -> EntryPick(flow, data, onNew)
        EntryStep.FORM -> Column {
          EntryFormFields(flow, data, onNew, Modifier.weight(1f, fill = false).heightIn(max = 560.dp).verticalScroll(rememberScrollState()))
          Spacer(Modifier.height(14.dp))
          Box(contentAlignment = Alignment.Center) {
            V3Button(StrStock.submitEntry, { flow.submit(data, onSave) }, Modifier.fillMaxWidth(), enabled = !flow.saved, haptic = true)
            //  لرزشِ «ثبت شد» را خودِ ثبت (`StockWriter`) داده است
            V3SuccessCheck(flow.saved, size = 44.dp, haptic = false)
          }
        }
      }
    }
  }
}

/**
 *  همان فرم به‌شکلِ صفحه — مقصدِ دکمهٔ شناورِ «ورود کالا» در انبار: دکمه با container
 *  transform به همین صفحه باز می‌شود و با بستن به دکمه برمی‌گردد (`modifier` همان کلیدِ
 *  `v3Container`). برگشت: با نوشته ⇐ «دور ریخته شود؟» (`V3Form`)، بی نوشته ⇐ بستن.
 */
@Composable
internal fun StockEntryPage(
  data: ShopData,
  flow: EntryFlow,
  onSave: (WarehouseEngine.EntryDraft) -> String?,
  onNewProduct: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(enabled = !flow.dirty) { onClose() }
  if (flow.saved) LaunchedEffect(Unit) { delay(ENTRY_CLOSE_DELAY); onClose() }
  val pick = flow.step == EntryStep.PICK
  V3Form(
    title = flow.title,
    primary = StrStock.submitEntry,
    onPrimary = { flow.submit(data, onSave) },
    modifier = modifier,
    onBack = { if (pick && flow.form.productId.isNotEmpty()) flow.step = EntryStep.FORM else onClose() },
    primaryEnabled = !flow.saved && !pick,
    dirty = flow.dirty,
  ) {
    Spacer(Modifier.height(8.dp))
    V3PageSwitch(flow.step, depth = { if (it == EntryStep.PICK) 1 else 0 }) { s ->
      when (s) {
        EntryStep.PICK -> EntryPick(flow, data, onNewProduct)
        EntryStep.FORM -> Box(contentAlignment = Alignment.TopCenter) {
          EntryFormFields(flow, data, onNewProduct)
          V3SuccessCheck(flow.saved, Modifier.padding(top = 120.dp), haptic = false)
        }
      }
    }
  }
}
