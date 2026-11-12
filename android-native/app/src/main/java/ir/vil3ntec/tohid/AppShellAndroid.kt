package ir.vil3ntec.tohid

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.DeepLinks
import ir.vil3ntec.tohid.core.nav.Move
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Transitions
import ir.vil3ntec.tohid.ui.v3.modes.ModeChooser
import ir.vil3ntec.tohid.ui.v3.modes.ModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 *  پوستهٔ مشترکِ برنامه — آنچه نقطهٔ ورودِ هر سکو پس از بالا آمدن می‌کند، یک بار نوشته (آیفون گامِ ۷).
 *
 *  `MainActivity` ِ اندروید و ریشهٔ آیفون (`ios/shared/…/ios/IosRoot.kt`) هر دو همین را صدا می‌زنند:
 *  پیش‌تر ریشهٔ آیفون رونوشتِ بدنهٔ `setContent`، `CrashNotice`، پیوند، لمسِ اعلان و همگام‌سازیِ `MainActivity`
 *  را داشت و هر تغییرِ این‌جا (تنظیمِ تازه‌ای که پیش از صفحه بار می‌شود، حالتِ تازه) بی‌صدا به آیفون نمی‌رسید.
 *  آنچه فقط مالِ اندروید است در `MainActivity` ماند: چرخهٔ عمرِ Activity، `enableEdgeToEdge`، نامِ مستعارِ پیوند
 *  در مانیفست، سرویسِ اسکنِ پس‌زمینه. ترتیبِ کارها در `MainActivity` همان پیشین است.
 *
 *  نامِ `…Android.kt`: با Context و نام‌های اندروید نوشته شده (مرزِ `platform-deps` ِ tools/quality) و مثلِ
 *  `ServerSwitchAndroid` و `HubRelayAndroid` با shimهای `ios/shared` روی آیفون هم کامپایل می‌شود.
 */
object AppStart {

  /** تنظیم‌هایی که پیش از نخستین کشیدنِ صفحه خوانده می‌شوند (کلیدِ انیمیشن، زبان، روزِ دکان، …) */
  fun loadSettings(context: Context) {
    ir.vil3ntec.tohid.sync.LicenseGuard.warm() // وضعِ سراسری پیش از کارِ پس‌زمینه و نخستین ترکیب
    Motion.load(context)
    LocaleChoice.load(context)
    ShopDayChoice.load(context)
    TillSynonyms.load(context) // POS-04: نسخهٔ ذخیره‌شدهٔ فهرستِ مترادف
    ir.vil3ntec.tohid.data.ExpiryPref.load(context) // S22: روزِ هشدارِ انقضا
    ir.vil3ntec.tohid.data.MultiDrawerPref.load(context) // PAY-17: کشوی برگزیدهٔ همین دستگاه (خاموش ⇒ کشوی اصلی)
    ir.vil3ntec.tohid.ui.theme.ThemeStore.load(context)
    ModeState.load(context)
  }

  /*
   *  ── Sync v1: کارِ پس‌زمینه و سوکتِ زنده ──────────────────────────
   *  `schedule` کارِ دورهٔ پانزده‌دقیقه‌ای را ثبت می‌کند (از قبل ثبت
   *  شده باشد، `KEEP` دست نمی‌زند). سوکت فقط «چیزی عوض شد» را
   *  می‌آورد و خودِ Pull کارِ همان Worker است.
   *
   *  ⚠️ هیچ‌کدام خطا بیرون نمی‌دهند: برنامه باید بی‌اینترنت و بی‌حساب
   *  هم کامل بالا بیاید.
   *
   *  حالتِ پیک (DLV-01): دفترِ دکانی در کار نیست — نه کارِ دوره‌ای، نه سوکت.
   *  `ShopRole.courierMode` را صدازننده پیش از این گذاشته است.
   *  برمی‌گرداند: سوکتِ زنده (بستنش با پایانِ صفحه کارِ صدازننده است).
   */
  fun startSync(context: Context): ir.vil3ntec.tohid.sync.v1.LiveSocket? {
    var live: ir.vil3ntec.tohid.sync.v1.LiveSocket? = null
    if (ir.vil3ntec.tohid.data.ShopRole.courierMode.value) {
      runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(context) }
    } else runCatching {
      ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(context)
      live = ir.vil3ntec.tohid.sync.v1.LiveSocket(context) {
        ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(context)
      }
      live?.connect()
    }
    return live
  }

  /**
   *  پیوندِ بیرونی (`vill3n://app/…`) ⇐ حالتِ همان پیوند و صفحهٔ دقیقش
   *  (`DeepLinks`)؛ پوستهٔ همان حالت پیوند را پس از دروازه‌ها (قفل، ورود) باز
   *  می‌کند. پیوندِ ویترین و QRِ دکان مالِ برنامهٔ خرید است و در مرورگر می‌ماند.
   */
  fun openLink(context: Context, url: String?) {
    val link = DeepLinks.parse(url) ?: return
    ModeState.pendingLink.value = link
    if (link.mode in ModeState.available(context) && ModeState.chosen.value != link.mode) {
      ModeState.choose(context, link.mode)
    }
  }

  /** M6: برنامه با لمسِ اعلانِ سفارش باز شد ⇒ همان سفارش؛ رسید همان دم در `scope` (اگر نت هست)، وگرنه در صف تا دورِ بعدیِ OrdersLive */
  fun openNotice(context: Context, intent: Intent?, scope: CoroutineScope) {
    val orderId = runCatching { ir.vil3ntec.tohid.data.OrderNotify.fromIntent(context, intent) }.getOrNull()
    if (orderId != null) {
      ir.vil3ntec.tohid.ui.OrdersLive.openFromNotice(orderId)
      scope.launch { runCatching { ir.vil3ntec.tohid.data.OrderNotify.flush(context) } }
    }
  }
}

