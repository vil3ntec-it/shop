package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SheetCloser

/** بستنِ برگهٔ فرم پس از کارِ تمام‌شده — بی پرسیدنِ «دور ریخته شود؟» */
@Stable
class FormSheetScope internal constructor(private val closer: V3SheetCloser, private val markDone: () -> Unit) {
  fun finish(after: () -> Unit) {
    markDone()
    closer.close(after)
  }
}

/**
 *  برگهٔ فرم (ورود، اصلاح): همان `V3Sheet` ِ کیت، و اگر نوشتهٔ ذخیره‌نشده هست، بستن با
 *  کشیدن/پس‌زمینه/برگشت اول «دور ریخته شود؟» می‌پرسد — «ادامهٔ ویرایش» برگه را با همان
 *  نوشته‌ها برمی‌گرداند (حالت بیرونِ برگه نگه داشته می‌شود). پیشنهادِ کیت (`Draft`):
 *  گزینهٔ `dirty` برای `V3Sheet`.
 */
@Composable
internal fun DraftFormSheet(
  title: String,
  dirty: Boolean,
  onClose: () -> Unit,
  content: @Composable ColumnScope.(FormSheetScope) -> Unit,
) {
  var open by remember { mutableStateOf(true) }
  var askDiscard by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  val isDirty by rememberUpdatedState(dirty)
  if (open) {
    V3Sheet(
      onDismiss = {
        when {
          done -> Unit
          isDirty -> { open = false; askDiscard = true }
          else -> onClose()
        }
      },
      title = title,
    ) { closer ->
      val scope = remember(closer) { FormSheetScope(closer) { done = true } }
      content(scope)
    }
  }
  if (askDiscard) {
    V3ConfirmSheet(
      title = StrKit.discardTitle,
      message = StrKit.discardBody,
      confirm = StrKit.discard,
      onConfirm = onClose,
      onDismiss = { askDiscard = false; open = true },
    )
  }
}
