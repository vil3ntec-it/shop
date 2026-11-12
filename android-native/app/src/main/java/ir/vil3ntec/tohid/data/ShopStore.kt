package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrAppData
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
import ir.vil3ntec.tohid.core.backup.RestoreRoute
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.atrest.AtRestMigration
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncRebuild
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
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
class ShopStore(
  private val context: Context,
  /**
   *  OPS-07 — دفترِ حالتِ آموزشی (`TrainingLedger`): فایلِ جدای `training/shop-data.json` (و کنارش همان‌جا)،
   *  و هرگز op، صفِ همگام‌سازی، کارگر، خلاصهٔ پس‌زمینه یا sweep. دفترِ واقعی (`false`) در حالتِ آموزشی هیچ
   *  نوشتنی نمی‌گیرد ([syncs]، `writeToDisk`). شرح سرِ `TrainingMode`.
   */
  val training: Boolean = false,
) {
  init { DebtMinorFlag.bind(context) }  //  `shop.debt_minor`: `debt` و موتورهای خالص پرچمِ همین دکان را از `DebtMinorFlag` می‌خوانند
  init { LoyaltyRules.bind(context) }  //  PRM-13: برگشت و لغو انقضای قاعدهٔ در کار (یکسانِ دکان یا همین دستگاه) را از `LoyaltyRules` می‌خوانند

  private val home = if (training) File(context.filesDir, TrainingMode.DIR).apply { mkdirs() } else context.filesDir
  private val file = File(home, TrainingMode.LEDGER)

  /** OPS-07: این نمونه به موتورِ همگام‌سازی و دفترِ واقعی دست می‌زند؟ دفترِ آموزشی هرگز؛ دفترِ واقعی نه در حالتِ آموزشی */
  private val syncs: Boolean get() = !training && !TrainingMode.on
  private val json = Json {
    ignoreUnknownKeys = true      // نسخهٔ وب ممکن است فیلدی اضافه کند
    encodeDefaults = true
    isLenient = true
  }

  private val _data = MutableStateFlow(ShopData())
  val data: StateFlow<ShopData> = _data.asStateFlow()
  @Volatile private var extras: LedgerExtras? = null   //  DATA-SAFETY §۷: کلیدهای نسخهٔ تازه‌تر، فقط [localOnly] (شرح سرِ `LedgerExtras`)
  private fun localOnly() = training || runCatching { !SyncChoice.isV3(context) && !Backend.isReady(context) && !SyncV1Engine.of(context).everSynced() }.getOrDefault(false)

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
      //  نشانِ دیسک **پیش از** خواندن — نوشتنی که میانِ خواندن برسد پایین دیده می‌شود
      val mark = follow.mark()
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
        //  SEC-01: دفترِ ساده همان‌طور، رمزدار با کلیدِ همین دستگاه. ⛔ DATA-SAFETY: خواندنِ ناموفق یا JSON ِ خراب «خالی» نیست (`LedgerRecovery`)
        val read = try { LedgerRecovery.read(file, json, { AtRest.readText(context, it) }) } catch (e: AtRestException) { _unreadable.value = e.kind; null }
        if (read is LedgerRecovery.Read.Ok) { extras = read.extras?.takeIf { localOnly() }; _unreadable.value = null; guard.clear(); follow.replace(read.data); recoverSync(read.data) }
        if (read is LedgerRecovery.Read.Bad) damaged(read.damage)
      } else {
        extras = null; _unreadable.value = null; guard.clear()
        recoverSync(ShopData())
      }
      //  از این‌جا نوشتنِ همتاها گرفته می‌شود؛ همتایی که میانِ خواندن نوشت ⇒ همین حالا از نو.
      //  پرچمِ خاموش ⇒ هیچ خواندنِ دوباره، پس بی قفلِ موتور (همان `main`)
      if (follow.on) synchronized(SyncV1Engine.of(context)) { follow.loadedLocked(mark) } else follow.loadedLocked(mark)
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

  /** DATA-SAFETY: دفترِ **ساده**ای که خوانده نشد — گونهٔ خرابی، نسخهٔ سالم و پیامِ برگشت (شرح سرِ `LedgerRecovery.Guard`) */
  val guard = LedgerRecovery.Guard()
  private fun damaged(damage: LedgerRecovery.Damage) {
    _unreadable.value = LedgerRecovery.kindOf(damage)
    guard.damaged(damage, { LedgerRecovery.near(context, file, json, snapshots = !training) }, { syncs && LedgerRecovery.autoRecoverable(context) }) { SyncV1Engine.of(context).resetTo(it.data, staging); recoverSync(it.data) }
  }
  /** پنجرهٔ دفترِ خراب: برگرداندن از `guard.recovery` از راهِ امنِ [rebuildFromServer] (بی op؛ فایلِ خراب کنار می‌رود)؛ [retryRead] همان `load` */
  suspend fun recoverFromCopy(): Rebuilt = rebuildFromServer(guard.recovery.value?.data).also { if (it.ok) guard.clear() }
  suspend fun retryRead(): Boolean { load(); return _unreadable.value == null }

  /* ------- چند نمونه روی یک دفتر: صفحه و کارگرِ پس‌زمینه — شرح سرِ `LedgerFollow` ------- */

  private val follow = LedgerFollow(
    file, _data,
    unreadable = { _unreadable.value != null },
    readDisk = { runCatching { json.decodeFromString<ShopData>(AtRest.readText(context, file)) }.getOrNull() },
    enabled = { LedgerMergeFlag.enabled(context) },
  )

  /** [LedgerFollow.freshLocked] برای نوشتنِ صفحه؛ `null` با پیامِ روشن */
  private fun currentForWrite(): ShopData? =
    follow.freshLocked() ?: run { _diskError.tryEmit(STALE_UNREADABLE); null }

  /**
   *  دفترِ همین لحظهٔ دیسک — `read` ِ موتورهای همگام‌سازی (`SyncV1Worker`، `SyncV3Runner`)، زیرِ
   *  همان قفلِ نوشتن. ⛔ نشاندنِ opهای سرور روی دفترِ کهنهٔ این نمونه فروشی را که صفحه در این
   *  فاصله ثبت کرده بود از دیسک پاک می‌کرد.
   *
   *  پرچمِ `shop.ledger_merge` خاموش ⇒ همان `data.value` ِ همیشه، بی قفل (همان `main`).
   *
   *  @throws StaleLedger دیسک جلوتر است و خوانده نشد — هیچ چیزی نشانده نمی‌شود
   */
  fun ledgerNow(): ShopData {
    if (!follow.on) return _data.value
    return synchronized(SyncV1Engine.of(context)) { follow.freshLocked() ?: throw StaleLedger(STALE_UNREADABLE) }
  }

  /** دیسکِ جلوترِ خوانده‌نشده یا دفترِ حسابِ دیگر ([ledgerNow]، [applyFromServerNow]) — فقط با پرچمِ روشن */
  class StaleLedger(message: String) : java.io.IOException(message)

  /**
   *  هم‌سو کردنِ فایل‌های دفتر با تنظیمِ «رمزگذاریِ داده» — زیرِ همان قفلِ
   *  نوشتنِ دفتر (`AtRest.converge`). با هر بازشدن و با هر تغییرِ تنظیم.
   */
  suspend fun convergeAtRest(): AtRestMigration.Report = withContext(Dispatchers.IO) {
    if (!syncs) return@withContext AtRestMigration.Report()
    runCatching { AtRest.converge(context, SyncV1Engine.of(context)) }
      .getOrElse { AtRestMigration.Report(failed = listOf("*" to AtRestException.Kind.IO)) }
  }

  /**
   *  commitِ همگام‌سازی‌ای که برنامه وسطش بسته شد، پیش از هر نوشتنی تمام یا
   *  برگردانده می‌شود — با همین دفترِ روی دیسک (F0-05، شرح سرِ `SyncCommit`).
   *  ⚠️ خواندنِ دفتر هرگز به‌خاطرِ این نمی‌ایستد.
   */
  private fun recoverSync(onDisk: ShopData) {
    if (!syncs) return
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
    if (_unreadable.value == null && !training) withContext(Dispatchers.IO) { LedgerSummary.write(context, _data.value) }
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
    if (!syncs) return@withContext null
    if (!ShopPolicy.crmReports(context)) return@withContext null
    if (SyncChoice.isV3(context)) return@withContext null
    val engine = SyncV1Engine.of(context)
    synchronized(engine) {
      if ((requireLoaded && !_loaded.value) || _unreadable.value != null || engine.rebuilding) return@synchronized null
      //  نمونه‌ای که پیش از عوضِ حساب بار شده روی دفترِ حسابِ تازه نمی‌نویسد (`LedgerFollow.movedLocked`)
      if (!follow.sameAccount()) return@synchronized null
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
   *  ذخیرهٔ دفتر — تنها درِ نوشتنِ صفحه‌ها و جریان‌ها. [base] **پایهٔ صریح** است: دفتری که [next] از آن ساخته شد (همان
   *  `data`/`d` ِ موتور، یک بار خوانده؛ `docs/DATA-SAFETY-fa.md` «دو ذخیره از یک صفحهٔ کهنه»). دفترِ همین حالا همان پایه ⇒
   *  خودِ [next] (رفتارِ همیشه)؛ جلوتر (Pull ِ کارگر، ذخیرهٔ دیگری از همان دفترِ کهنه، [appendAudit]/[update] ِ همین نمونه)
   *  ⇒ فقط تغییرِ صفحه روی آن (`LedgerMerge`)، با `shop.ledger_merge` روشن یا خاموش (خاموش ⇒ فقط دفترِ حافظهٔ همین نمونه).
   *  ⛔ پایه نه حدس زده می‌شود و نه همان لحظه از `data.value` خوانده (`SaveBaseGuardTest`). سدِ نقش (A16) و قفلِ نرم تغییرِ
   *  خودِ صفحه را در هر دو حالت می‌سنجند. `shop.save_base` = `false` ِ صریح (`LedgerMergeFlag.base`) ⇒ [saveGuessed].
   *  ⛔ با اشتراکِ تمام‌شده نمی‌نویسد (قفلِ نرمِ بندِ ۲۱٫۸): هیچ چیزی پاک نمی‌شود؛ خروجی، چاپ و پشتیبان باز می‌مانند.
   *  @return آیا نوشته شد؛ `false` ⇒ صفحه «ثبت شد» نگوید (دلیل از `blocked`، `roleBlocked` یا `diskError`).
   */
  suspend fun save(base: ShopData, next: ShopData): Boolean = withContext(Dispatchers.IO) {
    if (!LedgerMergeFlag.base(context)) return@withContext saveGuessed(next)
    !roleRefused(base, next) && !softLocked(base, next) && writeOnBase(base, next)
  }

  /** «پاک کردنِ همه»: جایگزینیِ کامل، بی پایه (⛔ از مقدارِ [next] حدس زده نمی‌شود)؛ سدِ نقش و قفلِ نرم همان */
  suspend fun replaceAll(next: ShopData): Boolean = withContext(Dispatchers.IO) {
    extras = null; !roleRefused(_data.value, next) && !softLocked(_data.value, next) && writeInternal(next)
  }

  /**
   *  راهِ پیشین: پایه از شیءهای مشترک حدس زده می‌شد (`LedgerFollow.baseOf`)؛ `shop.ledger_merge` خاموش ⇒ دفترِ صفحه کامل
   *  و بی سدِ نقش. ⛔ فقط خاموش‌کنِ `shop.save_base` و آزمون‌های پیشین (پسوندِ آزمونیِ `save(next)`)، هرگز صفحه‌ای.
   */
  internal suspend fun saveGuessed(next: ShopData, replace: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    //  [replace] («پاک کردنِ همه») هم: جایگزینیِ کامل به خواستِ کاربر، نه ویرایشی روی پایه
    if (!follow.on || replace) return@withContext !softLocked(_data.value, next) && writeInternal(next, base = if (replace) null else _data.value)
    //  ⛔ پایه دیگر نگه داشته نشده: ادغام با دفترِ همین حالا ردیف‌های تازه را DELETE می‌کرد
    val base = follow.baseOf(next) ?: run { _notice.tryEmit(STALE_BASE); return@withContext false }
    !roleRefused(base, next) && !softLocked(base, next) && writeOnBase(base, next)
  }

  /** A16: نقشی که این بخش را نمی‌نویسد ⇐ پیش از دیسک و op (سرور هم رد می‌کرد) — تغییرِ خودِ صفحه، نه دفترِ همین حالا */
  private fun roleRefused(base: ShopData, next: ShopData): Boolean {
    val role = ShopRole.current(context)
    val table = RoleGate.blocked(role, base, next) ?: return false
    _roleBlocked.tryEmit(RoleGate.tableTitle(table) to ir.vil3ntec.tohid.core.perm.RoleInfo.title(role))
    return true
  }

  /** ⛔ قفلِ نرم فقط فروش و قرض‌داران را می‌بندد (انبار، کالا، خرید و مصارف «همیشه باز») — تغییرِ خودِ صفحه */
  private fun softLocked(base: ShopData, next: ShopData): Boolean {
    if (!touchesPaid(base, next) || !SoftLock.isLocked(context)) return false
    SoftLock.noteBlocked()
    _blocked.tryEmit(Unit)
    return true
  }

  /** زیرِ قفلِ موتور: [next] روی دفترِ همین حالا با [base] ِ داده‌شده */
  private fun writeOnBase(base: ShopData, next: ShopData): Boolean = SyncV1Engine.of(context).let { engine ->
    synchronized(engine) {
      val theirs = currentForWrite() ?: return@synchronized false
      //  دفترِ همین حالا همان است که صفحه با آن ساخت — رفتارِ همیشه، بی ادغام (جز مُهرِ سابقه، STF-09)
      if (theirs === base) {
        val plan = auditSeal(theirs, base, next)
        if (plan.data === next) return@synchronized writeLocked(engine, next).also { if (it) plan.commit() }
        follow.remember(theirs)
        return@synchronized writeLocked(engine, plan.data).also { if (it) { plan.commit(); follow.submitted(next) } }
      }
      //  ⛔ جلوتر است: فقط تغییرِ صفحه نسبت به پایه — نوشتنِ کاملِ دفترِ کهنه DELETE و برگرداندن می‌ساخت
      val outcome = runCatching { LedgerMerge.mergeReport(base, next, theirs) }.getOrElse {
        _diskError.tryEmit(it.message ?: STALE_UNREADABLE)
        return@synchronized false
      }
      //  STF-09: ردیف‌های تازهٔ صفحه (نه آنچه از [theirs] آمد) مُهر و حلقه می‌خورند
      val plan = auditSeal(theirs, base, outcome.data)
      val merged = plan.data
      //  نگه‌داشتِ راهِ پیشین ([saveGuessed]): دفترِ پیش از ادغام، و [next] فقط با نشانهٔ یک‌سویه (`LedgerFollow.submitted`)
      if (merged !== next) follow.remember(theirs)
      val ok = writeLocked(engine, merged).also { if (it) plan.commit(); if (it && merged !== next) follow.submitted(next) }
      //  ⛔ ویرایشی که روی ردیفِ حذف‌شده در دستگاهِ دیگر ننشست بی‌صدا گم نمی‌شود
      if (ok && outcome.dropped.isNotEmpty()) _notice.tryEmit(editsDropped(outcome.dropped.size))
      ok
    }
  }

  /**
   *  نوشتنی که قفلِ نرم ردش کرد — `AppRoot` پیامِ روشن نشان می‌دهد، تا کسی
   *  فکر نکند فروشش ثبت شده (گزارشِ ممیزی: صفحه‌ها «ثبت شد» می‌گفتند).
   */
  private val _blocked = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val blocked: kotlinx.coroutines.flow.SharedFlow<Unit> = _blocked

  /** نوشتنی که سدِ نقش (A16) ردش کرد: (بخشِ دفتر، نامِ نقش) — `AppRoot` راستش را می‌گوید */
  private val _roleBlocked = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)
  val roleBlocked: kotlinx.coroutines.flow.SharedFlow<Pair<String, String>> = _roleBlocked

  private fun touchesPaid(cur: ShopData, next: ShopData): Boolean =
    cur.sales != next.sales || cur.saleItems != next.saleItems ||
      cur.saleReturns != next.saleReturns || cur.transactions != next.transactions || cur.debtors != next.debtors

  /** همان [restoreBackup] با پیامش در [notice] — برای جاهایی که خودشان پیام ندارند (وارد کردن، برگرداندن) */
  suspend fun applyRestore(next: ShopData): Boolean =
    restoreBackup(next).also { if (it.message.isNotEmpty()) _notice.tryEmit(it.message) }.ok

  /**
   *  بازیابیِ پشتیبان — بیرونِ قفلِ نرم (دادهٔ خودِ کاربر است؛ گروگان گرفتنش یعنی از دست دادنِ اعتماد).
   *
   *  ⛔ S143 (#455): **بازیابی هرگز op نمی‌سازد** — دیگر «ذخیره»ٔ عادی نیست که تفاضلش با سایه برای
   *  ردیف‌های تازه‌ترِ سرور DELETE و برگرداندن بفرستد. راه از `RestoreRoute.decide` (core، شرح همان‌جا):
   *  هرگز همگام‌نشده و (بی ورود یا حسابِ سرورِ خالی) ⇒ پشتیبان جای دفتر و خودش سایه (`resetTo`)؛ واردشده و
   *  سرور ردیف دارد ⇒ همان راهِ «از سرور»، Snapshot نیامد ⇒ «اول همگام کنید» (#485)؛ همگام ⇒ صف اول، بعد ردیف‌ها فقط از
   *  سرور و از پشتیبان فقط تنظیمات ([pushedStaging])؛ بی ورود/شبکه یا v3 ⇒ هیچ، با دلیل. دفترِ
   *  خوانده‌نشده همان «بازسازی از سرور» (SEC-01).
   *  @return `message` ِ خالی یعنی نوشته شد و حرفی نیست
   */
  suspend fun restoreBackup(next: ShopData): Rebuilt = withContext(Dispatchers.IO) {
    if (TrainingMode.on) return@withContext Rebuilt(false, TRAINING_SEALED)
    extras = null; if (_unreadable.value != null) return@withContext rebuildFromServer(next)
    val engine = SyncV1Engine.of(context)
    engine.rebind()
    val v3 = SyncChoice.isV3(context)
    val synced = if (v3) SyncV3Runner.of(context).initialized() else engine.everSynced()
    val signedIn = Backend.isReady(context)
    val online = Backend.isOnline(context)
    //  #485: واردشده ولی هرگز همگام‌نشده ⇒ سرور چه دارد؟ ردیف دارد ⇒ از سرور؛ نیامد ⇒ «اول همگام کنید»
    val serverRows = if (!synced && signedIn && online && !v3) serverRowCount(engine) else null
    when (RestoreRoute.decide(synced, v3, signedIn, online, serverRows)) {
      RestoreRoute.Route.LOCAL -> runCatching { engine.resetTo(next, staging) }
        .fold({ Rebuilt(true, "") }, { sealHalfRestore(); Rebuilt(false, StrSettings.restoreDiskFail) })
      RestoreRoute.Route.SYNC_FIRST -> Rebuilt(false, StrSettings.restoreSyncFirst)
      RestoreRoute.Route.NEED_SIGN_IN -> Rebuilt(false, NEED_SIGN_IN_SYNCED)
      RestoreRoute.Route.NEED_NET -> Rebuilt(false, StrSettings.restoreNeedNet)
      RestoreRoute.Route.NOT_ON_V3 -> Rebuilt(false, StrSettings.restoreNotOnV3)
      RestoreRoute.Route.FROM_SERVER -> try {
        when (val out = engine.rebuildFromServer(next, pushedStaging)) {
          is SyncRebuild.Outcome.QueueLeft -> Rebuilt(false, StrSettings.restoreQueueLeft(out.count))
          is SyncRebuild.Outcome.Done -> Rebuilt(
            true,
            StrSettings.restoredFromServer(out.rows, RestoreRoute.notOnServer(SyncRebuild.rowIds(next), SyncRebuild.rowIds(out.ledger))),
          )
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        sealHalfRestore()
        Rebuilt(false, StrSettings.restoreFailed(e.message ?: e.toString()))
      }
    }
  }

  /** شمارِ ردیف‌های Snapshot ِ سرور برای `RestoreRoute.decide`؛ شبکه یا پاسخِ خراب ⇒ `null` (نمی‌دانیم) */
  private suspend fun serverRowCount(engine: SyncV1Engine): Int? = try {
    engine.serverRows()
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

  /**
   *  بازیابی‌ای که پس از نشستنِ سایه، پیش از جابه‌جایی شکست (دیسک): همین حالا تمام می‌شود، وگرنه نوشتن
   *  بسته می‌ماند — همان کارِ `load`؛ دفترِ پیشین هرگز با سایهٔ تازه تفاضل نمی‌شود.
   */
  private fun sealHalfRestore() {
    val stuck = runCatching { recoverRebuild() }.getOrElse { rebuildMark.exists() && rebuildSide.exists() }
    if (stuck) { _unreadable.value = AtRestException.Kind.IO; _data.value = ShopData() }
  }

  /* ------------- SEC-01: دفترِ رمزدارِ خوانده‌نشده (بازبینیِ #331) ------------- */

  /** نتیجهٔ «بازسازی از سرور» — با پیامِ آمادهٔ صفحه */
  data class Rebuilt(val ok: Boolean, val message: String)

  /**
   *  پیامِ روشنِ کارهایی که از صفحهٔ خودشان بیرون می‌افتند (بازیابی در حالِ دفترِ خوانده‌نشده؛ و با پرچمِ
   *  `shop.ledger_merge`: ذخیره‌ای که پایه‌اش دیگر نگه داشته نشده بود، یا ویرایشی که روی ردیفِ حذف‌شده در
   *  دستگاهِ دیگر ننشست) — `AppRoot` نشانش می‌دهد. با دفترِ ساده و پرچمِ خاموش (پیش‌فرض) هرگز چیزی نمی‌فرستد.
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
    if (TrainingMode.on) return@withContext Rebuilt(false, TRAINING_SEALED)
    if (_unreadable.value == null) return@withContext Rebuilt(false, StrAppData.ledgerOpenNoRebuild)
    if (SyncChoice.isV3(context)) {
      return@withContext Rebuilt(false, StrAppData.rebuildV1Only)
    }
    val engine = SyncV1Engine.of(context)
    engine.rebind()
    if (!Backend.isReady(context)) {
      if (backup == null) return@withContext Rebuilt(false, NEED_SIGN_IN)
      //  ⛔ حسابی که روزی همگام شده: ردیفِ پشتیبان که سرور ندارد شاید حذف‌شده باشد ⇒ اول ورود
      if (engine.everSynced()) return@withContext Rebuilt(false, NEED_SIGN_IN_SYNCED)
      //  هرگز همگام‌نشده: پشتیبان خودش سایه می‌شود ⇐ هیچ op
      return@withContext runCatching { engine.resetTo(backup, staging) }.fold(
        { Rebuilt(true, StrAppData.backupRestored) },
        { Rebuilt(false, StrAppData.restoreFailed(it.message)) },
      )
    }
    if (!Backend.isOnline(context)) return@withContext Rebuilt(false, NEED_NET)
    val out = try {
      engine.rebuildFromServer(backup ?: ShopData(), staging)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      return@withContext Rebuilt(false, StrAppData.rebuildFailed(e.message ?: e.javaClass.simpleName))
    }
    when (out) {
      is SyncRebuild.Outcome.QueueLeft -> Rebuilt(
        false,
        StrAppData.queueLeft(out.count),
      )
      is SyncRebuild.Outcome.Done -> {
        //  کفِ شمارهٔ فاکتور پس از افزودنِ ردیف‌های پشتیبان هم (درونِ `withBackupRows`)
        val merged = backup?.let { SyncRebuild.withBackupRows(out.ledger, it, out.before) } ?: out.ledger
        val done = StrAppData.rebuiltRows(out.rows)
        when {
          backup == null -> Rebuilt(true, done)
          merged == out.ledger -> Rebuilt(
            true,
            done + if (out.before == null) StrAppData.backupRowsNotAdded else "",
          )
          writeInternal(merged) ->
            Rebuilt(true, StrAppData.backupRowsAdded(done, backupSize(merged) - backupSize(out.ledger)))
          else -> Rebuilt(false, StrAppData.backupRowsNotWritten(done))
        }
      }
    }
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
        follow.replace(onDisk!!)
        //  همان دفترِ دیسک، زیرِ قفلِ موتور — این نمونه دوباره همتاها را دنبال می‌کند
        follow.syncedLocked()
        staged = null
        return
      }
      throw java.io.IOException(StrAppData.rebuiltNotSettled)
    }
  }

  /**
   *  [staging] ِ بازیابیِ دستگاهِ همگام (S143). Snapshot پس از فرستادنِ صف و بیرونِ قفلِ نوشتن گرفته
   *  می‌شود؛ `stage` زیرِ همان قفل است: opی که در این فاصله ثبت شد (صف دیگر خالی نیست) ⇒ هیچ چیزی
   *  عوض نمی‌شود — وگرنه آن تغییر از دفترِ همین دستگاه می‌افتاد. از `stage` تا جابه‌جایی هم
   *  [writeLocked] با `engine.rebuilding` هیچ نوشتنی نمی‌پذیرد، تا دفترِ پیشین با سایهٔ تازه تفاضل نشود.
   */
  private val pushedStaging = object : SyncRebuild.Staging by staging {
    override fun stage(next: ShopData, shadowSha: String) {
      check(SyncV1Engine.of(context).queueSize() == 0) { StrSettings.restoreRaced }
      staging.stage(next, shadowSha)
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
    if (runCatching { DiskWrite.move(side, file) }.isFailure) return false else extras = null
    rebuildMark.delete()
    _unreadable.value = null; guard.clear()
    follow.replace(next)
    follow.publishedLocked(next)
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
    if (!syncs) return false
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
      val cur = currentForWrite() ?: return@synchronized false
      val next = transform(cur) ?: return@synchronized null
      if (touchesPaid(cur, next) && SoftLock.isLocked(context)) {
        SoftLock.noteBlocked()
        _blocked.tryEmit(Unit)
        return@synchronized false
      }
      val plan = auditSeal(cur, cur, next)
      writeLocked(engine, plan.data).also { if (it) plan.commit() }
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
   *  CRM-10: تنها درِ ادغامِ دو مشتری — `CustomerMerge.merge` (با `limitRules` ِ CRM-03؛ پیش‌فرض خاموش) روی دفترِ همین لحظه، از همان
   *  [update] (قفلِ نوشتن، قفلِ نرم، op). بسته ([mergeBlockedReason]) ⇒ `Failed` با همان دلیل:
   *  نه نوشتن، نه op.
   */
  suspend fun mergeCustomers(keepId: String, dropId: String, limitRules: ir.vil3ntec.tohid.core.perm.CreditLimitPerm.Rules = ir.vil3ntec.tohid.core.perm.CreditLimitPerm.Rules.OFF): CustomerMerge.Result {
    mergeBlockedReason()?.let { return CustomerMerge.Result.Failed(it) }
    var out: CustomerMerge.Result = CustomerMerge.Result.Failed(StrShell.mergeNotSaved)
    val wrote = update { d ->
      when (val r = CustomerMerge.merge(d, keepId, dropId, limitRules)) {
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
  private suspend fun writeInternal(next: ShopData, base: ShopData? = null): Boolean = withContext(Dispatchers.IO) {
    //  جایگزینیِ کامل (بازیابی، بازسازی): صفحه‌ای که هنوز با دفترِ پیشین می‌سازد، روی همین می‌نشیند
    follow.remember(_data.value).also { follow.replaced() }
    //  STF-09: جایگزینی مُهر نمی‌خورد؛ فقط ذخیرهٔ صفحه روی پایهٔ صریح ([saveGuessed] بی ادغام)
    writer().save(next, base)
  }

  /** STF-09 — مُهر و زنجیرهٔ ردیف‌های تازهٔ سابقه (شرح سرِ `AuditSeal`). دفترِ آموزشی و حالتِ آموزشی هرگز؛ هر شکست ⇐ همان [next] */
  private fun auditSeal(prior: ShopData, base: ShopData, next: ShopData): AuditSeal.Plan =
    if (!syncs) AuditSeal.Plan(next) else runCatching { AuditSealAndroid.plan(context, prior, base, next) }.getOrElse { AuditSeal.Plan(next) }

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

  /** همان [appendAudit] برای چند ردیف در یک نوشتن (RPT-09: ردیف‌های حذف‌شده از فاکتورِ باز) */
  suspend fun appendAudits(rows: (ShopData) -> List<AuditEntry>): Boolean = withContext(Dispatchers.IO) {
    if (!_loaded.value) return@withContext false
    writer().edit(LedgerWriter.appendAudits(rows))
  }

  /**
   *  نوشتن و op ساختن زیرِ همان قفلی که نشاندنِ opهای رسیده دارد
   *  (`SyncV1Engine.absorbRemote`) — شرحش همان‌جا.
   */
  private fun writer(): LedgerWriter {
    val engine = SyncV1Engine.of(context)
    return LedgerWriter(engine, { currentForWrite() }, { next -> writeLocked(engine, next) }) { prior, next -> auditSeal(prior, prior, next) }
  }

  /** بدنهٔ نوشتن؛ فراخوان قفلِ موتور را گرفته است ([LedgerWriter]، و [update] با `synchronized(engine)`) */
  private fun writeLocked(engine: SyncV1Engine, next: ShopData): Boolean {
    //  OPS-07: دفترِ آموزشی فقط به دیسکِ خودش — نه op، نه صفِ همگام‌سازی، نه کارگر
    if (training) return writeToDisk(next)
    //  وسطِ بازیابی/بازسازی از سرور هیچ نوشتنی — شرح سرِ [pushedStaging]
    if (engine.rebuilding) { _diskError.tryEmit(StrSettings.restoreBusy); return false }
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
    //  OPS-07: در حالتِ آموزشی هیچ opِ سروری روی هیچ دفتری نمی‌نشیند (cursor هم جلو نمی‌رود)
    if (!syncs) throw StaleLedger(TRAINING_SEALED)
    //  پرچمِ خاموش ⇒ همان `main`
    if (!follow.on) {
      if (!writeToDisk(next)) throw java.io.IOException(StrAppData.ledgerNotWritten)
      return
    }
    synchronized(SyncV1Engine.of(context)) {
      //  ⛔ دفترِ حسابِ دیگری جای این نشسته (خروج/ورود در میانهٔ همگام‌سازی): opهای حسابِ پیشین
      //  روی آن نمی‌نشینند؛ cursor جلو نمی‌رود و دورِ بعد با کلیدِ درست
      if (!follow.sameAccount()) throw StaleLedger(STALE_UNREADABLE)
      if (!writeToDisk(next)) throw java.io.IOException(StrAppData.ledgerNotWritten)
    }
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
    //  OPS-07: دفترِ واقعی در حالتِ آموزشی دست‌نخورده می‌ماند — تنها درِ دیسک همین‌جاست، پس هیچ راهی دورش نمی‌زند
    if (!TrainingMode.writable(training)) {
      if (!quiet) _diskError.tryEmit(TRAINING_SEALED)
      return false
    }
    //  SEC-01: دفترِ رمزدارِ خوانده‌نشده فقط با «بازسازی از سرور» جایگزین می‌شود (`finishRebuild`)
    //  — شرح سرِ `unreadable`
    if (_unreadable.value != null) {
      if (!quiet) _diskError.tryEmit(UNREADABLE)
      return false
    }
    //  فایلِ کناری + fsync + جابه‌جاییِ اتمی که روی ویندوز هم جایگزین می‌کند (`DiskWrite`). رمز فقط با «رمزگذاریِ داده» (`AtRest`)؛
    //  خاموش ⇒ همان بایت‌های همیشه، تکه‌تکه و بی رشته و آرایهٔ کاملِ دفتر در حافظه (PERF-LEDGER، `LedgerText`). DATA-SAFETY: `.prev`
    val saved = runCatching { val keep = extras?.takeIf { localOnly() }.also { extras = it }; if (AtRest.wanted(context)) AtRest.writeText(context, file, keep?.text(json, next) ?: json.encodeToString(next), keepPrevious = true)
      else DiskWrite.stream(file, keepPrevious = true) { o -> LedgerText.write(json, next, keep) { b, at, n -> o.write(b, at, n) } } }
    saved.exceptionOrNull()?.let {
      //  [quiet]: نوشتنِ پس‌زمینه (CRM-10 sweep) — کاربر چیزی ثبت نکرده که «ذخیره نشد» بشنود
      if (!quiet) _diskError.tryEmit(it.message ?: StrAppData.unknownError)
      return false
    }
    _unreadable.value = null
    _data.value = next
    //  همتاها (صفحه یا کارگر) همین دفتر را می‌گیرند؛ این نمونه نسخهٔ تازه را
    follow.publishedLocked(next)
    //  خلاصهٔ کوچک، کنارِ دفتر. نگهبانِ پس‌زمینه همین را می‌خواند و دیگر
    //  لازم نیست هر ربع ساعت کلِ دفتر را تجزیه کند — شرحش سرِ
    //  `LedgerSummary`.
    if (!training) LedgerSummary.write(context, next)
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
   *  مُهرِ `exportedAt` دارد. OPS-02: همین قاعده و پیامِ روشنِ فایلِ بریده/تازه‌تر در `BackupRestore.decode`.
   */
  fun parseBackup(raw: String): Result<ShopData> = runCatching { BackupRestore.decode(raw) }

  /**
   *  واردکردنِ داده‌ای که از نسخهٔ وب می‌آید.
   *
   *  ⚠️ قفلِ نرم جلوی این را نمی‌گیرد: بازیابیِ پشتیبان همان دادهٔ خودِ
   *  کاربر است و بستنش یعنی گروگان گرفتنِ دفترِ خودش.
   */
  suspend fun importJson(raw: String): Result<ShopData> = withContext(Dispatchers.IO) {
    parseBackup(raw).mapCatching {
      if (!applyRestore(it)) throw java.io.IOException(StrAppData.ledgerNotWritten)
      it
    }
  }

  /* -------------------------- پشتیبانِ ایمنی -------------------------- */

  private val safety = File(home, "before-restore.json")

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
      if (!applyRestore(parsed)) throw java.io.IOException(StrAppData.ledgerNotWritten)
      safety.delete()
      parsed
    }
  }

  fun dropSafetyCopy() {
    runCatching { safety.delete() }
  }

  fun hasData(): Boolean = file.exists()

  /** OPS-07: دفترِ نمونه روی دفترِ آموزشیِ **خالی** (`TrainingLedger.enter`) — دفترِ واقعی یا آموزشیِ پر هرگز */
  suspend fun seedTraining(sample: ShopData): Boolean = withContext(Dispatchers.IO) {
    if (!training || file.exists()) return@withContext false
    writeInternal(sample)
  }

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

  private fun vault(key: String) = File(home, "ledger-${safeKey(key)}.json")

  /** آیا دفترِ حسابی زیرِ این نام بایگانی شده (`LedgerOwner`: «بی‌دکان ⇒ دکانِ بایگانی‌شده») */
  fun hasVault(key: String): Boolean = vault(key).exists()

  /** نشانِ «این بایگانی را ساختِ «یک درِ ورود» نوشت» — سایه/صفِ همان حساب با آن جابه‌جا شده است */
  private fun bound(key: String) = File(home, "ledger-${safeKey(key)}.bound")

  /**
   *  بایگانیِ ساخت‌های پیشین (بی نشان): کلیدِ همگام‌سازی همراهش جابه‌جا نمی‌شد، پس سایهٔ حسابش شاید مالِ
   *  دفترِ دیگری است ⇒ پس از باز شدن فقط با `SyncV1Engine.recoverArchived` (سرور برنده، ردیف‌های تنها درج).
   */
  fun vaultLegacy(key: String): Boolean = vault(key).exists() && !bound(key).exists()

  /** دفترِ روی میز هیچ ردیفِ شناسه‌داری ندارد (فهرست‌های نامیِ پیش‌فرض شمرده نمی‌شوند) */
  fun deskEmpty(): Boolean = _data.value.let { d ->
    d.products.isEmpty() && d.sales.isEmpty() && d.saleItems.isEmpty() && d.debtors.isEmpty() &&
      d.transactions.isEmpty() && d.expenses.isEmpty() && d.warehouseEntries.isEmpty() && d.suppliers.isEmpty() &&
      d.purchases.isEmpty() && d.supplierPayments.isEmpty() && d.saleReturns.isEmpty() && d.stockMovements.isEmpty()
  }

  /**
   *  بایگانی کردنِ دفترِ فعلی زیرِ نامِ حساب، و خالی کردنِ دفترِ روی میز.
   *  @return `false` ⇒ رونوشت (fsync + جابه‌جاییِ اتمی، `DiskWrite.copy`) ننشست و دفترِ روی میز **دست نخورد**؛
   *    صدازننده (`LedgerOwner`) نباید دفترِ دیگری جایش باز کند
   */
  suspend fun stashTo(key: String): Boolean = withContext(Dispatchers.IO) {
    if (TrainingMode.on) return@withContext true   //  OPS-07: دفترِ واقعی در آموزش جابه‌جا نمی‌شود (`LedgerOwner` هم نمی‌خواهد)
    //  زیرِ قفلِ نوشتنِ دفتر و قفلِ کوچِ رمز (`AtRest.converge`): کوچ میانِ کپی و پاک کردن نمی‌نشیند
    val kept = synchronized(SyncV1Engine.of(context)) {
      runCatching {
        synchronized(AtRest.lock) {
          if (file.exists()) DiskWrite.copy(file, vault(key))
          runCatching { bound(key).writeText("1") }
          file.delete()
        }
      }.isSuccess.also { ok ->
        //  همتاهای پیشین (کارگرِ نیمه‌راه) دیگر روی این فایل نمی‌نویسند — `LedgerFollow.movedLocked`
        if (ok) { follow.movedLocked(stop = false, next = ShopData()); _unreadable.value = null; extras = null }
      }
    }
    if (kept) LedgerSummary.clear(context)
    kept
  }

  /** باز کردنِ دفترِ یک حساب — اگر بایگانی نداشت، دفترِ خالی */
  suspend fun openFrom(key: String) {
    if (TrainingMode.on) return
    withContext(Dispatchers.IO) {
      val saved = vault(key)
      synchronized(SyncV1Engine.of(context)) {
        runCatching {
          synchronized(AtRest.lock) { if (saved.exists()) DiskWrite.copy(saved, file) else file.delete() }
        }
        follow.movedLocked(stop = true, next = ShopData())
      }
    }
    _loaded.value = false
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
    if (_unreadable.value != null) throw StaleLedger(UNREADABLE)   //  ⛔ DATA-SAFETY: دفترِ خوانده‌نشده پشتیبانِ «خالی» نمی‌دهد
    val tree = (extras?.takeIf { localOnly() }?.tree(json, _data.value) ?: json.encodeToJsonElement(ShopData.serializer(), _data.value) as kotlinx.serialization.json.JsonObject).toMutableMap()  //  §۷: + کلیدهای نسخهٔ تازه‌تر
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
    /** پیامِ ذخیره‌ای که رد شد چون دفتری که صفحه با آن ساخت دیگر نگه داشته نشده ([LedgerFollow.baseOf]) */
    val STALE_BASE: String get() = StrAppData.staleBase

    /** پیامِ ویرایش‌هایی که چون ردیفشان در دستگاهِ دیگر حذف شده بود ننشستند ([LedgerMerge.Outcome.dropped]) */
    fun editsDropped(n: Int): String =
      StrAppData.editsDropped(n)

    /** پیامِ نوشتنی که رد شد چون دفترِ دیسک جلوتر بود و خوانده نشد (یا دفترِ حسابِ دیگری بود) */
    val STALE_UNREADABLE: String get() = StrAppData.staleUnreadable

    /** پیامِ نوشتنی که به‌خاطرِ دفترِ رمزدارِ خوانده‌نشده رد شد */
    /** OPS-07: نوشتنی روی دفترِ واقعی، یا کاری که به سرور می‌رسد، وقتی حالتِ آموزشی روشن است */
    val TRAINING_SEALED: String get() = StrAppData.trainingSealed

    val UNREADABLE: String get() = StrAppData.unreadable

    val NEED_SIGN_IN: String get() = StrAppData.needSignIn

    val NEED_SIGN_IN_SYNCED: String get() = StrAppData.needSignInSynced

    val NEED_NET: String get() = StrAppData.needNet

    /** چند رکورد در این دفتر هست — برای خلاصهٔ پیش از بازیابی */
    fun backupSize(d: ShopData): Int =
      d.products.size + d.sales.size + d.debtors.size + d.expenses.size +
        d.suppliers.size + d.purchases.size + d.warehouseEntries.size + d.transactions.size

    /* ------------------------- محاسبه‌ها ------------------------- */

    /**
     *  جدولِ موجودی — یک بار ساخته می‌شود، بارها خوانده.
     *
     *  این کلاس یک باگِ کندیِ واقعی را می‌بندد. حسابِ قبلی برای **هر کالا** کلِ اقلامِ فروش را می‌گشت، و برای
     *  **هر قلم** هم کلِ فاکتورها را دنبالِ وضعیتِ «لغو» می‌گردید. یعنی هزینه‌اش ضربِ سه عدد بود: کالاها × اقلامِ فروش × فاکتورها.
     *
     *  صفحهٔ محصولات این را برای همهٔ کالاها صدا می‌زد و با هر حرفی که در جستجو زده می‌شد از نو. دکانی با
     *  چند صد کالا و چند هزار فاکتور، همان‌جا می‌ایستاد.
     *
     *  حالا یک بار روی هر سه فهرست رد می‌شویم و سه نگاشت می‌سازیم؛ بعد هر پرسش یک خواندن است.
     *  PRD-10: کیت موجودیِ خودش را ندارد — ورود/فروش/برگشتِ آن به اجزا پخش می‌شود (`ProductPlansMap`).
     */
    /** [cancelled] = فاکتورهای لغوشده، برای [extend]؛ [absorbOn] = INV-10: فروشِ پیش از شمارشِ دیررسیده (`CountAbsorbWiring`؛ خاموش ⇒ خالی) */
    class StockIndex private constructor(
      private val entered: HashMap<String, Double>, private val cartonsIn: HashMap<String, Double>, private val sold: HashMap<String, Double>,
      private val kits: Map<String, List<KitPart>>, private val cancelled: HashSet<String>, val absorbOn: Boolean, d: ShopData?,
    ) {
      constructor(d: ShopData) : this(
        HashMap(), HashMap(), HashMap(), d.products.mapNotNull { p -> p.kit?.ifEmpty { null }?.let { p.id to it } }.toMap(), HashSet(), CountAbsorbWiring.serverFlag, d,
      ) {
        d.warehouseEntries.forEach { e ->
          (kits[e.productId]?.map { it.productId to e.units * it.qty } ?: listOf(e.productId to e.units)).forEach { (id, u) -> entered[id] = (entered[id] ?: 0.0) + u }
          cartonsIn[e.productId] = (cartonsIn[e.productId] ?: 0.0) + e.cartons
        }
        d.sales.forEach { if (it.status == "cancelled") cancelled += it.id } // وضعیتِ فاکتورها یک بار، نه برای هر قلم
        add(d.saleItems)
      }
      private fun add(items: List<SaleItem>) = items.forEach { item ->
        if (item.saleId in cancelled) return@forEach
        sold[item.productId] = (sold[item.productId] ?: 0.0) + (item.quantity - item.returnedQty)
        item.parts?.forEach { k -> sold[k.productId] = (sold[k.productId] ?: 0.0) + (item.quantity - item.returnedQty) * k.qty }
      }
      /** POS-42 — همین جدول برای [next] بی گذشتن از کلِ دفتر، فقط وقتی [next] = [prev] + فاکتور و قلمِ تازه به ته (`AppendTail`، همان شیءها)،
       *  کالا و ورودِ انبار همان شیء، فاکتورِ تازهٔ لغوشده نه و «جذبِ شمارش» خاموش: جمعِ هر کالا از همان جمعِ پیشین به همان ترتیب ⇒ عددِ
       *  دقیقاً برابرِ ساختنِ کامل (`StockIndexAppendTest`). وگرنه `null`. جدولِ پیشین دست نمی‌خورد (`sold` رونوشت است). */
      internal fun extend(prev: ShopData, next: ShopData): StockIndex? {
        if (absorbOn || CountAbsorbWiring.serverFlag || next.products !== prev.products || next.warehouseEntries !== prev.warehouseEntries) return null
        ir.vil3ntec.tohid.core.ledger.AppendTail.of(prev.sales, next.sales)?.takeIf { t -> t.none { it.status == "cancelled" } } ?: return null
        val items = ir.vil3ntec.tohid.core.ledger.AppendTail.of(prev.saleItems, next.saleItems) ?: return null
        return if (items.isEmpty()) this else StockIndex(entered, cartonsIn, HashMap(sold), kits, cancelled, false, null).also { it.add(items) }
      }

      fun soldQty(productId: String): Double = sold[productId] ?: 0.0

      private val absorbed = if (d != null) CountAbsorbWiring.gaps(d, absorbOn) else emptyMap()

      fun stock(productId: String): Double = kits[productId]?.let { k -> ProductPlansMap.kitStock(k) { raw(it) } } ?: raw(productId)
      private fun raw(productId: String): Double = (entered[productId] ?: 0.0) - (sold[productId] ?: 0.0) - (absorbed[productId]?.gapMilli ?: 0L) / 1000.0

      fun cartons(productId: String): Double = cartonsIn[productId] ?: 0.0

      fun status(product: Product): String {
        val s = if (product.stockless == true) Double.MAX_VALUE else stock(product.id) // PRD-09: خدماتی همیشه «کافی»
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
      if (ready != null && indexedData === d && ready.absorbOn == CountAbsorbWiring.serverFlag) return ready
      val fresh = indexedData?.let { prev -> ready?.extend(prev, d) } ?: StockIndex(d) // POS-42: پس از فروش فقط اقلامِ تازه، نه کلِ دفتر
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
    fun debt(d: ShopData, debtorId: String): Double = DebtMinorFlag.balance(DebtRows.of(d)[debtorId].orEmpty())

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

    fun barcodeIndex(d: ShopData): Map<String, String> = ProductIndexes.barcodes(d.products) // S125: یک بار برای همان فهرست
    fun productsById(products: List<Product>): Map<String, Product> = ProductIndexes.byId(products)
    fun product(d: ShopData, id: String): Product? = ProductIndexes.byId(d.products)[id] // = `products.find { it.id == id }`

    /**
     *  PRD-04: بارکدِ بسته‌ها (کارتن، شل…) ⇐ کالا و بسته — جدا از `barcodeIndex` تا هیچ راهِ اسکنی که
     *  بسته را نمی‌شناسد کارتن را «یک دانه» نخواند (آن‌جا «ناشناس» است، نه مقدارِ غلط).
     */
    fun packIndex(d: ShopData): ir.vil3ntec.tohid.core.product.PackBarcodes.Index =
      ir.vil3ntec.tohid.core.product.PackBarcodes.Index(
        d.products.mapNotNull { p -> p.packs?.takeIf { it.isNotEmpty() }?.let { p.id to it.map(::coreOf) } }
      )

    fun coreOf(p: ProductPack) = ir.vil3ntec.tohid.core.product.PackBarcodes.Pack(p.name, p.sizeMilli, p.barcodes, p.priceMinor)

    /**
     *  همهٔ بارکدهای دکان (کالا و بسته) ⇐ شناسهٔ کالا — برای سنجشِ «بارکد بین کالاها تکراری نمی‌شود» (§۳.۴).
     *  تا بسته‌ای نباشد همان `barcodeIndex` است.
     */
    fun codeOwners(d: ShopData): Map<String, String> {
      val main = barcodeIndex(d)
      if (d.products.none { !it.packs.isNullOrEmpty() || !it.purchaseUnits.isNullOrEmpty() }) return main
      return buildMap {
        //  PRD-07: بارکدِ واحدِ خرید هم مالِ همان کالاست (بارکدِ کالا و بسته بر آن جلو می‌افتند)
        d.products.forEach { p -> p.purchaseUnits.orEmpty().forEach { u -> u.barcode?.let { put(it, p.id) } } }
        d.products.forEach { p -> p.packs.orEmpty().forEach { pk -> pk.barcodes.forEach { put(it, p.id) } } }
        putAll(main)
      }
    }

    /** PRD-07: بارکدِ واحدِ خرید ⇐ کالا و واحد — فقط فرمِ خرید و سفارش آن را می‌خواند (صندوق نه) */
    fun purchaseUnitIndex(d: ShopData): ir.vil3ntec.tohid.core.product.PurchaseUnits.Index =
      ir.vil3ntec.tohid.core.product.PurchaseUnits.Index(
        d.products.mapNotNull { p -> p.purchaseUnits?.takeIf { it.isNotEmpty() }?.let { p.id to it.map(::coreOf) } }
      )

    fun coreOf(u: PurchaseUnit) = ir.vil3ntec.tohid.core.product.PurchaseUnits.Item(u.name, u.factorMilli, u.supplierId, u.barcode)
  }
}
