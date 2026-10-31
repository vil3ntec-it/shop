package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestMigration
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncRebuild
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import java.io.File

/**
 *  دفترِ دکان.
 *
 *  همان یک فایلِ JSON که نسخهٔ وب هم می‌نوشت — با همان کلیدها. پس هم داده‌های
 *  قدیمی مستقیم خوانده می‌شوند و هم فایلِ پشتیبان بین دو نسخه یکی می‌ماند.
 *
 *  محاسبه‌ها عمداً همان فرمولِ نسخهٔ وب‌اند، حتی جاهایی که می‌شد «بهترش» کرد:
 *  عددی که فروشنده روی کاغذ دارد نباید با به‌روزرسانی عوض شود.
 */
class ShopStore(private val context: Context) {
  init { DebtMinorFlag.bind(context) }  //  `shop.debt_minor`: `debt` و موتورهای خالص پرچمِ همین دکان را از `DebtMinorFlag` می‌خوانند

  private val file = File(context.filesDir, "shop-data.json")
  private val json = Json {
    ignoreUnknownKeys = true      // نسخهٔ وب ممکن است فیلدی اضافه کند
    encodeDefaults = true
    isLenient = true
  }

  private val _data = MutableStateFlow(ShopData())
  val data: StateFlow<ShopData> = _data.asStateFlow()

  /**
   *  آیا دفتر از روی دیسک خوانده شده؟
   *
   *  تا پیش از این، صفحه‌ها پیش از خوانده‌شدنِ فایل با دفترِ خالی کشیده
   *  می‌شدند و کاربری که صد قلم کالا داشت، یک لحظه «هنوز محصولی ثبت نشده»
   *  می‌دید. حالا تا وقتی خواندن تمام نشده، اسکلتِ بارگذاری نشان داده
   *  می‌شود.
   */
  private val _loaded = MutableStateFlow(false)
  val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

  /**
   *  @param sweep CRM-10: sweep ِ شروع ([loadAndSummarize]) — **پیش از** `loaded = true`. صفحه تا آن
   *         لحظه دفتر را برنمی‌دارد، پس ذخیره‌ای که از دفترِ پیش از sweep ساخته شده باشد پیش نمی‌آید
   *         و sweep (با ردهای دفترچه‌اش) برنمی‌گردد. کارگر و خلاصه (`load()` ِ بی sweep) چیزی نمی‌نویسند.
   */
  suspend fun load(sweep: Boolean = false) {
    withContext(Dispatchers.IO) {
      //  SEC-01: بازسازیِ نیمه‌کاره پیش از خواندنِ دفتر تمام یا دور ریخته می‌شود (`SyncRebuild.atLoad`)
      val stuck = runCatching { recoverRebuild() }.getOrElse { rebuildMark.exists() && rebuildSide.exists() }
      if (stuck) {
        /*
         *  ⛔ سایه همین حالا سایهٔ سرور است ولی جابه‌جایی جا نیفتاد: دفترِ قدیم (شاید خواندنی)
         *  کنارِ سایهٔ تازه نوشته نمی‌شود — وگرنه نخستین ذخیره برای هر ردیفی که فقط سرور دارد
         *  DELETE می‌ساخت. همان نگهبانِ «دفترِ خوانده‌نشده»: نوشتن بسته، راهِ برگشت «دوباره
         *  امتحان» (همین جابه‌جایی) یا «بازسازی از سرور».
         */
        _unreadable.value = AtRestException.Kind.IO
        _data.value = ShopData()
      } else if (file.exists()) {
        //  SEC-01: دفترِ ساده همان‌طور خوانده می‌شود؛ رمزدار با کلیدِ همین دستگاه
        val text = try {
          AtRest.readText(context, file).also { _unreadable.value = null }
        } catch (e: AtRestException) {
          _unreadable.value = e.kind
          null
        } catch (e: Exception) {
          //  همان رفتارِ همیشهٔ دفترِ ساده: خطای خواندن بی‌صدا، دفترِ خالی
          null
        }
        if (text != null) {
          runCatching { json.decodeFromString<ShopData>(text) }
            .onSuccess { _data.value = it; recoverSync(it) }
        }
      } else {
        _unreadable.value = null
        recoverSync(ShopData())
      }
    }
    if (sweep) quietly { sweepNow(requireLoaded = false) }
    _loaded.value = true
  }

  /**
   *  SEC-01 — دفترِ رمزداری که روی این دستگاه باز نشد (کلید نیست، گذرا در
   *  دسترس نیست، یا فایل خراب است). `null` یعنی دفتر سالم خوانده شد.
   *
   *  ⛔ تا وقتی این پر است **هیچ نوشتنی جز بازیابی** به دفتر نمی‌رسد:
   *  دفترِ حافظه خالی است و ذخیره‌اش یعنی رونویسیِ دفترِ واقعی با هیچ —
   *  و تفاضلِ همگام‌سازی همهٔ ردیف‌ها را «حذف‌شده» به سرور می‌فرستاد.
   *  بازیابی (پشتیبانِ ابری/فایل/نسخهٔ خودکار) اول فایلِ خوانده‌نشده را
   *  کنار می‌گذارد (`shop-data.json.unreadable-<زمان>`)، هرگز پاکش نمی‌کند.
   *  با دفترِ ساده (پیش‌فرض) هرگز پر نمی‌شود.
   */
  private val _unreadable = MutableStateFlow<AtRestException.Kind?>(null)
  val unreadable: StateFlow<AtRestException.Kind?> = _unreadable.asStateFlow()

  /**
   *  هم‌سو کردنِ فایل‌های دفتر با تنظیمِ «رمزگذاریِ داده» — زیرِ همان قفلِ
   *  نوشتنِ دفتر (`AtRest.converge`). با هر بازشدن و با هر تغییرِ تنظیم.
   */
  suspend fun convergeAtRest(): AtRestMigration.Report = withContext(Dispatchers.IO) {
    runCatching { AtRest.converge(context, SyncV1Engine.of(context)) }
      .getOrElse { AtRestMigration.Report(failed = listOf("*" to AtRestException.Kind.IO)) }
  }

  /**
   *  commitِ همگام‌سازی‌ای که برنامه وسطش بسته شد، پیش از هر نوشتنی تمام یا
   *  برگردانده می‌شود — با همین دفترِ روی دیسک (F0-05، شرح سرِ `SyncCommit`).
   *  ⚠️ خواندنِ دفتر هرگز به‌خاطرِ این نمی‌ایستد.
   */
  private fun recoverSync(onDisk: ShopData) {
    runCatching { SyncV1Engine.of(context).recover(onDisk) }
  }

