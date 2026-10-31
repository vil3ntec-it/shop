package ir.vil3ntec.tohid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/*
 *  چرا `FragmentActivity` و نه `ComponentActivity`:
 *
 *  `BiometricPrompt` — که قفلِ برنامه با اثر انگشت را ممکن می‌کند —
 *  اکتیویتی از این نوع می‌خواهد. `FragmentActivity` خودش از
 *  `ComponentActivity` ارث می‌برد، پس `setContent` و بقیهٔ چیزها دست
 *  نخورده‌اند؛ فقط یک پله بالاتر در همان زنجیره.
 */
class MainActivity : androidx.fragment.app.FragmentActivity() {

  private lateinit var store: ShopStore

  /** سوکتِ «چیزی عوض شد» — با بسته شدنِ صفحه بسته می‌شود */
  private var live: ir.vil3ntec.tohid.sync.v1.LiveSocket? = null

  /** برنامهٔ باز (singleTask) با لمسِ اعلانِ سفارش */
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    openNotice(intent)
  }

  private fun openNotice(intent: android.content.Intent?) {
    val orderId = runCatching { ir.vil3ntec.tohid.data.OrderNotify.fromIntent(applicationContext, intent) }.getOrNull()
    if (orderId != null) {
      ir.vil3ntec.tohid.ui.OrdersLive.openFromNotice(orderId)
      //  رسید همان دم (اگر نت هست)؛ وگرنه در صف تا دورِ بعدیِ OrdersLive
      lifecycleScope.launch { runCatching { ir.vil3ntec.tohid.data.OrderNotify.flush(applicationContext) } }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //  OPS-03: پیش از هر کارِ دیگر — خطای گرفته‌نشده گزارشِ بی دادهٔ شخصی با کد می‌شود
    ir.vil3ntec.tohid.data.platform.CrashReporter.install(applicationContext)
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.flush(applicationContext) }
    }
    enableEdgeToEdge()

    store = ShopStore(applicationContext)
    lifecycleScope.launch { store.loadAndSummarize() }

    /*
     *  شستنِ توکن‌هایی که نسخه‌های قبلی روی گوشی جا گذاشته‌اند.
     *
     *  تا دیروز فهرستِ «ورودِ سریع» کنارِ نامِ هر حساب، توکنِ تازه‌سازیِ
     *  همان حساب را هم — رمزنشده — نگه می‌داشت. آن راه بسته شد، ولی
     *  بستنش فقط جلوی نوشتنِ تازه را می‌گیرد؛ آنچه از قبل روی گوشیِ
     *  کاربر نوشته شده تا ابد آنجا می‌ماند مگر کسی پاکش کند.
     *
     *  اینجا، چون پیش از هر صفحه‌ای یک بار اجرا می‌شود. کارش چند
     *  میلی‌ثانیه است و اگر چیزی برای پاک کردن نباشد، هیچ.
     */
    runCatching { ir.vil3ntec.tohid.sync.SavedLogins.purgeTokens(applicationContext) }

    //  M6: برنامه با لمسِ اعلانِ سفارش باز شد ⇒ رسید در صف و همان سفارش
    openNotice(intent)

    // کلیدِ انیمیشن پیش از اولین کشیدنِ صفحه خوانده می‌شود
    Motion.load(applicationContext)
    LocaleChoice.load(applicationContext)
    ShopDayChoice.load(applicationContext)

    /*
     *  ── Sync v1: کارِ پس‌زمینه و سوکتِ زنده ──────────────────────────
     *  `schedule` کارِ دورهٔ پانزده‌دقیقه‌ای را ثبت می‌کند (از قبل ثبت
     *  شده باشد، `KEEP` دست نمی‌زند). سوکت فقط «چیزی عوض شد» را
     *  می‌آورد و خودِ Pull کارِ همان Worker است.
     *
     *  ⚠️ هیچ‌کدام خطا بیرون نمی‌دهند: برنامه باید بی‌اینترنت و بی‌حساب
     *  هم کامل بالا بیاید.
     */
    //  حالتِ پیک (DLV-01): دفترِ دکانی در کار نیست — نه کارِ دوره‌ای، نه سوکت
    ir.vil3ntec.tohid.data.ShopRole.courierMode.value = ir.vil3ntec.tohid.data.ShopRole.isCourier(applicationContext)
    if (ir.vil3ntec.tohid.data.ShopRole.courierMode.value) {
      runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(applicationContext) }
    } else runCatching {
      ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(applicationContext)
      live = ir.vil3ntec.tohid.sync.v1.LiveSocket(applicationContext) {
        ir.vil3ntec.tohid.sync.v1.SyncV1Worker.now(applicationContext)
      }
      live?.connect()
    }

    setContent {
      // انتخابِ ظاهر بین اجراها می‌ماند
      val prefs = remember { getSharedPreferences("tohid", MODE_PRIVATE) }
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
          if (courier) ir.vil3ntec.tohid.sync.v1.SyncV1Worker.cancel(applicationContext)
          else ir.vil3ntec.tohid.sync.v1.SyncV1Worker.schedule(applicationContext)   // KEEP: بی اثرِ دوم
        }
      }

      TohidTheme(theme) {
        // زبانِ صفحه و جهتش: فارسی و پشتو راست‌به‌چپ (پیش‌فرض، مثلِ پیش)، انگلیسی چپ‌به‌راست
        ir.vil3ntec.tohid.ui.ProvideLang {
          CrashNotice()
          if (!welcomed) {
            WelcomeScreen(store) {
              prefs.edit().putBoolean("welcomed", true).apply()
              welcomed = true
            }
          } else if (courier) {
            ir.vil3ntec.tohid.ui.courier.CourierHome(onSignedOut = {
              //  خروج از حسابِ پیک ⇒ صفحهٔ ورود؛ دفترِ دکانِ این گوشی دست نخورده است
              prefs.edit().putBoolean("welcomed", false).apply()
              welcomed = false
            })
          } else {
            AppRoot(
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
  private fun CrashNotice() {
    var code by remember {
      mutableStateOf(runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.notice(applicationContext) }.getOrNull())
    }
    val shown = code ?: return
    val done = {
      runCatching { ir.vil3ntec.tohid.data.platform.CrashReporter.markShown(applicationContext, shown) }
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

  override fun onDestroy() {
    runCatching { live?.disconnect() }
    super.onDestroy()
  }

  /*
   *  چتِ دکان (v3 §۳۳، CHAT-04): «خوانده شد» فقط وقتی برنامه واقعاً جلوی
   *  چشم است. برنامه در پس‌زمینه پیام را می‌گیرد («رسید») ولی «خواند» نه.
   */
  override fun onResume() {
    super.onResume()
    ir.vil3ntec.tohid.data.ShopChatSync.foreground = true
  }

  override fun onPause() {
    ir.vil3ntec.tohid.data.ShopChatSync.foreground = false
    super.onPause()
  }
}
