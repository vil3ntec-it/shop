package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  هشدارهای زنگِ دکان (اشتراکِ رو به پایان، خبرِ اعضا، کالای تمام/کم، بدهیِ تأمین‌کننده، پشتیبانِ کهنه، بدهکاران)
 *  و مرزِ «رو به پایان» ِ اشتراک. پیش‌تر کنارِ سربرگِ آبیِ قدیم (`TohidTopBar`، پاک‌شده در «پاک‌سازی، گامِ ۳») بود؛
 *  نسخهٔ ۳ (`ProfileScreen`، `PlansScreen`) و صفحه‌های امروز همین را می‌خوانند.
 */
/** یک هشدارِ زنگ */
data class Alert(
  val value: String,
  val title: String,
  val detail: String,
  val tint: Color,
  /** صفحه‌ای که این هشدار به آن مربوط است — با زدن روی هشدار همان‌جا باز می‌شود */
  val target: String,
)

/**
 *  هشدارِ اشتراک — فقط وقتی کاری از دستِ کاربر برمی‌آید.
 *
 *  اشتراکی که هفته‌ها اعتبار دارد خبری ندارد؛ شلوغ کردنِ زنگ با چیزی
 *  که کاری نمی‌شود کرد، باعث می‌شود کاربر دیگر زنگ را باز نکند.
 */
private fun subscriptionAlert(
  status: ir.vil3ntec.tohid.sync.License.Status?,
  danger: Color,
  warning: Color,
): Alert? {
  if (status == null) return null
  return when (status.state) {
    ir.vil3ntec.tohid.sync.License.State.EXPIRED,
    ir.vil3ntec.tohid.sync.License.State.GRACE ->
      Alert(StrNav.subscription, StrNav.subscriptionEnded, StrNav.renewToReopen, danger, "vip")

    ir.vil3ntec.tohid.sync.License.State.ACTIVE -> {
      val left = status.daysLeft()
      if (left in 0..SUBSCRIPTION_WARN_DAYS) {
        Alert(
          if (left <= 0) Str.today else Str.days(left.fa()),
          StrNav.subscriptionEnding,
          StrNav.renewBeforeEnd,
          if (left <= 3) danger else warning,
          "vip",
        )
      } else null
    }

    //  NONE یعنی هنوز اشتراکی نبوده و INVALID یعنی مجوز خراب است؛
    //  هیچ‌کدام «رو به پایان» نیستند و جایشان اینجا نیست
    else -> null
  }
}

/**
 *  مرزِ «رو به پایان» — یک هفته.
 *
 *  یک جا نوشته شده و سه جا خوانده می‌شود: هشدارِ زنگ، رنگِ نشانِ سربرگ،
 *  و کارتِ وضعیت در صفحهٔ اشتراک. اگر هرکدام عددِ خودش را داشت، کاربر
 *  نشانِ قرمز می‌دید و در صفحهٔ اشتراک «همه‌چیز مرتب است».
 */
const val SUBSCRIPTION_WARN_DAYS = 7

