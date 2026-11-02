package ir.vil3ntec.tohid.ios

import android.content.IosContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.ui.UiV3
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Tab
import ir.vil3ntec.tohid.ui.v3.V3TabBar
import ir.vil3ntec.tohid.ui.v3.gallery.KitGallery
import ir.vil3ntec.tohid.ui.v3.gallery.KitPart
import ir.vil3ntec.tohid.ui.v3.modes.ModeChooser
import ir.vil3ntec.tohid.ui.v3.modes.ModeState

/*
 *  ریشهٔ برنامهٔ آیفون — گامِ ۱ (docs/IOS-PLAN-fa.md، بخشِ ۰).
 *
 *  هیچ صفحه‌ای این‌جا نوشته نشده: همهٔ آنچه دیده می‌شود همان composableهای اندروید است که
 *  `ios/shared` برای iOS کامپایل می‌کند — تم و قلمِ Vazirmatn (`TohidTheme`)، نخستین صفحهٔ
 *  برنامه (`ModeChooser`، انتخابِ «دکان/پیک») و کیتِ نسخهٔ ۳ (`KitGallery`، همهٔ اجزا در
 *  همهٔ حالت‌ها) با نوارِ پایینِ مشترک (`V3TabBar`).
 *
 *  ⛔ ورود، فروش و فهرستِ کالا هنوز این‌جا نیستند، چون فایل‌هایشان هنوز برای iOS کامپایل
 *  نمی‌شوند (نه این‌که نسخهٔ جدا لازم باشد): دفترِ فروشگاه (`ShopStore`) رمزگذاریِ AES-GCM ِ
 *  JDK می‌خواهد، ورود شبکهٔ `HttpURLConnection` (`HttpEngine`)، و فهرستِ کالا تصویرِ `Bitmap`.
 *  هر کدام که در گامِ ۲ shim یا جایگزینِ iOS بگیرد، همان صفحهٔ اندروید این‌جا وصل می‌شود.
 *
 *  تنها کارِ این فایل همان کارِ `MainActivity` است: بارگذاریِ تنظیم‌ها و فراهم کردنِ
 *  `LocalContext`/`LocalConfiguration` و جهتِ نوشته. (`ProvideLang` هنوز iOS نیست؛ جهت همان
 *  قاعدهٔ `directionOf` ِ ‎ui/LangUi.kt‎.)
 */
private object Boot {
  val done: Boolean by lazy {
    Motion.load(IosContext)
    LocaleChoice.load(IosContext)
    ShopDayChoice.load(IosContext)
    ThemeStore.load(IosContext)
    ModeState.load(IosContext)
    UiV3.load(IosContext)
    true
  }
}

@Composable
fun IosRoot() {
  remember { Boot.done }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
      LocalContext provides IosContext,
      LocalConfiguration provides Configuration(maxWidth.value.toInt(), maxHeight.value.toInt()),
      LocalLayoutDirection provides if (LocaleChoice.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
      TohidTheme { IosShell() }
    }
  }
}

private val kitTabs = listOf(
  V3Tab(KitPart.BASICS.name, "پایه", Icons.Rounded.Palette),
  V3Tab(KitPart.FORMS.name, "فرم", Icons.Rounded.EditNote),
  V3Tab(KitPart.LISTS.name, "فهرست", Icons.Rounded.ViewList),
  V3Tab(KitPart.STATES.name, "حالت‌ها", Icons.Rounded.Inbox),
)

@Composable
private fun IosShell() {
  var chosen by rememberSaveable { mutableStateOf(false) }
  if (!chosen) {
    ModeChooser { picked ->
      ModeState.choose(IosContext, picked)
      chosen = true
    }
    return
  }
  var part by rememberSaveable { mutableStateOf(KitPart.BASICS.name) }
  Box(Modifier.fillMaxSize()) {
    KitGallery(KitPart.valueOf(part), onBack = { chosen = false })
    V3TabBar(kitTabs, part, { part = it }, Modifier.align(Alignment.BottomCenter))
  }
}
