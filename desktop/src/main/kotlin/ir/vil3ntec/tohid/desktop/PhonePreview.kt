package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingBag
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartShot
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.screens.QuickSaleScreen
import ir.vil3ntec.tohid.ui.components.common.NavTab
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import androidx.compose.ui.Alignment
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.ui.screens.DebtorDetailShot
import ir.vil3ntec.tohid.ui.screens.DebtorsScreen
import ir.vil3ntec.tohid.ui.screens.PhoneDebtorsShot
import ir.vil3ntec.tohid.ui.screens.ProfileScreen
import ir.vil3ntec.tohid.ui.screens.ProfileModel
import ir.vil3ntec.tohid.ui.screens.ProfileShot
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsNav
import ir.vil3ntec.tohid.ui.screens.SettingsShot
import ir.vil3ntec.tohid.ui.screens.MoreShot
import ir.vil3ntec.tohid.ui.screens.PhoneMoreScreen
import ir.vil3ntec.tohid.ui.screens.SettingsScreen
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme

/**
 *  پیش‌نمایشِ گوشی برای عکس‌های خودکار — فقط با فرمانِ `phone:<صفحه>`ِ `DevShot`.
 *
 *  صفحه‌های گوشی (نوارِ پایینِ قرصی، …) روی کامپیوتر هرگز دیده نمی‌شوند، چون
 *  کامپیوتر منوی کناری و چیدمانِ پهن دارد. این پنجرهٔ بی‌قابِ ۳۹۰×۸۴۴ همان
 *  کامپوننت‌های گوشی را با `LocalConfiguration`ِ گوشی (`Screen.PHONE`) و دفترِ
 *  نمایشی می‌کشد تا `desktop-shots.yml` از آن عکس بگیرد. در نسخهٔ دستِ کاربر
 *  هیچ‌کس `screen` را نمی‌گذارد و پنجره ساخته نمی‌شود.
 *
 *  پسوندِ `-dark` همان صفحه را در تمِ شب می‌کشد (`phone:nav-dark`).
 */
object PhonePreview {
  const val TITLE = "پیش‌نمایشِ گوشی"
  val screen = mutableStateOf<String?>(null)
}

private val PREVIEW_TABS = listOf(
  NavTab("home", "خانه", Icons.Filled.GridView),
  NavTab("sale", "فروش", Icons.Filled.PointOfSale),
  NavTab("debtors", "قرض‌داران", Icons.Filled.Groups),
  NavTab("products", "محصولات", Icons.Filled.ShoppingBag),
  NavTab("more", "بیشتر", Icons.Filled.MoreHoriz),
)

@Composable
fun PhonePreviewWindow(store: ShopStore, context: android.content.Context) {
  val requested = PhonePreview.screen.value ?: return
  //  پسوندها: `-dark` تمِ شب، `-en` انگلیسی (چپ‌به‌راست) — به همین ترتیب
  val en = requested.endsWith("-en")
  val dark = requested.removeSuffix("-en").endsWith("-dark")
  val screen = requested.removeSuffix("-en").removeSuffix("-dark")
  DisposableEffect(en) {
    //  زبانِ برنامه (`LocaleChoice`) پس از بستنِ پیش‌نمایش برمی‌گردد
    val before = ir.vil3ntec.tohid.core.i18n.I18n.source
    ir.vil3ntec.tohid.core.i18n.I18n.source = { if (en) ir.vil3ntec.tohid.core.i18n.Lang.EN else ir.vil3ntec.tohid.core.i18n.Lang.FA }
    onDispose { ir.vil3ntec.tohid.core.i18n.I18n.source = before }
  }
  DialogWindow(
    onCloseRequest = { PhonePreview.screen.value = null },
    title = PhonePreview.TITLE,
    undecorated = true,
    resizable = false,
    state = rememberDialogState(position = WindowPosition(0.dp, 0.dp), size = DpSize(390.dp, 844.dp)),
  ) {
    CompositionLocalProvider(
      LocalContext provides context,
      LocalView provides remember { android.view.View(context) },
      LocalConfiguration provides Configuration(390, 844),
    ) {
      TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
        CompositionLocalProvider(LocalLayoutDirection provides if (en) LayoutDirection.Ltr else LayoutDirection.Rtl) {
          key(requested) { PhonePreviewContent(screen, store, dark) }
        }
      }
    }
  }
}

