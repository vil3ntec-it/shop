package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.v3.V3Hue

/**
 *  یک «سیستم» یا بخشِ برنامه — قراردادِ ماژول (`docs/MODULES-fa.md`).
 *
 *  همه‌چیزِ یک بخش یک‌جا: مسیرها و پیوندهایش، ورودی‌هایش در «بیشتر»، کاشیِ خانه و
 *  فهرستِ نوارِ پایین، صفحه‌های تنظیماتش، نیازها (نقش/پرچمِ سرور)، پرچمِ ماژول
 *  (روشن/خاموشِ کلِ بخش) و حالت‌هایی که در آن‌ها هست (دکان/پیک).
 *
 *  بخشِ تازه = یک پوشهٔ `ui/v3/<بخش>/` با `<Bخش>Routes.kt` و **یک خط** در
 *  `V3Modules.ALL`؛ پوسته، «بیشتر»، تنظیمات و نوار از همین فهرست می‌خوانند.
 */
class AppModule(
  val id: String,
  val title: () -> String,
  val icon: ImageVector,
  val modes: Set<AppMode> = setOf(AppMode.SHOP),
  val routes: List<V3Route>,
  val entries: List<ModuleEntry> = emptyList(),
  val settings: List<SettingsEntry> = emptyList(),
  val needs: Set<Need> = emptySet(),
  /** پرچمِ ماژول (`ModuleFlags`)؛ خاموش ⇒ هیچ مسیر و ورودیِ این بخش دیده نمی‌شود */
  val flag: String? = null,
)

/**
 *  یک ورودیِ بخش: ردیفِ «بیشتر» (در `group`)، کاشیِ خانه، یا موردِ نوارِ پایین.
 *  `id` برای نوار ماندگار است (در تنظیماتِ کاربر ذخیره می‌شود) — عوضش نکنید.
 *  `badge` شمارِ کوچکِ کنارِ ردیف («۳ تازه»).
 */
class ModuleEntry(
  val id: String,
  val label: () -> String,
  val route: String,
  val icon: ImageVector,
  val places: Set<Place>,
  val group: MoreGroup? = null,
  val hue: V3Hue? = null,
  val needs: Set<Need> = emptySet(),
  val order: Int = 0,
  val badge: (() -> String?)? = null,
  /** آیکنِ حالتِ فعال در نوار (پُر)؛ نبود ⇒ همان `icon` */
  val iconSelected: ImageVector? = null,
)

/**
 *  صفحه‌ای که بخش در «تنظیمات» می‌گذارد: ردیفی زیرِ گروهِ `group` (نامِ گروهِ
 *  تنظیمات، مثلِ `SettingsLayout.SALES`) که مسیرِ `route` (معمولاً
 *  `settings/<گروه>/<صفحه>`) را باز می‌کند.
 */
class SettingsEntry(
  val label: () -> String,
  val route: String,
  val icon: ImageVector,
  val group: String,
  val needs: Set<Need> = emptySet(),
)
