package ir.vil3ntec.tohid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.money
import java.util.concurrent.TimeUnit

/**
 *  نگهبانِ دکان — خبرهایی که نباید منتظرِ باز شدنِ برنامه بمانند.
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  `Reminders` یک خلاصهٔ روزانه‌ی ساعت نُه می‌داد. برای «امروز چه خبر»
 *  خوب است، ولی سه چیز هست که فردا صبح دانستنشان دیر است:
 *
 *    • کالایی که **تمام شده** — تا فردا صبح، مشتری آمده و دست خالی رفته
 *    • **اشتراکی که تمام می‌شود** — صبح که بفهمی، صندوق قفل است
 *    • **قرضی که از حد گذشته** — هرچه دیرتر بفهمی، سخت‌تر وصول می‌شود
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ## چرا پیامک نه
 *
 *  سرویسِ پیامک برای کدِ شش‌رقمیِ ورود است و بس. این خبرها هر روز چند
 *  بار پیش می‌آیند و پیامک کردنشان هم پول می‌سوزاند و هم آدم را از
 *  پیامک بیزار می‌کند. اینها از خودِ برنامه می‌آیند.
 *
 *  ## چطور با برنامهٔ بسته کار می‌کند
 *
 *  `WorkManager` کارِ دوره‌ای را حتی وقتی برنامه بسته است اجرا می‌کند —
 *  همان چیزی که `Reminders` هم از آن استفاده می‌کند. پس اعلان می‌آید
 *  بی‌آنکه کسی برنامه را باز کرده باشد.
 *
 *  **صادقانه:** کوتاه‌ترین فاصله‌ای که اندروید برای کارِ دوره‌ای اجازه
 *  می‌دهد **پانزده دقیقه** است، و اگر گوشی در حالتِ ذخیرهٔ باتری باشد
 *  ممکن است دیرتر هم بشود. یعنی «فوری» نیست، «به‌زودی» است. اعلانِ
 *  همان لحظه فقط با FCM ممکن است، که درگاهش گوگل می‌شود نه سرورِ
 *  خودتان.
 *
 *  ## چرا فقط «تغییرها»
 *
 *  هر بار که این کار اجرا می‌شود، همان کالاهای تمام‌شده هنوز تمام‌اند.
 *  اگر هر بار خبر می‌داد، تا ظهر بیست اعلانِ تکراری آمده بود و کاربر
 *  اعلان‌های برنامه را خاموش می‌کرد. پس آنچه قبلاً خبر داده شده ذخیره
 *  می‌شود و فقط چیزهای **تازه** اعلان می‌گیرند.
 */
object Watchman {

  private const val PREFS = "tohid-watch"
  private const val SEEN_OUT = "notified_out"
  private const val SEEN_DEBT = "notified_debt"
  private const val SEEN_SUB = "notified_sub_step"
  private const val SEEN_NEWS = "notified_news_at"

  private const val WORK = "tohid-watchman"

  /** هر ربع ساعت — کوتاه‌ترین فاصله‌ای که اندروید اجازه می‌دهد */
  private const val EVERY_MINUTES = 15L

  /** از این مبلغ به بالا، قرض «زیاد» است — قابل تغییر در تنظیمات */
  const val DEFAULT_DEBT_LIMIT = 5_000.0

  /* ------------------------------ کانال‌ها ------------------------------ */

  /*
   *  هر خبر کانالِ خودش را دارد، نه یک کانالِ «اعلان‌ها».
   *
   *  کاربری که از خبرِ قرض خوشش نمی‌آید باید بتواند همان یکی را خاموش
   *  کند بی‌آنکه خبرِ تمام شدنِ کالا را هم از دست بدهد. با یک کانالِ
   *  مشترک، انتخابش «همه یا هیچ» بود.
   */
  private data class Channel(val id: String, val title: String, val about: String, val noteId: Int)

  private val STOCK = Channel("tohid-stock", "کالای تمام‌شده", "وقتی موجودی کالایی صفر شود", 4301)
  private val DEBT = Channel("tohid-debt", "قرض زیاد", "وقتی قرضِ یک نفر از حد بگذرد", 4302)
  private val SUB = Channel("tohid-subscription", "اشتراک", "پیش از تمام شدن اشتراک", 4303)
  private val NEWS = Channel("tohid-news", "خبر دکان", "کارِ بقیهٔ اعضای دکان", 4304)

