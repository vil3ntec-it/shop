package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  قابِ هر صفحهٔ تنظیمات (r5): `V3Scaffold` با عنوانِ درشتِ جمع‌شونده (ریشه: عنوانِ
 *  کوچکِ وسط) و برگشتِ گرد، و یک فهرستِ پیمایشی با گوشهٔ ۱۶. روی کامپیوتر همین قاب
 *  در ستونِ پایان کشیده می‌شود، بی دکمهٔ برگشت (`onBack = null`). هر ردیفِ فهرست بارِ
 *  اول با ورودِ پلکانیِ کیت می‌آید (`v3Stagger`: محو + بالا آمدن، ۳۰ms، ده تای اول).
 */
@Composable
internal fun SettingsPageFrame(
  title: String,
  onBack: (() -> Unit)?,
  subtitle: String? = null,
  largeTitle: Boolean = true,
  actions: List<V3Action> = emptyList(),
  state: LazyListState = rememberLazyListState(),
  bottomBar: (@Composable () -> Unit)? = null,
  content: LazyListScope.() -> Unit,
) {
  val stagger = rememberV3Stagger(title)
  V3Scaffold(title = title, subtitle = subtitle, onBack = onBack, actions = actions, largeTitle = largeTitle, bottomBar = bottomBar) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 720.dp).fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
      ) { StaggeredList(this, stagger).content() }
    }
  }
}

/** بندِ توضیح زیر یا میانِ کارت‌ها — خاکستری و کوتاه */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = V3Type.footnote,
    color = PhoneV3.colors.inkMuted,
    modifier = modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp),
  )
}

/** کارتِ مرزدار با فاصلهٔ ۱۶ برای محتوای آزاد (توضیح، کادر، دکمه) */
@Composable
internal fun SettingsBlock(modifier: Modifier = Modifier, top: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
  V3Card(modifier.padding(top = if (top) 16.dp else 0.dp), padding = PaddingValues(16.dp), content = content)
}

/** همان فهرست، با ورودِ پلکانیِ کیت برای هر `item` به ترتیبِ آمدن */
private class StaggeredList(private val base: LazyListScope, private val stagger: V3StaggerState) : LazyListScope by base {
  private var next = 0

  override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
    val index = next++
    val id = key ?: index
    //  بیرونِ لامبدا: نشانگرِ DSL ِ فهرست دسترسی به گیرندهٔ بیرونی را درونِ ردیف نمی‌پذیرد
    val state = stagger
    base.item(key, contentType) {
      val scope = this
      //  ردیفِ کلیددار آمدن و رفتنش هم حرکت دارد (`v3AnimateItem`)
      Box((if (key != null) Modifier.v3AnimateItem(scope) else Modifier).v3Stagger(state, id, index)) { scope.content() }
    }
  }
}