@Composable
private fun PhonePreviewContent(screen: String, store: ShopStore, dark: Boolean) {
  val data by store.data.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  Column(Modifier.fillMaxSize().background(Shop.colors.bg)) {
    //  عکس‌های گروه‌ها (`shots/<Group>Shots.kt`): g:<گروه>:<حالت>
    if (screen.startsWith("g:")) {
      val (group, name) = screen.removePrefix("g:").split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
      val shot = ir.vil3ntec.tohid.desktop.shots.ShotRegistry.find(group, name)
      Box(Modifier.weight(1f).fillMaxWidth()) {
        if (shot != null) {
          DisposableEffect(screen) {
            shot.prepare()
            onDispose { shot.cleanup() }
          }
          val route = shot.route
          if (route != null) ir.vil3ntec.tohid.desktop.shots.V3RoutePreview(route, store, dark)
          else shot.content?.invoke(store)
        }
      }
      return@Column
    }
    when (screen) {
      //  تنظیماتِ گوشی: گروه‌ها و کارت‌های گرد (`SettingsGroup`)
      "settings" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        SettingsScreen(store, data, snackbar, if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, {}, onUpdates = {})
      }
      /*
       *  گوشی نسخهٔ ۳ — «حساب من» (r4)، «تنظیمات» (r5) و «بیشتر»: حسابِ نمایشی برای
       *  «واردشده» (`ProfileShot`)، و بازکردنِ تنظیمات روی یک گروه یا یک بخش
       *  (`SettingsNav`) — همان راهی که «بیشتر ⇐ تنظیمات» می‌رود.
       */
      "v3-profile", "v3-profile-out" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        val signedIn = screen == "v3-profile"
        DisposableEffect(screen) {
          ProfileShot.account.value =
            if (signedIn) ProfileModel.Account(true, "احمد ولی‌زاده", "ahmad@example.af", "0700123456", "owner")
            else ProfileModel.Account(signedIn = false, name = "")
          ProfileShot.plan.value =
            if (signedIn) ProfileModel.Plan(active = true, expired = false, trial = false, days = 12, urgent = false) else null
          onDispose { ProfileShot.account.value = null; ProfileShot.plan.value = null }
        }
        //  مثلِ `AppRoot`ِ گوشی: صفحه تا ته و نوارِ شناورِ r10 رویش، با دایرهٔ «حساب» فعال
        key(screen) {
          ProfileScreen(store = store, snackbar = snackbar, onBack = {}, onSignIn = {}, onSubscription = {}, bottomReserve = PhoneNav.RESERVE)
        }
        PhoneNavBar(NavLayouts.DEFAULT, "profile", onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
      }
      "v3-settings", "v3-settings-scrolled", "v3-settings-detail" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        //  کلیدِ سبزِ «قفلِ برنامه» روشن دیده شود (r5 Face ID) — رمزِ واقعی دست نمی‌خورد
        //  پیش از کشیدنِ صفحه (در همین گذر)، نه در اثرِ پس از آن
        remember { SettingsShot.lockOn = true }
        DisposableEffect(Unit) { onDispose { SettingsShot.lockOn = null } }
        LaunchedEffect(screen) {
          SettingsNav.pending.value = when (screen) {
            "v3-settings-scrolled" -> SettingsNav.Request(group = SettingsLayout.DATA)
            "v3-settings-detail" -> SettingsNav.Request(detail = "ظاهر")
            else -> SettingsNav.Request(group = SettingsLayout.GENERAL)
          }
        }
        key(screen) {
          SettingsScreen(store, data, snackbar, if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, {}, onBack = {}, onUpdates = {})
        }
      }
      "v3-more", "v3-more-bottom" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        DisposableEffect(screen) {
          MoreShot.scrollToEnd.value = screen == "v3-more-bottom"
          onDispose { MoreShot.scrollToEnd.value = false }
        }
        //  همان پوستهٔ برنامه: «بیشتر» بی `bottomReserve`، پوسته جای نوار را پایینِ صفحه نگه می‌دارد
        key(screen) { Box(Modifier.fillMaxSize().padding(bottom = PhoneNav.RESERVE)) { PhoneMoreScreen(data) {} } }
        PhoneNavBar(NavLayouts.DEFAULT, "more", onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
      }
      //  «حساب من»: کارتِ عکس، نقش، آمار و گروه‌ها
      "profile" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        ProfileScreen(store = store, snackbar = snackbar, onBack = {}, onSignIn = {}, onSubscription = {})
      }
      //  سبدِ گوشی: نوارِ تیره (cart) و برگهٔ باز (cart-open) روی «انتخاب محصول» با چهار کالای دفترِ نمایشی
      "cart", "cart-open" -> {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val context = LocalContext.current
        val cartStore = remember { CartStore(context) }
        LaunchedEffect(data.products.size, screen) {
          val picks = data.products.filter { it.active }.take(4)
          if (picks.isNotEmpty()) cartStore.set(picks.mapIndexed { i, p -> SalesEngine.CartLine(p.id, if (i == 0) 2.0 else 1.0) })
          PhoneCartShot.open.value = screen == "cart-open"
        }
        QuickSaleScreen(store, cartStore, data, snackbar) {}
        }
        //  نسخهٔ ۳: صندوقِ گوشی تمام‌صفحه است و نوارِ پایین رویش نمی‌آید (r3 — نوارِ سبد جایش است)
        if (!PhoneCartUi.saleActive.value && !PhoneCartUi.sheetOpen.value) PhoneNavBar(tabs = PREVIEW_TABS, current = "sale", onPick = {})
      }
      //  حالتِ پیک (phone-v3-courier-*): ورود، خانه، مسیر، نقدِ من، پیگیری، ارسال، آمار، حساب، تأییدِ نقد — دادهٔ نمایشی
      "courier-join", "courier-home", "courier-route", "courier-cash", "courier-track", "courier-ship",
      "courier-stats", "courier-account", "courier-cod" ->
        Box(Modifier.weight(1f).fillMaxWidth()) { key(screen) { CourierPreview(screen.removePrefix("courier-")) } }
      //  قرض‌دارانِ گوشی (r9): سه نفرِ r9، هشت نفر با نام و مبلغِ بلند، و خالی — با دفترِ ساختگیِ خودش
      "debtors-r9", "debtors-many", "debtors-empty" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        val demo = remember(screen) { debtorsDemo(screen, System.currentTimeMillis()) }
        //  میکروفونِ کادرِ جستجو همان‌طور که اندروید نشان می‌دهد (کامپیوتر خودش پنهانش می‌کند)
        LaunchedEffect(Unit) { PhoneDebtorsShot.mic.value = true }
        //  پوستهٔ v3: قرض‌داران از «بیشتر» باز می‌شود ⇒ سربرگِ خودِ صفحه با برگشت
        LaunchedEffect(Unit) { DebtorDetailShot.open.value = null }
        DebtorsScreen(store, demo, snackbar, showHeader = true, onBack = {})
      }
      //  حسابِ یک قرض‌دار (owner-feedback-2): کشوی بسته، کشوی باز، برگهٔ «ثبتِ تراکنش»، بی تراکنش
      "debtor-detail", "debtor-detail-drawer", "debtor-detail-composer", "debtor-detail-empty",
      "debtor-detail-long", "debtor-detail-tx" -> Box(Modifier.weight(1f).fillMaxWidth()) {
        val demo = remember(screen) { debtorDetailDemo(screen) }
        LaunchedEffect(screen) {
          DebtorDetailShot.drawer.value = screen == "debtor-detail-drawer"
          DebtorDetailShot.composer.value = screen == "debtor-detail-composer"
          DebtorDetailShot.txSheet.value = if (screen == "debtor-detail-tx") "dd-invoice" else null
          DebtorDetailShot.open.value = "dd-h"
        }
        DebtorsScreen(store, demo, snackbar, showHeader = true, onBack = {})
      }
      //  گوشی نسخهٔ ۳، صندوق (r3 بسته / r2 باز): v3cart-closed|open|empty|long|long-open|scan
      in PHONE_V3_CART -> Box(Modifier.weight(1f).fillMaxWidth()) { PhoneV3CartPreview(screen, store, data, snackbar) }
      //  phone-v3-home: خانهٔ r7/r6/r8، نوارِ r10 و ویرایشگرش (`PhoneV3HomePreview`)
      //  «nav» (عکسِ 20-phone-nav) هم همان خانه با نوارِ r10 روی آن، مثلِ `AppRoot`
      else -> if (screen.startsWith("v3home") || screen == "nav") Box(Modifier.weight(1f).fillMaxWidth()) {
        PhoneV3HomePreview(if (screen == "nav") "v3home" else screen, store)
      } else {
        //  «nav» و هر نامِ ناشناس: خانه با نوارِ پایینِ قرصی
        //  همان مسیرِ `home` (امروز داشبورد، فردا خانهٔ G10) — نه صدا زدنِ مستقیمِ صفحه
        Box(Modifier.weight(1f).fillMaxWidth()) { ir.vil3ntec.tohid.desktop.shots.V3RoutePreview("home", store, dark) }
        PhoneNavBar(tabs = PREVIEW_TABS, current = "home", onPick = {})
      }
    }
  }
}

