package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.LinePolicyPrefs

/** بخشِ «قیمت و تخفیفِ ردیف» با خواندن و نوشتنِ `LinePolicyPrefs` — مرزِ پلتفرم، تا `Settings` یک خط بخواهد */
@Composable
fun LinePricingSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(LinePolicyPrefs.on(context)) }
  var direction by remember { mutableStateOf(LinePolicyPrefs.direction(context)) }
  LinePricingSettings(
    on = on,
    direction = direction,
    capMinor = remember { LinePolicyPrefs.freeLineCapMinor(context) },
    onOn = { on = it; LinePolicyPrefs.setOn(context, it) },
    onDirection = { direction = it; LinePolicyPrefs.setDirection(context, it) },
    onCap = { LinePolicyPrefs.setFreeLineCapMinor(context, it) },
  )
}
