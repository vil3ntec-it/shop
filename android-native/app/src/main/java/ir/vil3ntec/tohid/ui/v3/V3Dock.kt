package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/** کمینهٔ جای لمسِ هر دکمهٔ نوارِ کارها (۴۸dp) */
val V3DockTarget: Dp = 48.dp

/**
 *  نوارِ کارهای پایین (کارِ یک‌دستی، صاحب‌کار ۱۴۰۵/۰۸/۲۳): آنچه شست زیاد می‌زند پایینِ صفحه، درست بالای
 *  نوارِ شناور — **یک** جزء برای همهٔ فهرست‌ها. `V3Scaffold` خودش می‌کشدش (`search`، `chips`، `dock`) و
 *  صفحه‌ها مستقیم صدایش نمی‌زنند؛ کامپیوتر (`LocalDeskLayout`) همان چیدمانِ بالای پیشین را دارد.
 *
 *  از بالا به پایین: تراشه‌های فیلتر (`chips`)، سپس یک ردیف — کادرِ جست‌وجو (پهنای باقی‌مانده؛ هرگز خودش
 *  فوکوس نمی‌گیرد و صفحه‌کلید را باز نمی‌کند) و دکمه‌های گردِ ۴۸dp (اسکن، مرتب‌سازی/فیلتر، نما، …).
 *  صفحه‌کلیدِ باز نوار را بالای خودش می‌برد؛ [below] آن بخش از پایینِ صفحه است که زیرِ نوار است و
 *  صفحه‌کلید اول آن را می‌پوشاند (نوارِ چسبانِ زیرین).
 */
@Composable
fun V3Dock(
  modifier: Modifier = Modifier,
  search: (@Composable () -> Unit)? = null,
  chips: (@Composable () -> Unit)? = null,
  actions: List<V3Action> = emptyList(),
  below: Dp = 0.dp,
) {
  val c = PhoneV3.colors
  Column(
    modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars.add(WindowInsets(bottom = below))))
      .background(c.bg)
      .semantics { isTraversalGroup = true },
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    if (chips != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { chips() }
    if (search != null || actions.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (search != null) Box(Modifier.weight(1f).heightIn(min = V3DockTarget), contentAlignment = Alignment.Center) { search() }
        actions.forEach { a -> V3IconButton(a.icon, a.label, size = V3DockTarget, dot = a.dot, onClick = a.onClick) }
      }
    } else {
      Box(Modifier.height(8.dp))
    }
  }
}
