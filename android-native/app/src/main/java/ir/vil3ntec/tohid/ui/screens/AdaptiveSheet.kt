package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.ui.Platform

/**
 *  «برگهٔ پایین» روی گوشی، **پنجرهٔ وسطِ صفحه** روی کامپیوتر.
 *
 *  برگه‌ای که از پایینِ مانیتورِ ۲۴ اینچی بالا بیاید و تمامِ عرض را بگیرد،
 *  رفتارِ گوشی است. روی کامپیوتر همان محتوا در پنجره‌ای به پهنای ۵۶۰
 *  نقطه وسطِ صفحه باز می‌شود؛ Esc یا کلیک بیرونِ آن می‌بندد. گوشی: همان `V3Sheet` ِ همهٔ برگه‌ها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
  onDismissRequest: () -> Unit,
  containerColor: Color,
  shape: Shape? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (Platform.DESKTOP) {
    Dialog(onDismissRequest = onDismissRequest) {
      Surface(
        color = containerColor,
        shape = ir.vil3ntec.tohid.ui.theme.Shape.dialog,
        shadowElevation = 12.dp,
        modifier = Modifier.widthIn(min = 380.dp, max = 560.dp).fillMaxWidth().heightIn(max = 640.dp),
      ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 18.dp), content = content)
      }
    }
  } else {
    //  p0 (کالبدِ یکسان): گوشی همان **یک** سبکِ برگه را دارد (`V3Sheet`: گوشهٔ ۲۸، دستگیره، کاهشِ حرکت S110) — نه برگهٔ خامِ
    //  Material با رنگ و گوشهٔ هر صفحه. محتوا مثلِ پیش خودش پیمایش می‌کند (`scroll = false`)؛ [containerColor]/[shape] فقط کامپیوتر.
    ir.vil3ntec.tohid.ui.v3.V3Sheet(onDismiss = onDismissRequest, scroll = false) { content() }
  }
}