  /**
   *  دفتر خوانده شد و خلاصه‌اش هم تازه شد.
   *
   *  با باز شدنِ برنامه یک بار صدا زده می‌شود. اگر خلاصه از دست رفته
   *  باشد — نسخهٔ تازه روی دفترِ قدیمی، یا پاک شدنِ حافظهٔ برنامه — همین
   *  یک بار دوباره ساخته می‌شود و کارهای پس‌زمینه از آن به بعد ارزان
   *  می‌مانند.
   */
  suspend fun loadAndSummarize() {
    //  CRM-10: sweep ِ شروع درونِ `load`، پیش از `loaded` (شرح سرِ `load`)
    load(sweep = true)
    //  دفترِ خوانده‌نشده خلاصهٔ «خالی» نمی‌گیرد
    if (_unreadable.value == null) withContext(Dispatchers.IO) { LedgerSummary.write(context, _data.value) }
    //  SEC-01: با تنظیمِ خاموش و دفترِ ساده فقط سرآیندِ چند فایل خوانده می‌شود
    convergeAtRest()
  }

  /**
   *  CRM-10: ردیف‌های دیررسیده (فروش یا قرضی که دستگاهِ آفلاین یا ساختِ قدیمی روی مشتریِ
   *  ادغام‌شده ثبت کرد) و حلقهٔ دو ادغامِ وارونه ⇐ مشتریِ ماندگار (`CustomerMerge.sweep`).
   *  پس از هر Pull (`SyncV1Worker`: v1 با `absorbRemote` و v3، و [applyFromServer]) و یک
   *  بار در شروع ([loadAndSummarize] ⇐ [load]، پیش از `loaded`).
   *
   *  - فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض **خاموش**): خاموش ⇒
   *    هیچ کاری نمی‌شود و رفتارِ دکان همان است.
   *  - **زیرِ همان قفلِ نوشتنِ دفتر** (`synchronized(engine)`، مثلِ [update] و نشاندنِ opهای
   *    رسیده)، روی **دفترِ همین لحظهٔ دیسک**، نه `_data` ِ این نمونه: صفحه و کارگرِ همگام‌سازی
   *    (`SyncV1Worker`) دو نمونهٔ جدای `ShopStore` دارند و دفترِ حافظهٔ یکی از دیگری کهنه است.
   *  - ⛔ هرگز `record` ِ کلِ دفتر: فقط opهای هدفمندِ خودِ sweep (`CustomerMerge.sweepOps`:
   *    UPDATE ِ `debtorId`/`mergedInto` و INSERT ِ ردِ دفترچه) با `SyncV1Engine.recordOps`
   *    (سایه = سایه + همین opها). پس هیچ DELETEی ساخته نمی‌شود.
   *  - ⛔ روی همگام‌سازیِ v3 نمی‌نشیند: آن‌جا عوض کردنِ `debtorId` ِ سندِ مالی (قرض، فاکتور)
   *    «ویرایشِ سند» است، با قالبِ v2 می‌رود و سرور آن را به صندوقِ تعارض می‌برد
   *    (`SyncV3Engine`، آزمونِ `docEditIsNotLostGoesAsV2`). خودِ ادغام هم همین‌طور؛ راهِ v3 باید
   *    سمتِ سرور (یا با سندِ معکوس) ساخته شود.
   *  - نوشتنِ بسته ⇒ هیچ، بی پیام: دفترِ بارنشده، دفترِ خوانده‌نشده (SEC-01 / #331) یا
   *    قفلِ نرم. خطای دیسکِ همین sweep هم پیامِ «ذخیره نشد» ِ صفحه نمی‌شود؛ دورِ بعد دوباره.
   *  - خودتوان: کاری نبود ⇒ `null`، نه نوشتن نه op. هر دستگاه از همان دفتر همان نتیجه را
   *    می‌سازد (کوچک‌ترین شناسهٔ حلقه)، پس دو دستگاه پینگ‌پنگ نمی‌سازند.
   *
   *  @return null (کاری نبود یا نوشتن بسته/خاموش) · true (نوشته شد) · false (دیسک)
   */
  suspend fun sweepMerged(): Boolean? = sweepNow(requireLoaded = true)

  /** @param requireLoaded `false` فقط برای sweep ِ شروع درونِ [load]، پیش از `loaded = true` */
  private suspend fun sweepNow(requireLoaded: Boolean): Boolean? = withContext(Dispatchers.IO) {
    if (!ShopPolicy.crmReports(context)) return@withContext null
    if (SyncChoice.isV3(context)) return@withContext null
    val engine = SyncV1Engine.of(context)
    synchronized(engine) {
      if ((requireLoaded && !_loaded.value) || _unreadable.value != null) return@synchronized null
      val disk = readDiskLocked() ?: return@synchronized null
      val next = CustomerMerge.sweep(disk) ?: return@synchronized null
      val ops = CustomerMerge.sweepOps(disk, next) ?: return@synchronized null
      if (SoftLock.isLocked(context)) return@synchronized null
      //  همان قاعدهٔ `writeLocked`: دفتری که به دیسک نرسید op نمی‌سازد (F0-01)
      if (!writeToDisk(next, quiet = true)) return@synchronized false
      runCatching { if (engine.recordOps(ops) > 0) SyncV1Worker.now(context) }
      true
    }
  }

  /** همان [sweepMerged] برای جاهایی که شکستش نباید Pull یا بارِ دفتر را بگیرد — لغو بالا می‌رود */
  suspend fun sweepMergedQuietly() = quietly { sweepMerged() }