/**
 *  دفترِ ساختگیِ عکس‌های حسابِ یک قرض‌دار — همان «haroon» ِ عکسِ صاحب محصول با
 *  شماره، و چند ردیف در چند روز (برد با یادداشت، برد با سررسید، رسید، بردِ امروز)؛
 *  «empty» همان نفر بی هیچ تراکنش.
 */
private fun debtorDetailDemo(screen: String): ShopData {
  val now = System.currentTimeMillis()
  val dayMs = 86_400_000L
  val today = java.time.LocalDate.now()
  var seq = 0
  fun tx(type: String, amount: Double, daysAgo: Long, notes: String = "", due: String? = null, id: String? = null) = DebtTransaction(
    id = id ?: "dd-t${seq++}", debtorId = "dd-h", type = type, amount = amount,
    date = today.minusDays(daysAgo).toString(), notes = notes, createdAt = now - daysAgo * dayMs - seq++,
    dueDate = due,
  )
  val debtor = Debtor(id = "dd-h", name = "haroon", phone = "0792236008", notes = "مشتریِ همیشگی — پنجشنبه‌ها حساب می‌کند")
  //  بردِ فاکتوردار: «فاکتور #12» در شرحِ ردیف ⇐ همان فاکتور و اقلامش (`DebtBook`)
  val sale = Sale(
    id = "dd-sale", invoiceNumber = 12, debtorId = "dd-h", total = 1_200.0, finalTotal = 1_200.0,
    paymentMethod = "credit", remaining = 1_200.0, debtGiven = 1_200.0, date = today.minusDays(3).toString(), createdAt = now - 3 * dayMs,
  )
  val items = listOf(
    SaleItem(id = "dd-i1", saleId = "dd-sale", name = "آرد ۲۵ کیلویی", quantity = 2.0, unitPrice = 350.0, totalPrice = 700.0),
    SaleItem(id = "dd-i2", saleId = "dd-sale", name = "روغن ۵ لیتری", quantity = 1.0, unitPrice = 300.0, totalPrice = 300.0),
    SaleItem(id = "dd-i3", saleId = "dd-sale", name = "شکر", quantity = 2.0, unitPrice = 100.0, totalPrice = 200.0),
  )
  val base = listOf(
    tx("give", 600.0, 6, "آرد و روغن"),
    tx("give", 1_200.0, 3, "فروشِ نسیه — فاکتور #12", due = today.plusDays(10).toString(), id = "dd-invoice"),
    tx("receive", 500.0, 1, "نقد"),
    tx("give", 250.0, 0),
  )
  val rows = when (screen) {
    "debtor-detail-empty" -> emptyList()
    //  فهرستِ بلند: پانزده ردیف در بیست روز — برای دیدنِ تراکمِ ردیف‌ها
    "debtor-detail-long" -> base + (1..11).map { i ->
      val give = i % 3 != 0
      tx(if (give) "give" else "receive", (i * 137 % 900 + 100).toDouble(), (6 + i).toLong())
    }
    else -> base
  }
  return ShopData(debtors = listOf(debtor), transactions = rows, sales = if (rows.isEmpty()) emptyList() else listOf(sale), saleItems = items)
}

