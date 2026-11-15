package ir.vil3ntec.tohid.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  جست‌وجوی یک بخش (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «چرا جستجو را بینِ نوارها آوردی»): دادهٔ کادر، نه خودِ کادر —
 *  `V3Scaffold` روی گوشی و تبلت آن را فقط وقتی می‌کشد که جست‌وجو باز است (از دکمهٔ جست‌وجوی نوارِ پایین، یا
 *  ذره‌بینِ سربرگ وقتی نوار آن دکمه را ندارد)، و روی کامپیوتر زیرِ عنوان. اسکن آیکنی درونِ همین کادر است.
 */
@Immutable
data class V3Search(
  val query: String,
  val onQuery: (String) -> Unit,
  val hint: String,
  val onScan: (() -> Unit)? = null,
  val onVoice: (() -> Unit)? = null,
  val onSubmit: (() -> Unit)? = null,
)

/**
 *  دکمهٔ جست‌وجوی نوارِ پایین آگاه از بخش: هر صفحه‌ای که جست‌وجوی خودش را دارد تا هنگامِ کشیده شدن این‌جا ثبت است
 *  (تازه‌ترین بالا — همان صفحهٔ رو). پوسته (`AppRoot`، پیش‌نمایش‌ها) پیش از رفتن به صفحهٔ جست‌وجوی کل [openCurrent]
 *  را می‌زند؛ بخشی که جست‌وجو ندارد (خانه، …) همان جست‌وجوی کل می‌ماند.
 */
object V3ScopedSearch {
  private val stack = ArrayList<() -> Unit>()

  internal fun register(open: () -> Unit) { stack += open }

  internal fun unregister(open: () -> Unit) { stack.remove(open) }

  /** true ⇒ صفحهٔ رو جست‌وجوی خودش را باز کرد (و صفحهٔ جست‌وجوی کل باز نمی‌شود) */
  fun openCurrent(): Boolean {
    val open = stack.lastOrNull() ?: return false
    open()
    return true
  }

  /** صفحهٔ رو جست‌وجوی خودش را دارد؟ (آزمون) */
  val active: Boolean get() = stack.isNotEmpty()
}

/**
 *  پوسته می‌گوید دکمهٔ جست‌وجوی نوارِ پایین روی این صفحه هست؛ نه ⇒ سربرگ خودش ذره‌بین می‌گذارد تا جست‌وجو
 *  هرگز بی‌راه نماند (نوارِ ویرایش‌شده بی جست‌وجو، تبلتِ بی نوار، پیش‌نمایش).
 */
val LocalV3NavSearch = compositionLocalOf { false }

/** باز/بستهٔ جست‌وجوی یک بخش — [shown] تا وقتی چیزی نوشته شده هم باز می‌ماند (فیلترِ پنهان نیست) */
@Stable
class V3SearchToggle internal constructor(
  private val opened: () -> Boolean,
  private val query: () -> String,
  val open: () -> Unit,
  val close: () -> Unit,
) {
  val shown: Boolean get() = opened() || query().isNotEmpty()
}

/**
 *  ثبتِ جست‌وجوی بخش برای دکمهٔ نوار و نگه‌داشتنِ باز/بسته. [enabled] = false (کامپیوتر، بی جست‌وجو) ⇒ ثبت نمی‌شود.
 *  «بستن» یا برگشتِ سیستم نوشته را پاک می‌کند تا فهرست بی‌نشان فیلترشده نماند. صفحه‌کلید خودبه‌خود باز نمی‌شود.
 */
@Composable
fun rememberV3SearchToggle(search: V3Search?, enabled: Boolean = true): V3SearchToggle {
  var opened by rememberSaveable { mutableStateOf(false) }
  val latest by rememberUpdatedState(search)
  val toggle = remember {
    V3SearchToggle(
      opened = { opened },
      query = { latest?.query.orEmpty() },
      open = { opened = true },
      close = { latest?.onQuery?.invoke(""); opened = false },
    )
  }
  val live = enabled && search != null
  DisposableEffect(live) {
    val handler = toggle.open
    if (live) V3ScopedSearch.register(handler)
    onDispose { V3ScopedSearch.unregister(handler) }
  }
  BackHandler(enabled = live && toggle.shown, onBack = toggle.close)
  return toggle
}

/**
 *  نوارِ جست‌وجو جای سربرگ (گوشی و تبلت): کادر با اسکن/صدا درونش و «بستن». زیرش همان تراشه‌ها و فهرستِ همان بخش
 *  که با هر حرف کوتاه می‌شود — نتیجه همان‌جاست، نه صفحهٔ دیگر.
 */
@Composable
fun V3SearchBar(search: V3Search, onClose: () -> Unit, transparent: Boolean = false) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(if (transparent) Color.Transparent else c.bg)
      .windowInsetsPadding(WindowInsets.statusBars)
      .semantics { isTraversalGroup = true },
  ) {
    Row(
      Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.weight(1f)) {
        V3SearchField(search.query, search.onQuery, search.hint, onScan = search.onScan, onVoice = search.onVoice, onSubmit = search.onSubmit)
      }
      Spacer(Modifier.width(4.dp))
      V3TextButton(StrKit.close, onClose)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
  }
}

/** همان کادر زیرِ عنوان (کامپیوتر) — فاصله‌های ۸dp */
@Composable
internal fun V3InlineSearch(search: V3Search) {
  Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
    V3SearchField(search.query, search.onQuery, search.hint, onScan = search.onScan, onVoice = search.onVoice, onSubmit = search.onSubmit)
  }
}