  /** شکست بی‌صدا، ولی لغو (`CancellationException`) بالا می‌رود */
  private suspend fun quietly(block: suspend () -> Unit) {
    try {
      block()
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (_: Exception) {
    }
  }

  /** دفترِ همین لحظهٔ دیسک؛ فراخوان قفلِ نوشتن (`synchronized(engine)`، یا تبدیلِ [update]) را دارد. `null` ⇒ نبود یا خوانده نشد */
  internal fun readDiskLocked(): ShopData? = runCatching {
    if (!file.exists()) null else json.decodeFromString<ShopData>(AtRest.readText(context, file))
  }.getOrNull()

  /**
   *  ذخیرهٔ دفتر — تنها درِ نوشتن.
   *
   *  ⛔ با اشتراکِ تمام‌شده **نمی‌نویسد** (قفلِ نرمِ بندِ ۲۱٫۸): دفتر
   *  دست‌نخورده می‌ماند و صفحه همان چیزی را نشان می‌دهد که روی دیسک
   *  است. هیچ چیزی پاک نمی‌شود و خروجی و چاپ و پشتیبان باز می‌مانند.
   *
   *  @return آیا نوشته شد. `false` یعنی قفلِ نرم یا خطای دیسک — صفحه
   *  نباید «ثبت شد» بگوید؛ دلیلش از `blocked` یا `diskError` گفته می‌شود.
   */
  suspend fun save(next: ShopData): Boolean = withContext(Dispatchers.IO) {
    //  ⛔ قفلِ نرم فقط بخش‌های پولی را می‌بندد — فروش و قرض‌داران. انبار،
    //  کالا، خرید و مصارف «همیشه باز» است، همان‌که صفحهٔ قفل می‌گوید.
    if (touchesPaid(_data.value, next) && SoftLock.isLocked(context)) {
      SoftLock.noteBlocked()
      _blocked.tryEmit(Unit)
      return@withContext false
    }
    writeInternal(next)
  }

  /**
   *  نوشتنی که قفلِ نرم ردش کرد — `AppRoot` پیامِ روشن نشان می‌دهد، تا کسی
   *  فکر نکند فروشش ثبت شده (گزارشِ ممیزی: صفحه‌ها «ثبت شد» می‌گفتند).
   */
  private val _blocked = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val blocked: kotlinx.coroutines.flow.SharedFlow<Unit> = _blocked

  private fun touchesPaid(cur: ShopData, next: ShopData): Boolean =
    cur.sales != next.sales || cur.saleItems != next.saleItems ||
      cur.saleReturns != next.saleReturns || cur.transactions != next.transactions || cur.debtors != next.debtors

  /**
   *  بازیابیِ پشتیبان — بیرونِ قفلِ نرم.
   *
   *  ⛔ دادهٔ خودِ کاربر است و گروگان گرفتنش سریع‌ترین راهِ از دست دادنِ
   *  اعتماد. همان قاعده‌ای که برای خروجی و چاپ هم هست.
   */
  suspend fun applyRestore(next: ShopData): Boolean =
    if (_unreadable.value != null) restoreUnreadable(next) else writeInternal(next)

  /* ------------- SEC-01: دفترِ رمزدارِ خوانده‌نشده (بازبینیِ #331) ------------- */

  /** نتیجهٔ «بازسازی از سرور» — با پیامِ آمادهٔ صفحه */
  data class Rebuilt(val ok: Boolean, val message: String)

  /**
   *  پیامِ روشنِ کارهایی که از صفحهٔ خودشان بیرون می‌افتند (بازیابی در حالِ دفترِ خوانده‌نشده)
   *  — `AppRoot` نشانش می‌دهد. با دفترِ ساده (پیش‌فرض) هرگز چیزی نمی‌فرستد.
   */
  private val _notice = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
  val notice: kotlinx.coroutines.flow.SharedFlow<String> = _notice

  /**
   *  «بازسازی از سرور» — راهِ برگشتِ دفتری که این دستگاه باز نمی‌کند (گوشیِ تازه، Keystore
   *  ازدست‌رفته، فایلِ خراب).
   *
   *  ۱) صفِ این دستگاه اول فرستاده می‌شود؛ خالی نشد ⇒ هیچ چیزی عوض نمی‌شود.
   *  ۲) دفتر از Snapshot ِ سرور، سایه و cursor با آن از نو — **پیش از** دفتر؛ هیچ تفاضلی با
   *     سایهٔ کهنه گرفته نمی‌شود و هیچ opی ساخته نمی‌شود (شرح سرِ `SyncRebuild`).
   *  ۳) دفترِ خوانده‌نشده کنار می‌رود (`.unreadable-<زمان>`)، پاک نمی‌شود.
   *
   *  @param backup پشتیبانی که در همین حال بازیابی می‌شود (ابری، پوشه، فایل، نسخهٔ ایمنی):
   *  تنظیماتش می‌ماند و فقط ردیف‌هایی افزوده می‌شوند (INSERT) که هرگز در سایهٔ پیشین نبودند
   *  (`SyncRebuild.withBackupRows`) — هرگز DELETE، برگرداندنِ ردیفِ تازه‌ترِ سرور، یا زنده کردنِ
   *  ردیفی که پس از پشتیبان حذف شد. حسابِ همگام‌شده بی ورود: بازیابی رد می‌شود.
   */
  suspend fun rebuildFromServer(backup: ShopData? = null): Rebuilt = withContext(Dispatchers.IO) {
    if (_unreadable.value == null) return@withContext Rebuilt(false, "دفترِ این دستگاه باز است؛ بازسازی لازم نیست.")
    if (SyncChoice.isV3(context)) {
      return@withContext Rebuilt(false, "بازسازی از سرور فعلاً فقط با همگام‌سازیِ نسخهٔ ۱ است؛ هیچ چیزی پاک نشده.")
    }
    val engine = SyncV1Engine.of(context)
    engine.rebind()
    if (!Backend.isReady(context)) {
      if (backup == null) return@withContext Rebuilt(false, NEED_SIGN_IN)
      //  ⛔ حسابی که روزی همگام شده: ردیفِ پشتیبان که سرور ندارد شاید حذف‌شده باشد ⇒ اول ورود
      if (engine.everSynced()) return@withContext Rebuilt(false, NEED_SIGN_IN_SYNCED)
      //  هرگز همگام‌نشده: پشتیبان خودش سایه می‌شود ⇐ هیچ op
      return@withContext runCatching { engine.resetTo(backup, staging) }.fold(
        { Rebuilt(true, "پشتیبان بازیابی شد.") },
        { Rebuilt(false, "بازیابی انجام نشد (${it.message}). هیچ چیزی پاک نشده.") },
      )
    }
    if (!Backend.isOnline(context)) return@withContext Rebuilt(false, NEED_NET)
    val out = try {
      engine.rebuildFromServer(backup ?: ShopData(), staging)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      return@withContext Rebuilt(false, "بازسازی انجام نشد (${e.message ?: e.javaClass.simpleName}). هیچ چیزی پاک نشده؛ دوباره امتحان کنید.")
    }
    when (out) {
      is SyncRebuild.Outcome.QueueLeft -> Rebuilt(
        false,
        "${out.count} تغییرِ این دستگاه هنوز به سرور نرسیده؛ اول باید برسند تا گم نشوند. به اینترنت وصل شوید و دوباره امتحان کنید.",
      )
      is SyncRebuild.Outcome.Done -> {
        //  کفِ شمارهٔ فاکتور پس از افزودنِ ردیف‌های پشتیبان هم (درونِ `withBackupRows`)
        val merged = backup?.let { SyncRebuild.withBackupRows(out.ledger, it, out.before) } ?: out.ledger
        val done = "دفتر از سرور بازسازی شد (${out.rows} ردیف)."
        when {
          backup == null -> Rebuilt(true, done)
          merged == out.ledger -> Rebuilt(
            true,
            done + if (out.before == null) " ردیف‌هایی که فقط در پشتیبان بودند افزوده نشدند: معلوم نیست پس از پشتیبان حذف شده‌اند یا نه." else "",
          )
          writeInternal(merged) ->
            Rebuilt(true, "$done ${backupSize(merged) - backupSize(out.ledger)} ردیفی که هرگز به سرور نرفته بود از پشتیبان افزوده شد.")
          else -> Rebuilt(false, "$done ولی ردیف‌های پشتیبان نوشته نشدند؛ دوباره بازیابی کنید.")
        }
      }
    }
  }

  /** بازیابیِ پشتیبان وقتی دفترِ رمزدار باز نمی‌شود — همیشه از راهِ «بازسازی از سرور» */
  private suspend fun restoreUnreadable(backup: ShopData): Boolean {
    val r = rebuildFromServer(backup)
    _notice.tryEmit(r.message)
    return r.ok
  }

  /** کناری و نشانِ «بازسازیِ نیمه‌کاره» — شرح سرِ `SyncRebuild` */
  private val rebuildSide: File get() = File(file.absoluteFile.parentFile, file.name + ".rebuild")
  private val rebuildMark: File get() = File(file.absoluteFile.parentFile, file.name + ".rebuild-pending")

  /** دو گامِ فایلیِ بازسازی، زیرِ قفلِ موتور: کناری + نشان، و جابه‌جایی */
  private val staging = object : SyncRebuild.Staging {
    /** دفتری که همین بازسازی گذاشت — برای شناختنِ جابه‌جاییِ تمام‌شده به دستِ دیگری */
    @Volatile private var staged: ShopData? = null

    override fun stage(next: ShopData, shadowSha: String) {
      AtRest.writeText(context, rebuildSide, json.encodeToString(next))
      DiskWrite.text(rebuildMark, shadowSha)
      staged = next
    }

    override fun swap() {
      if (finishRebuild()) { staged = null; return }
      //  کارگرِ پس‌زمینه (نمونهٔ دیگرِ همین کلاس) زودتر تمامش کرد؟ نشان و کناری رفته‌اند و دفترِ
      //  روی دیسک همان است که این بازسازی گذاشت ⇒ تمام شده است، نه «جا نیفتاد»
      val side = rebuildSide.exists()
      val mark = rebuildMark.exists()
      val onDisk = if (!side && !mark) runCatching { json.decodeFromString<ShopData>(AtRest.readText(context, file)) }.getOrNull() else null
      if (SyncRebuild.swappedElsewhere(side, mark, onDisk, staged)) {
        _unreadable.value = null
        _data.value = onDisk!!
        staged = null
        return
      }
      throw java.io.IOException("دفترِ بازسازی‌شده جا نیفتاد؛ با «دوباره امتحان» یا باز شدنِ دوبارهٔ برنامه تمام می‌شود")
    }
  }

  /**
   *  کناری جای دفتر — بی op. دفترِ قبلی (خوانده‌نشده، یا خواندنیِ کهنه) کنار می‌رود
   *  (`.unreadable-<زمان>`)، پاک نمی‌شود؛ نشان آخر از همه پاک می‌شود.
   */
  private fun finishRebuild(): Boolean {
    val side = rebuildSide
    if (!side.isFile) return false
    val next = runCatching { json.decodeFromString<ShopData>(AtRest.readText(context, side)) }.getOrElse { return false }
    if (file.exists()) {
      val keep = File(file.absoluteFile.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}")
      if (runCatching { DiskWrite.move(file, keep) }.isFailure) return false
    }
    if (runCatching { DiskWrite.move(side, file) }.isFailure) return false
    rebuildMark.delete()
    _unreadable.value = null
    _data.value = next
    runCatching { LedgerSummary.write(context, next) }
    return true
  }

  /**
   *  هنگامِ باز شدن: بازسازی‌ای که میانِ سایه و جابه‌جایی بسته شد تمام می‌شود؛ پیش از سایه، دور ریخته.
   *  وقتی همین فرایند وسطِ بازسازی است (`SyncV1Engine.rebuilding` — مثلاً کارگرِ پس‌زمینه دفتر را
   *  می‌خواند) به هیچ‌کدام دست نمی‌زند.
   *
   *  @return `true` یعنی سایه با نشان می‌خواند ولی جابه‌جایی **نشد** — `load` نوشتن را می‌بندد
   */
  private fun recoverRebuild(): Boolean {
    val mark = rebuildMark
    val side = rebuildSide
    if (!mark.exists() && !side.exists()) return false
    val engine = SyncV1Engine.of(context)
    if (engine.rebuilding) return false
    val marker = if (mark.exists()) runCatching { mark.readText().trim() }.getOrNull() else null
    return synchronized(engine) {
      //  Sync v3 بازسازی ندارد ⇒ نشان هرگز نمی‌خواند ⇒ دور ریخته
      when (SyncRebuild.atLoad(marker, side.isFile, if (SyncChoice.isV3(context)) "" else engine.shadowSha())) {
        SyncRebuild.AtLoad.FINISH -> !finishRebuild()
        SyncRebuild.AtLoad.DISCARD -> { side.delete(); mark.delete(); false }
        SyncRebuild.AtLoad.NONE -> false
      }
    }
  }

  /**
   *  خواندن ⇐ تبدیل ⇐ نوشتن **زیرِ همان قفلِ موتور** (`synchronized(engine)`) که نشاندنِ
   *  opهای رسیده هم زیرِ آن است: [transform] همیشه تازه‌ترین دفتر را می‌گیرد، نه نسخهٔ کهنهٔ
   *  صفحه — نوشتنی که در پس‌زمینه می‌رود (فروشِ تحویلِ سفارش) چیزی از دیگران را پاک نمی‌کند.
   *  [transform] باید سبک و خالص باشد. `null` ⇒ چیزی برای نوشتن نبود. قفلِ نرم مثلِ [save].
   *  @return null (هیچ) · true (نوشته شد) · false (قفلِ نرم یا دیسک)
   */
  suspend fun update(transform: (ShopData) -> ShopData?): Boolean? = withContext(Dispatchers.IO) {
    val engine = SyncV1Engine.of(context)
    synchronized(engine) {
      val cur = _data.value
      val next = transform(cur) ?: return@synchronized null
      if (touchesPaid(cur, next) && SoftLock.isLocked(context)) {
        SoftLock.noteBlocked()
        _blocked.tryEmit(Unit)
        return@synchronized false
      }
      writeLocked(engine, next)
    }
  }

  /**
   *  CRM-10: آیا «ادغامِ مشتری» روی این دستگاه باز است؟ `null` ⇒ باز؛ وگرنه همان یک خطِ دلیل
   *  برای کنارِ دکمهٔ بسته.
   *
   *  ⛔ روی همگام‌سازیِ v3 بسته است: آن‌جا جابه‌جاییِ `debtorId` ِ قرض و فاکتور «ویرایشِ سندِ
   *  مالی» است، با قالبِ v2 می‌رود و سرور آن را به صندوقِ تعارض می‌برد (`SyncV3Engine`، آزمونِ
   *  `docEditIsNotLostGoesAsV2`). راهِ سرور بعداً ساخته می‌شود (STATUS: CRM-10 ⇒ PARTIAL).
   */
  fun mergeBlockedReason(): String? = if (SyncChoice.isV3(context)) StrShell.mergeNotOnV3 else null

  /**
   *  CRM-10: تنها درِ ادغامِ دو مشتری — `CustomerMerge.merge` روی دفترِ همین لحظه، از همان
   *  [update] (قفلِ نوشتن، قفلِ نرم، op). بسته ([mergeBlockedReason]) ⇒ `Failed` با همان دلیل:
   *  نه نوشتن، نه op.
   */
  suspend fun mergeCustomers(keepId: String, dropId: String): CustomerMerge.Result {
    mergeBlockedReason()?.let { return CustomerMerge.Result.Failed(it) }
    var out: CustomerMerge.Result = CustomerMerge.Result.Failed(StrShell.mergeNotSaved)
    val wrote = update { d ->
      when (val r = CustomerMerge.merge(d, keepId, dropId)) {
        is CustomerMerge.Result.Ok -> { out = r; r.data }
        is CustomerMerge.Result.Failed -> { out = r; null }
      }
    }
    return if (wrote == false) CustomerMerge.Result.Failed(StrShell.mergeNotSaved) else out
  }

  /**
   *  نوشتنی که قفلِ نرم جلویش را نمی‌گیرد: بازیابی و جابه‌جاییِ دفتر.
   *
   *  @return آیا به دیسک رسید
   */
  private suspend fun writeInternal(next: ShopData): Boolean = withContext(Dispatchers.IO) { writer().save(next) }

  /**
   *  ردیفِ سابقه (`auditLog`) روی دفترِ **همان لحظه** — خواندن و نوشتن زیرِ
   *  همان قفلِ `writeInternal`. برای هر نوشتنِ پس‌زمینه (کشوی بی فروش، چاپِ
   *  دوبارهٔ صفِ چاپ): `save(store.data.value.copy(…))` در فاصلهٔ خواندن تا
   *  گرفتنِ قفل فروشِ هم‌زمان را گم می‌کرد و opِ حذفش را به سرور می‌فرستاد.
   *  فقط بخشِ «همیشه باز» را می‌نویسد، پس قفلِ نرم ردش نمی‌کند.
   *
   *  @return آیا به دیسک رسید؛ پیش از بارِ دفتر هرگز (دفترِ خالی روی دفترِ واقعی نمی‌نشیند)
   */
  suspend fun appendAudit(row: (ShopData) -> AuditEntry): Boolean = withContext(Dispatchers.IO) {
    if (!_loaded.value) return@withContext false
    writer().edit(LedgerWriter.appendAudit(row))
  }

  /**
   *  نوشتن و op ساختن زیرِ همان قفلی که نشاندنِ opهای رسیده دارد
   *  (`SyncV1Engine.absorbRemote`) — شرحش همان‌جا.
   */
  private fun writer(): LedgerWriter {
    val engine = SyncV1Engine.of(context)
    return LedgerWriter(engine, { _data.value }) { next -> writeLocked(engine, next) }
  }

  /** بدنهٔ نوشتن؛ فراخوان قفلِ موتور را گرفته است ([LedgerWriter]، و [update] با `synchronized(engine)`) */
  private fun writeLocked(engine: SyncV1Engine, next: ShopData): Boolean {
    /*
     *  ⛔ دفتری که به دیسک نرسید op نمی‌سازد (F0-01). وگرنه opها به سرور
     *  می‌رفتند و سایه جلو می‌رفت، ولی پس از بستن و باز کردن دفترِ کهنه
     *  می‌ماند و تفاضلِ بعدی همان ردیف‌ها را «حذف‌شده» به سرور می‌فرستاد.
     */
    if (!writeToDisk(next)) return false
    /*
     *  ⛔ **تنها درِ نوشتن در دفتر همین‌جاست** و هر تغییری از همین‌جا رد
     *  می‌شود. پس دفترِ تغییرات (Sync v1) هم همین‌جا نوشته می‌شود:
     *  `record` تفاضلِ دفتر را با سایهٔ آخرین حالتِ همگام‌شده می‌گیرد و
     *  برای هر ردیفِ عوض‌شده یک op می‌سازد.
     *
     *  یعنی «نوشتنی که op نسازد» از نظرِ **ساختاری** ممکن نیست — نه
     *  اینکه یادمان باشد در هر یک از صدها جای نوشتن یک خط اضافه کنیم.
     *  شرح در `sync/v1/LedgerDiff.kt`.
     *
     *  ⚠️ هیچ خطایی از این‌جا بالا نمی‌رود: نرفتنِ همگام‌سازی نباید
     *  جلوی ذخیرهٔ دفترِ خودِ دکان‌دار را بگیرد.
     */
    runCatching {
      val made = engine.record(next)
      if (made > 0) SyncV1Worker.now(context)
    }
    return true
  }

  /**
   *  نشاندنِ دفتری که از **سرور** آمده.
   *
   *  ⚠️ عمداً `save()` را صدا نمی‌زند: آن یکی دوباره تفاضل می‌گرفت و
   *  همان ردیف‌هایی که تازه از سرور آمده‌اند را به سرور پس می‌فرستاد.
   *  سایه را خودِ موتور در همان لحظه جلو می‌برد.
   */
  suspend fun applyFromServer(next: ShopData) {
    withContext(Dispatchers.IO) { applyFromServerNow(next) }
    //  CRM-10 — شرح سرِ `sweepMerged`
    sweepMergedQuietly()
  }

  /**
   *  همان، بی تعلیق — برای `SyncV1Engine.absorbRemote` که زیرِ قفل صدایش می‌زند.
   *
   *  ⛔ اگر به دیسک نرسد **پرتاب می‌کند**: موتور نباید سایه و cursor را جلو
   *  ببرد؛ همان opها دورِ بعد دوباره گرفته می‌شوند.
   */
  fun applyFromServerNow(next: ShopData) {
    if (!writeToDisk(next)) throw java.io.IOException("دفتر روی دیسک نوشته نشد")
  }

  /**
   *  خطای نوشتنِ دفتر روی دیسک — `AppRoot` پیامِ روشن نشان می‌دهد (F0-01).
   *  پیش‌تر این خطا بی‌صدا خورده می‌شد و صفحه «ثبت شد» می‌گفت.
   */
  private val _diskError = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
  val diskError: kotlinx.coroutines.flow.SharedFlow<String> = _diskError

  /**
   *  @return آیا دفتر واقعاً روی دیسک نشست.
   *
   *  ⚠️ دفترِ حافظه (`data`) فقط **پس از** نشستن روی دیسک عوض می‌شود: آنچه
   *  صفحه نشان می‌دهد همان است که پس از بستن و باز کردن می‌ماند.
   */
  private fun writeToDisk(next: ShopData, quiet: Boolean = false): Boolean {
    //  SEC-01: دفترِ رمزدارِ خوانده‌نشده فقط با «بازسازی از سرور» جایگزین می‌شود (`finishRebuild`)
    //  — شرح سرِ `unreadable`
    if (_unreadable.value != null) {
      if (!quiet) _diskError.tryEmit(UNREADABLE)
      return false
    }
    //  فایلِ کناری + fsync + جابه‌جاییِ اتمی که روی ویندوز هم جایگزین
    //  می‌کند — شرحش سرِ `DiskWrite`. رمز فقط اگر «رمزگذاریِ داده» روشن است
    //  (`AtRest`)؛ خاموش ⇒ همان بایت‌های همیشه.
    val saved = runCatching { AtRest.writeText(context, file, json.encodeToString(next)) }
    saved.exceptionOrNull()?.let {
      //  [quiet]: نوشتنِ پس‌زمینه (CRM-10 sweep) — کاربر چیزی ثبت نکرده که «ذخیره نشد» بشنود
      if (!quiet) _diskError.tryEmit(it.message ?: "خطای نامشخص")
      return false
    }
    _unreadable.value = null
    _data.value = next
    //  خلاصهٔ کوچک، کنارِ دفتر. نگهبانِ پس‌زمینه همین را می‌خواند و دیگر
    //  لازم نیست هر ربع ساعت کلِ دفتر را تجزیه کند — شرحش سرِ
    //  `LedgerSummary`.
    LedgerSummary.write(context, next)
    return true
  }

  /**
   *  خواندنِ فایلِ پشتیبان — بدونِ نوشتن.
   *
   *  جدا از `importJson` است تا بشود پیش از جایگزین کردنِ دفتر، نشان داد
   *  داخلِ فایل چه چیزی هست. بازیابی کاری است که برنمی‌گردد؛ کاربر باید
   *  ببیند چه چیزی جای چه چیزی می‌نشیند.
   *
   *  فایلی که JSON درستی باشد ولی مالِ این برنامه نباشد، همه‌جا خالی
   *  می‌خواند و بی‌صدا دفتر را پاک می‌کند. برای همین یک بررسیِ ساده هست:
   *  فایلِ پشتیبانِ واقعی دستِ‌کم یکی از فهرست‌هایش پر است، یا صریحاً
   *  مُهرِ `exportedAt` دارد.
   */
  fun parseBackup(raw: String): Result<ShopData> = runCatching {
    val tree = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
      ?: error("فایل پشتیبان معتبر نیست")
    val parsed = json.decodeFromJsonElement(ShopData.serializer(), tree)
    val stamped = tree.containsKey("exportedAt")
    if (!stamped && backupSize(parsed) == 0) error("این فایل، پشتیبانِ فروشگاه من نیست")
    parsed
  }

  /** واردکردنِ داده‌ای که از نسخهٔ وب می‌آید */
  /**
   *  واردکردنِ داده‌ای که از نسخهٔ وب می‌آید.
   *
   *  ⚠️ قفلِ نرم جلوی این را نمی‌گیرد: بازیابیِ پشتیبان همان دادهٔ خودِ
   *  کاربر است و بستنش یعنی گروگان گرفتنِ دفترِ خودش.
   */
  suspend fun importJson(raw: String): Result<ShopData> = withContext(Dispatchers.IO) {
    parseBackup(raw).mapCatching {
      if (!applyRestore(it)) throw java.io.IOException("دفتر روی دیسک نوشته نشد")
      it
    }
  }

  /* -------------------------- پشتیبانِ ایمنی -------------------------- */

  private val safety = File(context.filesDir, "before-restore.json")

  /**
   *  یک نسخه از دفترِ فعلی، پیش از بازیابی.
   *
   *  اگر کاربر فایلِ اشتباهی را بازیابی کند، کارِ چند ماهش رفته است. این
   *  نسخه همان‌جا روی گوشی می‌ماند تا یک دکمه بتواند برش گرداند.
   */
  suspend fun keepSafetyCopy() = withContext(Dispatchers.IO) {
    //  دفترِ خوانده‌نشده نسخهٔ ایمنیِ «خالی» نمی‌گیرد (نسخهٔ ایمنیِ درستِ قبلی می‌ماند)
    if (_unreadable.value == null) {
      runCatching { synchronized(AtRest.lock) { AtRest.writeText(context, safety, json.encodeToString(_data.value)) } }
    }
    Unit
  }

  fun hasSafetyCopy(): Boolean = safety.exists()

  /** برگرداندن به لحظهٔ پیش از بازیابی */
  suspend fun undoRestore(): Result<ShopData> = withContext(Dispatchers.IO) {
    runCatching {
      val parsed = json.decodeFromString<ShopData>(AtRest.readText(context, safety))
      //  ⛔ نسخهٔ ایمنی فقط وقتی پاک می‌شود که دفتر واقعاً روی دیسک نشسته باشد
      if (!applyRestore(parsed)) throw java.io.IOException("دفتر روی دیسک نوشته نشد")
      safety.delete()
      parsed
    }
  }

  fun dropSafetyCopy() {
    runCatching { safety.delete() }
  }

  fun hasData(): Boolean = file.exists()

  /* ------------------------ دفتر، به نامِ حساب ------------------------ */

  /*
   *  چرا دفتر باید اسمِ صاحبش را داشته باشد.
   *
   *  تا دیروز روی یک گوشی فقط یک فایل بود: `shop-data.json`. خروج از حساب
   *  توکن را پاک می‌کرد ولی این فایل سرِ جایش می‌ماند. نفرِ بعدی که روی
   *  همان گوشی وارد می‌شد، «سایه» خالی داشت — یعنی همگام‌سازی همهٔ ردیف‌های
   *  نفرِ قبلی را «تغییرِ تازه» می‌دید و صاف می‌فرستاد داخلِ دکانِ او.
   *
   *  یک گوشیِ مشترک در دکان، یا فروختنِ گوشی، یا حتی امتحان کردنِ دو حساب
   *  کافی بود.
   *
   *  حالا دفترِ هر حساب زیرِ نامِ خودش بایگانی می‌شود. عوض شدنِ حساب یعنی
   *  دفترِ قبلی می‌رود کنار و دفترِ همین حساب باز می‌شود؛ هیچ ردیفی از یکی
   *  به دیگری نشت نمی‌کند و هیچ‌کدام هم پاک نمی‌شود.
   */

  private fun vault(key: String) = File(context.filesDir, "ledger-${safeKey(key)}.json")

  /** بایگانی کردنِ دفترِ فعلی زیرِ نامِ حساب، و خالی کردنِ دفترِ روی میز */
  suspend fun stashTo(key: String) = withContext(Dispatchers.IO) {
    //  زیرِ قفلِ نوشتنِ دفتر و قفلِ کوچِ رمز (`AtRest.converge`): کوچ میانِ کپی و پاک کردن نمی‌نشیند
    runCatching {
      synchronized(SyncV1Engine.of(context)) {
        synchronized(AtRest.lock) {
          if (file.exists()) file.copyTo(vault(key), overwrite = true)
          file.delete()
        }
      }
    }
    LedgerSummary.clear(context)
    _data.value = ShopData()
    _unreadable.value = null
    Unit
  }

  /** باز کردنِ دفترِ یک حساب — اگر بایگانی نداشت، دفترِ خالی */
  suspend fun openFrom(key: String) {
    withContext(Dispatchers.IO) {
      val saved = vault(key)
      runCatching {
        synchronized(SyncV1Engine.of(context)) {
          synchronized(AtRest.lock) { if (saved.exists()) saved.copyTo(file, overwrite = true) else file.delete() }
        }
      }
    }
    _loaded.value = false
    _data.value = ShopData()
    //  خلاصهٔ حسابِ قبلی نباید روی حسابِ تازه بماند؛ با خواندنِ دفتر از نو
    //  ساخته می‌شود
    LedgerSummary.clear(context)
    loadAndSummarize()
  }

  /** نامِ فایل نباید از کاراکترهای شناسه آسیب ببیند */
  private fun safeKey(key: String): String =
    key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(48).ifBlank { "anon" }

  /**
   * فایلِ پشتیبان.
   *
   * همان قالبی که نسخهٔ وب می‌سازد و می‌خواند — به‌علاوهٔ `exportedAt`.
   * پس پشتیبانی که اینجا گرفته می‌شود در آنجا باز می‌شود و برعکس؛ کسی که
   * بین دو نسخه جابه‌جا می‌شود دفترش را دوباره نمی‌سازد.
   */
  suspend fun exportBackup(storeName: String = ""): String = withContext(Dispatchers.IO) {
    val tree = json.encodeToJsonElement(ShopData.serializer(), _data.value)
      .let { it as kotlinx.serialization.json.JsonObject }
      .toMutableMap()
    tree["exportedAt"] = kotlinx.serialization.json.JsonPrimitive(
      java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date())
    )
    tree["settings"] = kotlinx.serialization.json.buildJsonObject {
      put("storeName", kotlinx.serialization.json.JsonPrimitive(storeName))
    }
    kotlinx.serialization.json.JsonObject(tree).toString()
  }