  private val ALL = listOf(STOCK, DEBT, SUB, NEWS)

  /* ------------------------------ راه‌اندازی ------------------------------ */

  /** صدا زدنش چند بار بی‌ضرر است */
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<Worker>(EVERY_MINUTES, TimeUnit.MINUTES)
      //  باتریِ کم را دست نمی‌زنیم: خبرِ قرض ارزشِ خالی کردنِ باتریِ
      //  کسی را ندارد که وسطِ راه است
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .build()
    runCatching {
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** حدِ قرض — صاحب دکان در تنظیمات عوضش می‌کند */
  fun debtLimit(context: Context): Double =
    prefs(context).getFloat("debt_limit", DEFAULT_DEBT_LIMIT.toFloat()).toDouble()

  fun setDebtLimit(context: Context, value: Double) {
    prefs(context).edit()
      .putFloat("debt_limit", value.coerceAtLeast(0.0).toFloat())
      //  حد که عوض شد، آنچه قبلاً خبر داده شده باید از نو سنجیده شود
      .remove(SEEN_DEBT)
      .apply()
  }

  private fun allowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

  private fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    ALL.forEach { channel ->
      manager.createNotificationChannel(
        NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_DEFAULT)
          .apply { description = channel.about }
      )
    }
  }

  private fun notify(context: Context, channel: Channel, title: String, text: String) {
    if (!allowed(context)) return
    ensureChannels(context)

    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = open?.let {
      PendingIntent.getActivity(
        context, channel.noteId,
        it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_IMMUTABLE,
      )
    }

    val note = NotificationCompat.Builder(context, channel.id)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setAutoCancel(true)
      .apply { pending?.let { setContentIntent(it) } }
      .build()

    runCatching { NotificationManagerCompat.from(context).notify(channel.noteId, note) }
  }

  /* ------------------------------ سه نگهبان ------------------------------ */

  /**
   *  کالایی که **تازه** تمام شده.
   *
   *  فهرستِ خبرداده‌شده ذخیره می‌شود؛ کالایی که دوباره پر شود از فهرست
   *  بیرون می‌آید، پس اگر بار دیگر تمام شد دوباره خبر می‌دهد.
   */
  fun checkStock(context: Context, d: ShopData) {
    val index = ShopStore.index(d)
    val out = d.products.filter { index.status(it) == "out" }
    val ids = out.map { it.id }.toSet()
    val known = prefs(context).getStringSet(SEEN_OUT, emptySet()).orEmpty()

    val fresh = out.filter { it.id !in known }
    if (fresh.isNotEmpty()) {
      val title = if (fresh.size == 1) "${fresh[0].name} تمام شد" else "${fresh.size} کالا تمام شد"
      notify(context, STOCK, title, fresh.take(4).joinToString("، ") { it.name })
    }
    if (ids != known) prefs(context).edit().putStringSet(SEEN_OUT, ids).apply()
  }

  /**
   *  قرضی که از حد گذشته.
   *
   *  فقط لحظهٔ **گذشتن** خبر می‌دهد، نه هر بار که بالای حد است. کسی که
   *  ده هزار قرض دارد نباید هر ربع ساعت یادآوری بگیرد.
   */
  fun checkDebt(context: Context, d: ShopData) {
    val limit = debtLimit(context)
    if (limit <= 0) return

    val over = d.debtors
      .map { it to ShopStore.debt(d, it.id) }
      .filter { it.second >= limit }

    val ids = over.map { it.first.id }.toSet()
    val known = prefs(context).getStringSet(SEEN_DEBT, emptySet()).orEmpty()

    val fresh = over.filter { it.first.id !in known }
    if (fresh.isNotEmpty()) {
      val title = if (fresh.size == 1) "قرضِ ${fresh[0].first.name} زیاد شد"
      else "${fresh.size} نفر قرضِ زیاد دارند"
      val text = fresh.take(4).joinToString("، ") {
        "${it.first.name}: ${money(it.second)} افغانی"
      }
      notify(context, DEBT, title, text)
    }
    if (ids != known) prefs(context).edit().putStringSet(SEEN_DEBT, ids).apply()
  }

  /**
   *  اشتراکی که رو به پایان است.
   *
   *  چهار پله: هفت روز، سه روز، یک روز، و تمام‌شده. هر پله یک بار خبر
   *  می‌دهد؛ ذخیرهٔ آخرین پله جلوی تکرار را می‌گیرد. با تمدید شدنِ
   *  اشتراک، پله صفر می‌شود و دفعهٔ بعد از نو خبر می‌دهد.
   */
  fun checkSubscription(context: Context, daysLeft: Int?, expired: Boolean) {
    val step = when {
      expired -> 0
      daysLeft == null -> return
      daysLeft <= 1 -> 1
      daysLeft <= 3 -> 3
      daysLeft <= 7 -> 7
      else -> {
        //  اشتراک تازه شده — پله را صفر می‌کنیم تا دفعهٔ بعد دوباره
        //  خبر بدهد
        prefs(context).edit().remove(SEEN_SUB).apply()
        return
      }
    }

    val last = prefs(context).getInt(SEEN_SUB, Int.MAX_VALUE)
    if (step >= last) return                     // این پله یا پایین‌ترش خبر داده شده

    val (title, text) = when (step) {
      0 -> "اشتراک تمام شد" to "برای باز شدن دوبارهٔ صندوق فروش، اشتراک را تمدید کنید."
      1 -> "اشتراک فردا تمام می‌شود" to "پیش از تمام شدن تمدید کنید تا کارتان نخوابد."
      else -> "اشتراک تا ${step} روز دیگر تمام می‌شود" to "همین حالا تمدید کنید تا بعداً عجله نشود."
    }
    notify(context, SUB, title, text)
    prefs(context).edit().putInt(SEEN_SUB, step).apply()
  }

  /** خبرهای بقیهٔ اعضا — فروشی که شاگرد زد، کالایی که دستِ او تمام شد */
  fun notifyNews(context: Context, title: String, body: String, at: Long) {
    val last = prefs(context).getLong(SEEN_NEWS, 0)
    if (at <= last) return
    notify(context, NEWS, title, body)
    prefs(context).edit().putLong(SEEN_NEWS, at).apply()
  }

  fun lastNewsAt(context: Context): Long = prefs(context).getLong(SEEN_NEWS, 0)

  /* ------------------------------ کارِ دوره‌ای ------------------------------ */

  class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      val app = applicationContext
      runCatching {
        val store = ShopStore(app)
        store.load()
        val data = store.data.value

        checkStock(app, data)
        checkDebt(app, data)

        //  اشتراک از خودِ مجوز خوانده می‌شود، نه از سرور: نبودنِ
        //  اینترنت نباید جلوی هشدارِ اشتراک را بگیرد
        val state = ir.vil3ntec.tohid.sync.SyncStore(app)
        val status = ir.vil3ntec.tohid.sync.LicenseGuard.status(app, state)
        when (status.state) {
          ir.vil3ntec.tohid.sync.License.State.ACTIVE ->
            checkSubscription(app, status.daysLeft(), expired = false)
          ir.vil3ntec.tohid.sync.License.State.EXPIRED,
          ir.vil3ntec.tohid.sync.License.State.GRACE ->
            checkSubscription(app, null, expired = true)
          else -> Unit                       // هنوز اشتراکی نبوده؛ خبری هم نیست
        }
      }

      //  خبرهای اعضای دیگر — جدا، چون شبکه می‌خواهد و ممکن است نباشد
      runCatching { pullNews(app) }
      return Result.success()
    }

    private suspend fun pullNews(app: Context) {
      if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(app)) return
      if (!ir.vil3ntec.tohid.data.repo.Backend.isOnline(app)) return

      val since = lastNewsAt(app)
      ir.vil3ntec.tohid.data.repo.Backend.events(app).feed(since)
        .onSuccess { feed ->
          val me = ir.vil3ntec.tohid.sync.SyncStore(app).accountId
          //  خبرِ خودم برای خودم خبر نیست
          val others = feed.events.filter { it.userId != me }
          if (others.isEmpty()) return@onSuccess

          val newest = others.maxOf { it.at }
          val title = if (others.size == 1) others[0].title
          else "${others.size} خبر تازه از دکان"
          val body = others.take(4).joinToString(" • ") {
            listOf(it.userName, it.title).filter { part -> part.isNotBlank() }.joinToString(": ")
          }
          notifyNews(app, title, body, newest)
        }
    }
  }
}
