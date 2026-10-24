package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.screens.V3GroupTitle
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/** چند میلی‌ثانیه کارتِ خواسته‌شده (`?tab=`) با حاشیهٔ برند دیده می‌شود */
private const val HIGHLIGHT_MS = 1400L

/**
 *  «دستگاه‌های جانبی» (`devices?tab=printers|scanners|phone|live|photos`): هر چیزی
 *  که به برنامه وصل است، در کارت‌های پشتِ هم — چاپگرها، بارکدخوان‌ها، گوشی ⇄
 *  کامپیوتر، همگام‌سازیِ زنده و عکسِ کالاها. `tab` تا همان کارت می‌رود و لحظه‌ای
 *  نشانش می‌دهد. منطق همه از موتورهای امروز (`PrinterRegistry`، `PhoneScanner`، …).
 */
@Composable
internal fun DevicesScreen(data: ShopData, tab: String?, onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val list = rememberLazyListState()
  val target = remember(tab) { DevicesModel.tab(tab) }
  var lit by remember(tab) { mutableStateOf(target) }
  LaunchedEffect(Unit) { PhoneScanner.load(context); PhotoPrefs.load(context) }
  LaunchedEffect(target) {
    val t = target ?: return@LaunchedEffect
    list.animateScrollToItem(t.ordinal + 1)
    kotlinx.coroutines.delay(HIGHLIGHT_MS)
    lit = null
  }
  SettingsPageFrame(title = Str.devices, onBack = onBack, state = list) {
    item(key = "intro") { SettingsNote(StrSettings.devicesIntro) }
    item(key = DeviceTab.PRINTERS.id) { PrintersCard(lit == DeviceTab.PRINTERS) }
    item(key = DeviceTab.SCANNERS.id) { ScannersCard(lit == DeviceTab.SCANNERS) }
    item(key = DeviceTab.PHONE.id) { PhoneLinkCard(data, lit == DeviceTab.PHONE) }
    item(key = DeviceTab.LIVE.id) { LiveCard(lit == DeviceTab.LIVE) }
    item(key = DeviceTab.PHOTOS.id) { PhotosCard(lit == DeviceTab.PHOTOS) }
  }
}

/**
 *  کارتِ یک دستگاه: سرخطِ بیرونِ کارت، کارتِ مرزدار (با حاشیهٔ برندِ لحظه‌ای وقتی
 *  پیوند همین‌جا را خواسته) و یک خطِ توضیح زیرش.
 */
@Composable
internal fun DeviceCard(
  title: String,
  caption: String?,
  highlight: Boolean,
  padding: PaddingValues = PaddingValues(vertical = 4.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  val ring by animateColorAsState(if (highlight) PhoneV3Roles.focus(c) else Color.Transparent, MotionSpec.tweenOf(MotionTokens.LONG), label = "deviceRing")
  Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
    V3GroupTitle(title)
    V3Card(Modifier.border(2.dp, ring, RoundedCornerShape(PhoneV3.groupRadius)), padding = padding, content = content)
    if (caption != null) Text(caption, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp))
  }
}
