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
import androidx.compose.runtime.CompositionLocalProvider
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
import ir.vil3ntec.tohid.ui.v3.settings.LocalSettingsKey
import ir.vil3ntec.tohid.ui.screens.SettingsLayout

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
    //  کارتِ «این چه می‌کند؟» و یادداشتِ بالا پیش از کارت‌ها
    list.animateScrollToItem(t.ordinal + 2)
    kotlinx.coroutines.delay(HIGHLIGHT_MS)
    lit = null
  }
  //  «این چه می‌کند؟» همان کلیدِ ردیفِ «دستگاه‌ها» ِ تنظیمات (`SettingsHelp.example`)
  CompositionLocalProvider(LocalSettingsKey provides SettingsLayout.PERIPHERALS) {
  SettingsPageFrame(title = Str.devices, onBack = onBack, state = list) {
    item(key = "intro") { SettingsNote(StrSettings.devicesIntro) }
    item(key = DeviceTab.HUB.id) { HubCard(lit == DeviceTab.HUB) }
    item(key = DeviceTab.BACKGROUND.id) { BackgroundCard(lit == DeviceTab.BACKGROUND) }
    item(key = DeviceTab.PRINTERS.id) { PrintersCard(lit == DeviceTab.PRINTERS) }
    item(key = DeviceTab.SCANNERS.id) { ScannersCard(lit == DeviceTab.SCANNERS) }
    item(key = DeviceTab.PHONE.id) { PhoneLinkCard(data, lit == DeviceTab.PHONE) }
    item(key = DeviceTab.LIVE.id) { LiveCard(lit == DeviceTab.LIVE) }
    item(key = DeviceTab.PHOTOS.id) { PhotosCard(lit == DeviceTab.PHOTOS) }
    //  ابزارهای سخت‌افزارِ صفحهٔ دستگاه‌هایِ پیشین (چاپگر، صفِ چاپ، برچسب، ترازو، نمایشگرِ مشتری) — `DevicesHwAndroid.kt`
    item(key = "hw") { DevicesHwExtras(data) }
  }
  }
}

/** رنگ‌های بدنهٔ مشترکِ «دستگاه‌ها» در تمِ نسخهٔ ۳ */
@Composable
private fun hubColors(): ir.vil3ntec.tohid.ui.components.devices.HubColors {
  val c = PhoneV3.colors
  return ir.vil3ntec.tohid.ui.components.devices.HubColors(
    ok = PhoneV3Roles.toneIcon(c, ir.vil3ntec.tohid.ui.theme.V3Tone.SUCCESS), bad = PhoneV3Roles.toneIcon(c, ir.vil3ntec.tohid.ui.theme.V3Tone.ERROR), muted = c.inkMuted, text = c.ink,
  )
}

/** «همهٔ دستگاه‌ها»: جست‌وجو، نوعِ خودکار، چند وصلِ هم‌زمان، وضعیت و آزمایش (`DeviceHubBody`) */
@Composable
private fun HubCard(highlight: Boolean) {
  val snackbar = ir.vil3ntec.tohid.ui.v3.LocalV3Toasts.current
  DeviceCard(ir.vil3ntec.tohid.core.i18n.StrDevices.hubTitle, ir.vil3ntec.tohid.core.i18n.StrDevices.hubCaption, highlight, padding = PaddingValues(16.dp)) {
    ir.vil3ntec.tohid.ui.components.devices.DeviceHubBody(hubColors()) { snackbar?.show(it) }
  }
}

/** «اسکن وقتی برنامه بسته است» — کلید ندارد؛ با ثبتِ بارکدخوان خودش روشن است */
@Composable
private fun BackgroundCard(highlight: Boolean) {
  val snackbar = ir.vil3ntec.tohid.ui.v3.LocalV3Toasts.current
  DeviceCard(ir.vil3ntec.tohid.core.i18n.StrDevices.backgroundTitle, ir.vil3ntec.tohid.core.i18n.StrDevices.backgroundCaption, highlight, padding = PaddingValues(16.dp)) {
    ir.vil3ntec.tohid.ui.components.devices.BackgroundScanBody(hubColors())
  }
  //  «کارتِ فرمان» (فروشِ نقدی از بارکدخوان، پیش‌فرض خاموش)
  DeviceCard(ir.vil3ntec.tohid.core.i18n.StrDevices.cardTitle, null, false, padding = PaddingValues(16.dp)) {
    ir.vil3ntec.tohid.ui.components.devices.CommandCardBody(hubColors()) { snackbar?.show(it) }
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
