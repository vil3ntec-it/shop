package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.V3Toasts

/**
 *  آنچه پوسته به هر صفحه می‌دهد — یک بار در `AppRoot` ساخته می‌شود.
 *  `bottomReserve` جای نوارِ شناورِ پایین است وقتی همین صفحه زیرِ آن است.
 */
@Stable
class ShellContext(
  val store: ShopStore,
  val data: ShopData,
  val cart: CartStore,
  val snackbar: SnackbarHostState,
  val toasts: V3Toasts,
  val theme: ThemeChoice,
  val onTheme: (ThemeChoice) -> Unit,
  val bottomReserve: Dp,
  val wide: Boolean,
  val access: Access,
  /** شناسه‌های نوارِ پایین که این کاربر حق دارد (`NavCatalog.allowed`) */
  val navAllowed: Set<String>,
  /** پس از ورود: دکانِ حساب دوباره پرسیده و دفتر همان لحظه همگام می‌شود */
  val afterSignIn: () -> Unit = {},
)

/**
 *  حوزهٔ محتوای یک مسیر: پارامترها (`args["id"]`، `args.query("filter")`)،
 *  ناوبری (`nav.open(...)`، `nav.back()`)، و هرچه پوسته دارد (`store`، `data`،
 *  `cart`، `snackbar`/`toasts`، `theme`). صفحه‌ها فقط از همین می‌خوانند — هیچ
 *  `var openX`ِ سراسری نیست.
 */
@Stable
class RouteScope(val args: RouteArgs, val nav: V3Nav, val shell: ShellContext) {
  val store: ShopStore get() = shell.store
  val data: ShopData get() = shell.data
  val cart: CartStore get() = shell.cart
  val snackbar: SnackbarHostState get() = shell.snackbar
  val toasts: V3Toasts get() = shell.toasts
  val theme: ThemeChoice get() = shell.theme
  val onTheme: (ThemeChoice) -> Unit get() = shell.onTheme
  val bottomReserve: Dp get() = shell.bottomReserve
  val wide: Boolean get() = shell.wide
  val access: Access get() = shell.access

  /**
   *  برگشت از **همین** مسیر. اگر مسیر دیگر بالای پشته نیست (مثلاً `nav.finish` آن را
   *  برداشته و برگه پس از آن `onDismiss` هم صدا زده)، کاری نمی‌کند — وگرنه صفحهٔ
   *  صدازننده هم بسته می‌شد.
   */
  fun back() { nav.backFrom(args.path) }
}
