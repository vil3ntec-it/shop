package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop

/* ============================ ابزارهای فرم ============================ */

/** بدنهٔ برگه: روی گوشی خودش می‌لغزد؛ کامپیوتر را `AdaptiveSheet` می‌لغزاند */
@Composable
internal fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
  val scroll = if (Platform.DESKTOP) Modifier else Modifier.verticalScroll(rememberScrollState())
  Column(Modifier.fillMaxWidth().then(scroll), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun ErrorLine(text: String?) {
  if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
}