/**
 *  دفترِ ساختگیِ عکس‌های قرض‌داران — فقط قرض‌دار و تراکنش، بی‌ربط به دفترِ نمایشی.
 *
 *  • r9: همان سه نفرِ r9 — «جلیل» ۱٬۰۰۰ بی شماره، «haroon» ۶۰۰ با شماره (هر دو
 *    شش روز)، و «haroon» ِ صاف با رسیدِ ۱۰٬۰۰۰.
 *  • many: هشت نفر با نامِ بلند، مبلغِ درشت، قرضِ کهنه، موجودی‌دار و صاف.
 *  • empty: هیچ.
 */
private fun debtorsDemo(screen: String, now: Long): ShopData {
  val day = 86_400_000L
  var seq = 0
  fun tx(debtorId: String, type: String, amount: Double, daysAgo: Long) =
    DebtTransaction(id = "demo-t${seq++}", debtorId = debtorId, type = type, amount = amount, createdAt = now - daysAgo * day)
  return when (screen) {
    "debtors-r9" -> ShopData(
      debtors = listOf(
        Debtor(id = "r9-j", name = "جلیل"),
        Debtor(id = "r9-h6", name = "haroon", phone = "0792236008"),
        Debtor(id = "r9-h0", name = "haroon"),
      ),
      transactions = listOf(
        tx("r9-j", "give", 1000.0, 6),
        tx("r9-h6", "give", 600.0, 6),
        tx("r9-h0", "give", 10000.0, 9),
        tx("r9-h0", "receive", 10000.0, 2),
      ),
    )
    "debtors-many" -> ShopData(
      debtors = listOf(
        Debtor(id = "m1", name = "محمد عبدالرحیم صدیقی"),
        Debtor(id = "m2", name = "عبدالرحمن نورزاد", phone = "+93700123456"),
        Debtor(id = "m3", name = "Mohammad Haroon Rahimi"),
        Debtor(id = "m4", name = "شرکت ترانسپورتی برادران حیدری", phone = "0799111222"),
        Debtor(id = "m5", name = "زرغونه"),
        Debtor(id = "m6", name = "گل‌احمد"),
        Debtor(id = "m7", name = "نجیب‌الله", phone = "0788000111"),
        Debtor(id = "m8", name = "فریدون"),
      ),
      transactions = listOf(
        tx("m1", "give", 12_345_678.0, 12),
        tx("m2", "give", 4_500_000.0, 3),
        tx("m3", "give", 987_654.0, 64),
        tx("m4", "give", 250_000.0, 45),
        tx("m5", "give", 8_000.0, 20),
        tx("m5", "receive", 8_000.0, 5),
        tx("m6", "receive", 1_500.0, 2),
        tx("m7", "give", 75.0, 0),
        tx("m8", "give", 3_000_000.0, 400),
        tx("m8", "receive", 120_000.0, 30),
      ),
    )
    else -> ShopData()
  }
}
