package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind

/**
 *  فرمِ نسخهٔ ۳: سربرگِ `V3Scaffold` (عنوانِ کوچکِ وسط، برگشت)، بدنهٔ پیمایش‌پذیر
 *  از `V3Section`ها با کادرهای `V3TextField`، و نوارِ **چسبانِ** پایین با دکمهٔ
 *  اصلی (و دومِ اختیاری) که بالای صفحه‌کلید می‌ماند. `summary` یک خطِ کوتاه
 *  بالای دکمه‌ها («۳ کالا آمادهٔ ثبت»).
 *
 *  `dirty`: برگشت با نوشتهٔ ذخیره‌نشده اول می‌پرسد (برگهٔ «دور انداختن»).
 *
 *  سنجش درجا کارِ صفحه است (خطای هر کادر زیرِ خودش)، و دکمهٔ اصلی تا وقتی
 *  فرم آماده نیست غیرفعال می‌ماند یا با زدن اولین خطا را نشان می‌دهد.
 */
@Composable
fun V3Form(
  title: String,
  primary: String,
  onPrimary: () -> Unit,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  actions: List<V3Action> = emptyList(),
  primaryEnabled: Boolean = true,
  loading: Boolean = false,
  secondary: String? = null,
  onSecondary: (() -> Unit)? = null,
  summary: String? = null,
  /** نوشته‌ای ذخیره‌نشده هست ⇒ «برگشت» (دکمه یا کلیدِ گوشی) اول برگهٔ «دور ریخته شود؟» */
  dirty: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  var askDiscard by remember { mutableStateOf(false) }
  BackHandler(enabled = dirty && onBack != null) { askDiscard = true }
  if (askDiscard && onBack != null) {
    V3ConfirmSheet(
      title = StrKit.discardTitle,
      message = StrKit.discardBody,
      confirm = StrKit.discard,
      onConfirm = onBack,
      onDismiss = { askDiscard = false },
    )
  }
  V3Scaffold(
    title = title,
    modifier = modifier,
    onBack = onBack?.let { back -> { if (dirty) askDiscard = true else back() } },
    actions = actions,
    largeTitle = false,
    notice = null,
    bottomBar = {
      V3FormBar(primary, onPrimary, primaryEnabled, loading, secondary, onSecondary, summary)
    },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
          .widthIn(max = 640.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())
          .background(c.bg),
        content = content,
      )
    }
  }
}

/** نوارِ چسبانِ پایینِ فرم — بالای صفحه‌کلید و نوارِ سیستم */
@Composable
fun V3FormBar(
  primary: String,
  onPrimary: () -> Unit,
  primaryEnabled: Boolean = true,
  loading: Boolean = false,
  secondary: String? = null,
  onSecondary: (() -> Unit)? = null,
  summary: String? = null,
) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(c.bg)
      .windowInsetsPadding(if (LocalV3SystemBottom.current != null) WindowInsets.ime.exclude(WindowInsets.navigationBars) else WindowInsets.ime.union(WindowInsets.navigationBars)),
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
      if (summary != null) {
        Text(
          summary,
          style = v3Text(12, FontWeight.Medium, 17),
          color = c.inkMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondary != null && onSecondary != null) {
          V3Button(secondary, onSecondary, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
        }
        V3Button(primary, onPrimary, Modifier.weight(if (secondary != null) 1.4f else 1f), enabled = primaryEnabled, loading = loading, haptic = true)
      }
    }
  }
}