  companion object {
    /** پیامِ نوشتنی که به‌خاطرِ دفترِ رمزدارِ خوانده‌نشده رد شد */
    const val UNREADABLE =
      "دفترِ رمزدارِ این دستگاه باز نشد، پس این تغییر ثبت نشد تا دفترِ واقعی رونویسی نشود. از «تنظیمات ← امنیت» دوباره امتحان یا «بازسازی از سرور» را بزنید."

    const val NEED_SIGN_IN =
      "برای بازسازی از سرور اول وارد حساب شوید؛ یا از «داده‌ها» یک پشتیبان را بازیابی کنید. هیچ چیزی پاک نشده."

    const val NEED_SIGN_IN_SYNCED =
      "این دفتر پیش‌تر با سرور همگام شده؛ برای بازیابیِ پشتیبان اول وارد حساب شوید تا دفتر از سرور ساخته شود و ردیف‌های حذف‌شده زنده نشوند. هیچ چیزی پاک نشده."

    const val NEED_NET =
      "برای بازسازی از سرور اینترنت لازم است؛ دفترِ خوانده‌نشده با پشتیبانِ کهنه جایگزین نمی‌شود تا ردیف‌های تازه‌ترِ سرور حذف نشوند. هیچ چیزی پاک نشده."

    /** چند رکورد در این دفتر هست — برای خلاصهٔ پیش از بازیابی */
    fun backupSize(d: ShopData): Int =
      d.products.size + d.sales.size + d.debtors.size + d.expenses.size +
        d.suppliers.size + d.purchases.size + d.warehouseEntries.size + d.transactions.size

    /* ------------------------- محاسبه‌ها ------------------------- */

    /**
     *  جدولِ موجودی — یک بار ساخته می‌شود، بارها خوانده.
     *
     *  این کلاس یک باگِ کندیِ واقعی را می‌بندد. حسابِ قبلی برای **هر
     *  کالا** کلِ اقلامِ فروش را می‌گشت، و برای **هر قلم** هم کلِ
     *  فاکتورها را دنبالِ وضعیتِ «لغو» می‌گردید. یعنی هزینه‌اش ضربِ سه
     *  عدد بود: کالاها × اقلامِ فروش × فاکتورها.
     *
     *  صفحهٔ محصولات این را برای همهٔ کالاها صدا می‌زد و با هر حرفی که
     *  در جستجو زده می‌شد از نو. دکانی با چند صد کالا و چند هزار فاکتور،
     *  همان‌جا می‌ایستاد.
     *
     *  حالا یک بار روی هر سه فهرست رد می‌شویم و سه نگاشت می‌سازیم؛ بعد
     *  هر پرسش یک خواندن است. نتیجه‌ها مو‌به‌مو همان‌اند — فقط راهش عوض
     *  شده.
     */
    class StockIndex(d: ShopData) {
      private val entered = HashMap<String, Double>()
      private val cartonsIn = HashMap<String, Double>()
      private val sold = HashMap<String, Double>()

      init {
        d.warehouseEntries.forEach { e ->
          entered[e.productId] = (entered[e.productId] ?: 0.0) + e.units
          cartonsIn[e.productId] = (cartonsIn[e.productId] ?: 0.0) + e.cartons
        }
        // وضعیتِ فاکتورها یک بار در یک نگاشت می‌نشیند تا برای هر قلم
        // دوباره جستجو نشود
        val cancelled = HashSet<String>()
        d.sales.forEach { if (it.status == "cancelled") cancelled += it.id }
        d.saleItems.forEach { item ->
          if (item.saleId in cancelled) return@forEach
          sold[item.productId] =
            (sold[item.productId] ?: 0.0) + (item.quantity - item.returnedQty)
        }
      }

      fun soldQty(productId: String): Double = sold[productId] ?: 0.0

      fun stock(productId: String): Double =
        (entered[productId] ?: 0.0) - (sold[productId] ?: 0.0)

      fun cartons(productId: String): Double = cartonsIn[productId] ?: 0.0

      fun status(product: Product): String {
        val s = stock(product.id)
        return when {
          s <= 0 -> "out"
          s <= product.minStock -> "low"
          else -> "ok"
        }
      }
    }

    /*
     *  آخرین جدولِ ساخته‌شده، با همان دفتری که از رویش ساخته شد.
     *
     *  `ShopData` تغییرناپذیر است، پس اگر همان شیء باشد جدول هم هنوز
     *  درست است. یک خانه بس است: در هر لحظه یک دفتر بیشتر روی صفحه
     *  نیست.
     */
    private var indexedData: ShopData? = null
    private var indexed: StockIndex? = null

    @Synchronized
    fun index(d: ShopData): StockIndex {
      val ready = indexed
      if (ready != null && indexedData === d) return ready
      val fresh = StockIndex(d)
      indexedData = d
      indexed = fresh
      return fresh
    }

    /**
     *  جدولِ فروش — همان کارِ `StockIndex`، برای سمتِ فاکتورها.
     *
     *  ── چه اشکالی را می‌بندد ──────────────────────────────────────
     *  `ReportEngine` سه جای پیاپی همان اشتباهی را می‌کرد که یک بار در
     *  `StockIndex` درست شده بود:
     *
     *   • گزارشِ بازه، برای **هر فاکتور** کلِ اقلامِ فروش را می‌گشت.
     *   • گزارشِ سودِ محصولات، برای **هر کالا** کلِ اقلام را می‌گشت و
     *     برای هر قلمِ پیداشده یک بار هم کلِ فاکتورها را — یعنی ضربِ سه
     *     عدد: کالاها × اقلام × فاکتورها.
     *   • مرجوعی‌ها هم برای هر ردیف، فاکتور و قلمش را جستجو می‌کردند.
     *
     *  دکانی با سیصد کالا و بیست هزار قلمِ فروش، سرِ باز کردنِ تبِ
     *  «محصولات» گزارشات می‌ایستاد.
     *
     *  حالا یک بار روی دو فهرست رد می‌شویم و چهار نگاشت می‌سازیم؛ بعد
     *  هر پرسش یک خواندن است. نتیجه‌ها مو‌به‌مو همان‌اند.
     *  ──────────────────────────────────────────────────────────────
     */
    class SalesIndex(d: ShopData) {

      /** فاکتور از روی شناسه */
      val saleById: Map<String, Sale> = d.sales.associateBy { it.id }

      /** اقلامِ هر فاکتور */
      val itemsBySale: Map<String, List<SaleItem>> = d.saleItems.groupBy { it.saleId }

      /** قلمِ فروش از روی شناسه — برای ردیف‌های مرجوعی */
      val itemById: Map<String, SaleItem> = d.saleItems.associateBy { it.id }

      /** تعدادِ خالص و سودِ یک کالا، از آغاز تا حالا */
      data class Sold(val quantity: Double, val profit: Double)

      //  [۰] = تعداد، [۱] = سود. یک آرایه به‌جای دو نگاشت، چون این جدول
      //  ممکن است برای چند صد کالا ساخته شود.
      private val perProduct = HashMap<String, DoubleArray>()

      init {
        /*
         *  فاکتورِ لغوشده اصلاً شمرده نمی‌شود. قلمی که فاکتورش در دفتر
         *  نیست (ردیفِ یتیم از یک دفترِ قدیمی) عمداً شمرده **می‌شود** —
         *  دقیقاً همان کاری که حسابِ قبلی می‌کرد، تا عددِ گزارش با
         *  به‌روزرسانی عوض نشود.
         */
        val cancelled = HashSet<String>()
        d.sales.forEach { if (it.status == "cancelled") cancelled += it.id }
        d.saleItems.forEach { item ->
          if (item.saleId in cancelled) return@forEach
          val net = item.quantity - item.returnedQty
          val slot = perProduct.getOrPut(item.productId) { DoubleArray(2) }
          slot[0] += net
          slot[1] += net * (item.unitPrice - item.purchasePrice)
        }
      }

      fun product(productId: String): Sold {
        val slot = perProduct[productId] ?: return Sold(0.0, 0.0)
        return Sold(slot[0], slot[1])
      }
    }

    private var salesIndexedData: ShopData? = null
    private var salesIndexed: SalesIndex? = null

    /** جدولِ فروش، با همان قاعدهٔ یک‌خانه‌ایِ `index` */
    @Synchronized
    fun salesIndex(d: ShopData): SalesIndex {
      val ready = salesIndexed
      if (ready != null && salesIndexedData === d) return ready
      val fresh = SalesIndex(d)
      salesIndexedData = d
      salesIndexed = fresh
      return fresh
    }

    /**
     * مقدارِ فروخته‌شدهٔ یک کالا.
     * فروشِ لغوشده و مقدارِ مرجوعی حساب نمی‌شوند — عیناً مثل نسخهٔ وب.
     */
    fun soldQty(d: ShopData, productId: String): Double = index(d).soldQty(productId)

    /** موجودی = آنچه وارد انبار شده، منهای آنچه فروخته شده */
    fun stock(d: ShopData, productId: String): Double = index(d).stock(productId)

    fun cartons(d: ShopData, productId: String): Double = index(d).cartons(productId)

    /** تمام‌شده | موجودی کم | موجودی کافی */
    fun stockStatus(d: ShopData, product: Product): String = index(d).status(product)

    /** بدهیِ یک قرض‌دار: آنچه گرفته منهای آنچه پس داده — با `shop.debt_minor` به پول (`DebtMinorFlag`)، خاموش همان جمعِ Double */
    fun debt(d: ShopData, debtorId: String): Double = DebtMinorFlag.balance(d.transactions.filter { it.debtorId == debtorId })

    /**
     *  بدهیِ ما به یک تأمین‌کننده.
     *
     *  عمداً از `totalAmount − paidAmount` حساب می‌شود، نه از فیلدِ `debt`
     *  که روی خودِ خرید نوشته شده — همان کاری که نسخهٔ وب می‌کند.
     *
     *  دلیلش یک اشکالِ واقعی است: فایلِ پشتیبانِ نسخه‌های قدیمی فیلدِ
     *  `debt` را ندارد. آن‌وقت این‌طرف صفر خوانده می‌شد و بدهیِ
     *  تأمین‌کننده صفر یا حتی منفی درمی‌آمد، در حالی که نسخهٔ وب همان
     *  فایل را درست می‌خواند. این فرمول از خودِ دو عددِ اصلی حساب می‌کند،
     *  پس فایلِ ناقص هم درست خوانده می‌شود.
     */
    fun supplierDebt(d: ShopData, supplierId: String): Double {
      val mine = d.purchases.filter { it.supplierId == supplierId }
      val billed = mine.sumOf { it.totalAmount }
      val paid = mine.sumOf { it.paidAmount } +
        d.supplierPayments.filter { it.supplierId == supplierId }.sumOf { it.amount }
      return billed - paid
    }

    fun barcodeIndex(d: ShopData): Map<String, String> = buildMap {
      d.products.forEach { p -> p.barcodes.forEach { code -> put(code, p.id) } }
    }
  }
}
