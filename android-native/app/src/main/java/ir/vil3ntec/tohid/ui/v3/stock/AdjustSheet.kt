package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3FormSheet
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Shake
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

/**
 *  `stock/{id}/adjust` — برگهٔ «اصلاح موجودی»: سه گونه (زیاد/کم/برگشت به تأمین‌کننده)،
 *  مقدار با واحد، دلیل (اجباریِ موتور) و پیش‌نمایشِ زندهٔ «۱۴۴ ← ۱۳۰ عدد». دکمه برای هر
 *  سه گونه «اصلی» است — اصلاح ثبت می‌شود، چیزی پاک نمی‌شود.
 *
 *  @param onSave null یعنی ثبت شد؛ وگرنه دلیلِ ردِ `WarehouseEngine.adjustStock`
 */
@Composable
fun AdjustSheet(
  product: Product,
  stock: Double,
  onSave: (AdjustForm) -> String?,
  onClose: () -> Unit,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  start: AdjustForm = AdjustForm(),
) {
  var form by remember { mutableStateOf(start) }
  var showErrors by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  var saved by remember { mutableStateOf(false) }
  var shake by remember { mutableIntStateOf(0) }
  val errors = if (showErrors) form.errors() else emptyMap()
  val unit = product.unit.ifBlank { null }

  V3FormSheet(if (form.choice == AdjustChoice.RETURN) StrStock.returnTitle(product.name) else StrStock.adjustTitle(product.name), dirty = form.dirty && !saved, onClose = onClose) { sheet ->
    if (saved) LaunchedEffect(Unit) { delay(MotionTokens.EMPHASIZED + 150L); sheet.finish(onClose) }
    V3Segmented(
      listOf(StrStock.kindUp, StrStock.kindDown, StrStock.kindReturnShort),
      form.choice.ordinal,
      { form = form.copy(choice = AdjustChoice.entries[it]); failure = null },
    )
    Spacer(Modifier.height(16.dp))
    Column(Modifier.v3Shake(shake.takeIf { it > 0 })) {
    V3TextField(
      form.amount, { form = form.copy(amount = it); failure = null }, StrStock.quantity, Modifier.fillMaxWidth(),
      kind = V3FieldKind.NUMBER, suffix = unit, required = true, error = errors[AdjustForm.AMOUNT],
    )
    Spacer(Modifier.height(12.dp))
    V3TextField(
      form.reason, { form = form.copy(reason = it); failure = null }, StrStock.reason, Modifier.fillMaxWidth(),
      helper = StrStock.reasonHelper, required = true, error = errors[AdjustForm.REASON], singleLine = false,
    )
    failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 12.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    Preview(stock, form.after(stock), unit.orEmpty())
    Spacer(Modifier.height(14.dp))
    Box(contentAlignment = Alignment.Center) {
      V3Button(StrStock.submit, {
        showErrors = true
        if (form.errors().isEmpty()) {
          failure = onSave(form)
          saved = failure == null
        }
        if (!saved) shake++
      }, Modifier.fillMaxWidth(), enabled = !saved, haptic = true)
      V3SuccessCheck(saved, size = 44.dp, haptic = false)
    }
  }
}

/** «۱۴۴ ← ۱۳۰ عدد» — عددِ تازه با شمارندهٔ کیت به جای خودش می‌رسد */
@Composable
private fun Preview(stock: Double, after: Double?, unit: String) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(after ?: stock)
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(StrStock.stock, style = V3Type.footnote, color = c.inkMuted)
    Text(
      if (after == null) listOf(qty(stock), unit).filter { it.isNotBlank() }.joinToString(" ")
      else StrStock.preview(qty(stock), qty(shown), unit).trim(),
      style = V3Type.title,
      color = if (after != null && after < 0) PhoneV3Roles.errorText(c) else c.ink,
      textAlign = TextAlign.Center,
    )
  }
}