/** همان هشدارهایی که نسخهٔ وب در زنگ نشان می‌دهد */
@Composable
fun rememberAlerts(d: ShopData): List<Alert> {
  val context = LocalContext.current
  val backupStale = remember { BackupClock.isStale(context) }

  /*
   *  اشتراک، پیش از آنکه تمام شود.
   *
   *  تا امروز هیچ خبری نبود: فروشنده یک روز صبح می‌آمد و صندوقِ فروشش
   *  قفل بود. برای کسی که دکانش با همین برنامه می‌چرخد، این یعنی یک
   *  روزِ کاری از دست رفته و یک تماسِ عصبانی — نه یک ناراحتیِ کوچک.
   *
   *  یک بار حساب می‌شود، نه با هر بار کشیده شدنِ صفحه: سنجیدنِ مجوز
   *  یعنی بررسیِ امضای رمزنگاری.
   */
  /*
   *  خبرهای دکان — آنچه بقیهٔ اعضا کرده‌اند.
   *
   *  هشدارهای زیر از دفترِ **محلی** حساب می‌شوند و فقط همین گوشی را
   *  می‌بینند. اینها از سرور می‌آیند: فروشی که کریم زد، کالایی که
   *  دستِ او تمام شد. برای صاحب دکانی که خانه است، همین‌ها مهم‌اند.
   */
  var news by remember { mutableStateOf<List<ir.vil3ntec.tohid.data.repo.EventsRepository.Event>>(emptyList()) }
  LaunchedEffect(Unit) {
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return@LaunchedEffect
    //  وضعیتِ اشتراک از زبانِ سرور — خودش می‌داند تازه هست یا نه و
    //  اگر تازه بود، از سرور نمی‌پرسد
    SubscriptionPulse.refresh(context)
    ir.vil3ntec.tohid.data.repo.Backend.events(context).feed()
      .onSuccess { feed ->
        //  خبرِ خودم برای خودم خبر نیست
        val me = ir.vil3ntec.tohid.sync.SyncStore(context).accountId
        news = feed.events.filter { it.userId != me }.take(12)
      }
    //  شکست بی‌صداست: نبودنِ اینترنت نباید زنگ را خالی نشان دهد
  }

  val subscription = remember {
    runCatching {
      ir.vil3ntec.tohid.sync.LicenseGuard.status(
        context, ir.vil3ntec.tohid.sync.SyncStore(context),
      )
    }.getOrNull()
  }
  // رنگ‌ها از CompositionLocal می‌آیند و خواندنشان فقط داخل بدنهٔ کامپوزبل
  // مجاز است، نه داخل لامبدای remember — پس همین‌جا گرفته می‌شوند.
  val danger = Shop.colors.danger
  val warning = Shop.colors.warning
  val accent = Shop.colors.accent
  return remember(d, backupStale, danger, warning, accent, subscription, news, ir.vil3ntec.tohid.LocaleChoice.lang) {
    buildList {
      //  اول از همه، چون از هر موجودیِ کمی مهم‌تر است
      subscriptionAlert(subscription, danger, warning)?.let { add(it) }

      //  بعد خبرهای بقیهٔ اعضا — تازه‌ترین بالا
      news.forEach { event ->
        add(
          Alert(
            value = event.userName.ifBlank { Str.shop },
            title = event.title,
            detail = event.body.ifBlank { sinceText(event.at) },
            tint = if (event.kind == "stock_out") danger else accent,
            target = if (event.kind == "stock_out") "products" else "sales",
          )
        )
      }
      d.products.filter { ShopStore.stockStatus(d, it) == "out" }.forEach {
        add(Alert(StrNav.soldOut, it.name, StrNav.notInStock, danger, "products"))
      }
      d.products.filter { ShopStore.stockStatus(d, it) == "low" }.forEach {
        add(Alert(Str.stockLow, it.name, StrNav.leftQty(qty(ShopStore.stock(d, it.id))), warning, "products"))
      }
      val supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }
      if (supplierDebt > 0) {
        add(Alert(StrNav.supplierDebt, "${money(supplierDebt)} ؋", StrNav.unpaid, warning, "purchasing"))
      }
      if (backupStale) {
        add(Alert(StrNav.backup, StrNav.takeBackup, BackupClock.text(context), warning, "settings"))
      }
      d.debtors
        .map { it to ShopStore.debt(d, it.id) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(3)
        .forEach { (debtor, amount) ->
          add(Alert(Str.debtor, debtor.name, "${money(amount)} ؋", danger, "debtors"))
        }
    }
  }
}

/** «۳ دقیقه پیش» — نه ساعتِ خام */
private fun sinceText(at: Long): String {
  val minutes = ((System.currentTimeMillis() - at) / 60000L).coerceAtLeast(0)
  return when {
    minutes < 1 -> StrNav.justNow
    minutes < 60 -> StrNav.minutesAgo(minutes.fa())
    minutes < 24 * 60 -> StrNav.hoursAgo((minutes / 60).fa())
    else -> StrNav.daysAgo((minutes / (24 * 60)).fa())
  }
}
