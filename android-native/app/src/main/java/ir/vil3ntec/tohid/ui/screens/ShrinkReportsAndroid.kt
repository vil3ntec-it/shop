package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.ShrinkReportPref

/** بخشِ «گزارشِ ضایعات و کسری» با خواندن و نوشتنِ `ShrinkReportPref` — مرزِ پلتفرم، تا `Settings` یک خط بخواهد */
@Composable
fun ShrinkReportSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(ShrinkReportPref.on(context)) }
  ShrinkReportSettings(on) { on = it; ShrinkReportPref.set(context, it) }
}
