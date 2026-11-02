package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.SimpleAskPref

/** بخشِ «پرسشِ ساده از گزارش‌ها» (RPT-16) با خواندن و نوشتنِ `SimpleAskPref` — مرزِ پلتفرم، تا `Settings` یک خط بخواهد */
@Composable
fun SimpleAskSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(SimpleAskPref.on(context)) }
  SimpleAskSettings(on) { on = it; SimpleAskPref.set(context, it) }
}
