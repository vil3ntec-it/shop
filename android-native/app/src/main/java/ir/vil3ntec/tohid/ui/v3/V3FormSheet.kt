package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrKit

/** بستنِ برگهٔ فرم پس از کارِ تمام‌شده (ذخیره، ثبت) — بی پرسیدنِ «دور ریخته شود؟» */
@Stable
class V3FormSheetScope internal constructor(private val closer: V3SheetCloser, private val markDone: () -> Unit) {
  /** برگه با حرکت بسته می‌شود و بعد `after` اجرا می‌شود؛ پرسشِ دورریختن نمی‌آید */
  fun finish(after: () -> Unit) {
    markDone()
    closer.close(after)
  }
}

/**
 *  برگهٔ فرم (ورود، اصلاح، پرداخت…): همان `V3Sheet` ِ کیت با نگهبانِ «دور ریخته شود؟».
 *
 *  اگر `dirty` است و برگه کشیده شود، روی پس‌زمینه زده شود یا «برگشت» زده شود، اول `V3ConfirmSheet`
 *  با `StrKit.discardTitle` / `discardBody` / `discard` می‌آید؛ «ادامهٔ ویرایش» برگه را با همان وضع
 *  دوباره باز می‌کند (حالتِ فرم بیرونِ برگه، نزدِ صدازننده، نگه داشته می‌شود). کارِ تمام‌شده با
 *  `scope.finish { … }` می‌بندد. بی تغییر، بستن مستقیم `onClose` است.
 *
 *  @param scroll false ⇒ محتوا خودش پیمایش دارد (بخشِ پیمایش‌پذیرِ فرم + دکمهٔ چسبان)
 */
@Composable
fun V3FormSheet(
  title: String,
  dirty: Boolean,
  onClose: () -> Unit,
  scroll: Boolean = true,
  content: @Composable ColumnScope.(V3FormSheetScope) -> Unit,
) {
  var open by remember { mutableStateOf(true) }
  var askDiscard by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  val isDirty by rememberUpdatedState(dirty)
  val close by rememberUpdatedState(onClose)
  if (open) {
    V3Sheet(
      onDismiss = {
        when {
          done -> Unit
          isDirty -> { open = false; askDiscard = true }
          else -> close()
        }
      },
      title = title,
      scroll = scroll,
    ) { closer ->
      val scope = remember(closer) { V3FormSheetScope(closer) { done = true } }
      content(scope)
    }
  }
  if (askDiscard) {
    V3ConfirmSheet(
      title = StrKit.discardTitle,
      message = StrKit.discardBody,
      confirm = StrKit.discard,
      onConfirm = { close() },
      onDismiss = { askDiscard = false; open = true },
    )
  }
}