/**
 *  بدنهٔ صفحهٔ برنامه (همان `setContent` ِ `MainActivity`): ظاهر، دروازهٔ ورود، حالتِ دکان/پیک و `CrashNotice`.
 *
 *  @param onShopShown فقط سکویی که اجازهٔ اعلان را هنگامِ نخستین نمایشِ دکان می‌پرسد (آیفون؛ اندروید آن را جای
 *    دیگری می‌پرسد) ⇒ بی آن هیچ.
 */
@Composable
fun AppShell(context: Context, store: ShopStore, onShopShown: (suspend () -> Unit)? = null) {
  // انتخابِ ظاهر بین اجراها می‌ماند
  val prefs = remember { context.getSharedPreferences("tohid", Context.MODE_PRIVATE) }
  var theme by remember {
    mutableStateOf(
      runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }
        .getOrDefault(ThemeChoice.SYSTEM)
    )
  }

  // دروازهٔ ورود فقط بارِ اول می‌آید: یا وارد می‌شوید یا «ادامه بدون
  // حساب» را می‌زنید. از آن به بعد سرِ راه کسی نمی‌ایستد.
  var welcomed by remember { mutableStateOf(prefs.getBoolean("welcomed", false)) }
  //  نقشِ نشست: «پیک» ⇒ حالتِ پیک به‌جای دکان — زنده، از هر راهی که نقش عوض شود
  val courier by ir.vil3ntec.tohid.data.ShopRole.courierMode.collectAsState()
  LaunchedEffect(courier) {
    runCatching {
      if (courier) ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(context)
      else ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(context)   // KEEP: بی اثرِ دوم
    }
  }
  if (onShopShown != null && welcomed && !courier) LaunchedEffect(Unit) { onShopShown() }
  //  LOC-04: جدولِ تعطیلاتِ رسمیِ سرور روزی یک بار (بی‌توکن؛ بی‌شبکه همان نسخهٔ ذخیره‌شده) ⇒ `ShopDayChoice.rules`
  LaunchedEffect(Unit) { ShopDayChoice.keepHolidaysFresh(context) }
  //  POS-04: فهرستِ مرکزیِ مترادفِ جست‌وجوی صندوق، همان روزی یک بار
  LaunchedEffect(Unit) { TillSynonyms.keepFresh(context) }

  //  حالتِ برنامه (دکان / پیک) — انتخابِ کاربر؛ نصبِ پیشین همان رفتارِ امروز
  val chosen = ModeState.chosen.value
  ModeState.onCourierSignedOut = {
    //  خروج از حسابِ پیک ⇒ صفحهٔ ورود؛ دفترِ دکانِ این گوشی دست نخورده است
    prefs.edit().putBoolean("welcomed", false).apply()
    welcomed = false
  }
  ModeState.onStartShop = {
    prefs.edit().putBoolean("welcomed", false).apply()
    welcomed = false
  }
  val mode = remember(chosen, courier, welcomed) { ModeState.current(context) }
  val screen = when {
    !welcomed && chosen == null -> "choose"
    !welcomed -> "welcome"
    else -> mode.id
  }

  TohidTheme(theme) {
    // زبانِ صفحه و جهتش: فارسی و پشتو راست‌به‌چپ (پیش‌فرض، مثلِ پیش)، انگلیسی چپ‌به‌راست
    ir.vil3ntec.tohid.ui.ProvideLang {
      CrashNotice(context)
      //  عوض شدنِ حالت (سوییچرِ «حساب من»، پیوند) با محو و بزرگ‌شدنِ نرم
      AnimatedContent(
        targetState = screen,
        transitionSpec = { V3Transitions.page(Move.NONE, ir.vil3ntec.tohid.ui.directionOf(LocaleChoice.lang)) },
        label = "mode",
      ) { s ->
        when (s) {
          "choose" -> ModeChooser { picked -> ModeState.choose(context, picked) }
          "welcome" -> WelcomeScreen(store) {
            prefs.edit().putBoolean("welcomed", true).apply()
            welcomed = true
          }
          AppMode.COURIER.id -> ir.vil3ntec.tohid.ui.courier.CourierHome(onSignedOut = { ModeState.signedOutOfCourier() })
          else -> AppRoot(
            store = store,
            theme = theme,
            onTheme = {
              theme = it
              prefs.edit().putString("theme", it.name).apply()
            },
          )
        }
      }
    }
  }
}

/**
 *  OPS-03: برنامه دفعهٔ پیش ناگهان بسته شد ⇒ یک بار کدِ گزارش را بگو (برای تماس با پشتیبانی).
 *  نخواندنِ صف یا هر خطای دیگر ⇒ هیچ.
 */
@Composable
private fun CrashNotice(context: Context) {
  var code by remember {
    mutableStateOf(runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.notice(context) }.getOrNull())
  }
  val shown = code ?: return
  val done = {
    runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.markShown(context, shown) }
    code = null
  }
  androidx.compose.material3.AlertDialog(
    onDismissRequest = done,
    title = { androidx.compose.material3.Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashTitle) },
    text = {
      androidx.compose.foundation.layout.Column {
        androidx.compose.material3.Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashBody)
        androidx.compose.foundation.text.selection.SelectionContainer {
          androidx.compose.material3.Text(
            shown,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = androidx.compose.ui.Modifier.padding(top = 8.dp),
          )
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton(onClick = done) {
        androidx.compose.material3.Text(ir.vil3ntec.tohid.core.i18n.StrShell.crashOk)
      }
    },
  )
}
