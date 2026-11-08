package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException

/**
 *  DATA-SAFETY — دفترِ ساده‌ای که خوانده نشد هرگز «دفترِ خالی» نیست.
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  `ShopStore.load` خطای خواندنِ دفترِ ساده (فایلِ قفل‌شده به دستِ ضدویروس، خطای دیسک) و JSON ِ خراب
 *  (فایلِ بریده، صفر بایت) را بی‌صدا «دفترِ خالی» می‌گرفت. نوشتنِ بعدی — نخستین فروش، یا کارگرِ
 *  همگام‌سازی که opهای رسیده را روی همان دفترِ خالی می‌نشاند — فایلِ واقعی را رونویسی می‌کرد و تفاضلِ
 *  همگام‌سازی هر ردیف را «حذف‌شده» به سرور می‌فرستاد: از دست رفتنِ کلِ دادهٔ دکان.
 *
 *  ── حالا ─────────────────────────────────────────────────────────────
 *  ۱) خواندنِ ناموفق یکی از سه [Damage] است و نوشتن بسته می‌ماند (همان نگهبانِ `ShopStore.unreadable`).
 *  ۲) فایلِ خراب سرِ جایش می‌ماند؛ هر راهِ جایگزینی (`ShopStore.finishRebuild`) کنارش می‌گذارد
 *     (`shop-data.json.unreadable-<زمان>`)، هرگز پاکش نمی‌کند.
 *  ۳) تازه‌ترین نسخهٔ سالم از میانِ [candidates] پیدا می‌شود: نسخهٔ پیشینِ هر ذخیره (`.prev`، `DiskWrite`)،
 *     فایلِ کناریِ نوشتنی که جابه‌جایی‌اش نرسید (`.tmp`) و نسخه‌های خودکار (`AutoBackup`).
 *  ۴) برگرداندن هرگز op نمی‌سازد: دستگاهِ بی ورود و هرگز همگام‌نشده ⇒ خودکار، همان `SyncV1Engine.resetTo`؛
 *     وارد شده ⇒ با پنجره و از راهِ «بازسازی از سرور» با همین نسخه به‌عنوانِ پشتیبان (`ShopStore.rebuildFromServer`).
 */
object LedgerRecovery {

  /** چرا دفتر خوانده نشد */
  enum class Damage {
    /** JSON ِ بریده یا بی‌معنی (صفر بایت، قطعِ برق، خرابیِ دیسک) — نسخهٔ سالم جایش برمی‌گردد */
    CORRUPT,

    /** JSON ِ درست ولی دفترِ این نسخه نیست — شاید نسخهٔ تازه‌ترِ برنامه نوشته؛ هرگز خودکار برنمی‌گردد */
    NEWER_FORMAT,

    /** خودِ خواندن نشد (دیسک، اجازه، قفلِ برنامهٔ دیگر) — فایل شاید سالم است؛ «دوباره امتحان» */
    READ_ERROR,
  }

  /** از کجا برگشت */
  enum class Source { PENDING, PREVIOUS, AUTO_BACKUP }

  data class Found(val data: ShopData, val file: File, val at: Long, val source: Source)

  /** خواندنِ دفتر: یا دفتر، یا گونهٔ خرابی */
  sealed interface Read {
    data class Ok(val data: ShopData) : Read
    data class Bad(val damage: Damage) : Read
  }

  /** همان سه تلاشِ کوتاه پیش از «خوانده نشد» — قفلِ لحظه‌ایِ ضدویروس یا نمایه‌سازِ ویندوز */
  const val READ_ATTEMPTS = 3

  /**
   *  @param readText خواندنِ متن (رمزگشایی اگر رمزدار است) — `AtRestException` بالا می‌رود (کارِ صدازننده)
   */
  fun read(file: File, json: Json, readText: (File) -> String, pause: (Int) -> Unit = { Thread.sleep(40L * it) }): Read {
    var text: String? = null
    var attempt = 0
    while (text == null) {
      try {
        text = readText(file)
      } catch (e: AtRestException) {
        throw e
      } catch (e: Exception) {
        attempt++
        if (attempt >= READ_ATTEMPTS) return Read.Bad(Damage.READ_ERROR)
        pause(attempt)
      }
    }
    return decode(text!!, json)
  }

  /** متنِ خوانده‌شده ⇒ دفتر یا گونهٔ خرابی */
  fun decode(text: String, json: Json): Read =
    runCatching { json.decodeFromString(ShopData.serializer(), text) }.fold(
      { Read.Ok(it) },
      { Read.Bad(if (runCatching { json.parseToJsonElement(text) }.getOrNull() is JsonObject) Damage.NEWER_FORMAT else Damage.CORRUPT) },
    )

  /**
   *  جاهایی که نسخهٔ سالم شاید هست، به هر ترتیب — [best] با زمانِ فایل می‌چیند.
   *  @param snapshots نسخه‌های خودکار (`AutoBackup.list`) — برای دفترِ آموزشی خالی
   */
  fun candidates(ledger: File, snapshots: List<File>): List<Pair<File, Source>> =
    listOf(
      File(ledger.absoluteFile.parentFile, ledger.name + ".tmp") to Source.PENDING,
      DiskWrite.previousOf(ledger) to Source.PREVIOUS,
    ) + snapshots.map { it to Source.AUTO_BACKUP }

  /**
   *  تازه‌ترین نسخه‌ای که درست خوانده می‌شود — هر کدام که نخواند یا خراب بود رد می‌شود، بی‌صدا.
   *  ترتیب با زمانِ نوشتنِ خودِ فایل (پیوندِ `.prev` زمانِ همان نسخه را دارد؛ نسخهٔ خودکار زمانِ رونوشت)؛
   *  هم‌زمان ⇒ همان ترتیبِ [candidates].
   */
  fun best(candidates: List<Pair<File, Source>>, json: Json, readText: (File) -> String): Found? =
    candidates.withIndex()
      .filter { (_, c) -> c.first.isFile && c.first.length() > 0L }
      .sortedWith(compareByDescending<IndexedValue<Pair<File, Source>>> { it.value.first.lastModified() }.thenBy { it.index })
      .firstNotNullOfOrNull { (_, c) ->
        val text = runCatching { readText(c.first) }.getOrNull() ?: return@firstNotNullOfOrNull null
        (decode(text, json) as? Read.Ok)?.let { Found(it.data, c.first, c.first.lastModified(), c.second) }
      }

  /** همان نگهبانِ نوشتنِ `ShopStore.unreadable` برای هر گونهٔ خرابی */
  fun kindOf(damage: Damage): AtRestException.Kind = when (damage) {
    Damage.CORRUPT -> AtRestException.Kind.MALFORMED
    Damage.NEWER_FORMAT -> AtRestException.Kind.UNSUPPORTED
    Damage.READ_ERROR -> AtRestException.Kind.IO
  }

  /** تازه‌ترین نسخهٔ سالمِ کنارِ [ledger] (`.tmp`، `.prev` و — اگر [snapshots] — نسخه‌های خودکار) */
  fun near(context: Context, ledger: File, json: Json, snapshots: Boolean): Found? {
    val snaps = if (!snapshots) emptyList() else runCatching { AutoBackup.list(context).map { it.file } }.getOrDefault(emptyList())
    return best(candidates(ledger, snaps), json) { AtRest.readText(context, it) }
  }

  /**
   *  برگشتِ خودکار فقط وقتی هیچ سروری در کار نیست: دستگاهِ بی ورود که این حساب را هرگز همگام نکرده (همان
   *  `RestoreRoute.Route.LOCAL`) و همگام‌سازیِ v1. واردشده ⇒ پنجره و «بازسازی از سرور» با همین نسخه: ردیف‌های
   *  پس از نسخه روی سرورند، و نشاندنِ نسخهٔ کهنه کنارِ سایهٔ تازه برای آن‌ها DELETE می‌ساخت.
   */
  fun autoRecoverable(context: Context): Boolean {
    if (SyncChoice.isV3(context)) return false
    if (runCatching { Backend.isReady(context) }.getOrDefault(true)) return false
    val engine = SyncV1Engine.of(context)
    return !engine.rebuilding && !engine.everSynced()
  }

  /**
   *  حالت‌های دفترِ خرابِ یک `ShopStore` — بیرون از آن تا خودِ دفتر کوتاه بماند.
   *
   *  ⛔ هر وقت [damage] پر است `ShopStore.unreadable` هم پر است ⇒ هر نوشتن بسته؛ این فقط متنِ پنجره
   *  (`UnreadableLedgerDialog`) و راهِ برگشت را می‌دهد.
   */
  class Guard {
    private val _damage = MutableStateFlow<Damage?>(null)
    val damage: StateFlow<Damage?> = _damage.asStateFlow()

    /** تازه‌ترین نسخهٔ سالمی که پیدا شد — دکمهٔ «برگرداندن از نسخهٔ سالم» */
    private val _recovery = MutableStateFlow<Found?>(null)
    val recovery: StateFlow<Found?> = _recovery.asStateFlow()

    /** دفتری که هنگامِ باز شدن خودکار برگشت — یک بار گفته می‌شود ([ack]) */
    private val _recovered = MutableStateFlow<Found?>(null)
    val recovered: StateFlow<Found?> = _recovered.asStateFlow()

    fun clear() { _damage.value = null; _recovery.value = null }
    fun ack() { _recovered.value = null }

    /**
     *  دفتر خوانده نشد: نسخهٔ سالم پیدا می‌شود؛ فقط JSON ِ خراب و فقط وقتی [auto] اجازه دهد خودکار با [reset]
     *  برمی‌گردد. خطای خواندن ⇒ هیچ نسخه‌ای (فایل شاید سالم است و فقط لحظه‌ای قفل بود).
     *  شکستِ [reset] ⇒ همان حالِ بسته با پنجره — هیچ چیزی پاک نمی‌شود.
     */
    fun damaged(damage: Damage, find: () -> Found?, auto: () -> Boolean, reset: (Found) -> Unit) {
      _damage.value = damage
      val found = if (damage == Damage.READ_ERROR) null else runCatching { find() }.getOrNull()
      _recovery.value = found
      if (found == null || damage != Damage.CORRUPT || !runCatching { auto() }.getOrDefault(false)) return
      if (runCatching { reset(found) }.isSuccess) { clear(); _recovered.value = found }
    }
  }

  /**
   *  خواندنِ سخت‌گیر برای **نوشتن**: نبودِ فایل ⇒ `null`؛ خطای خواندن ⇒ همان خطا؛ متنِ خراب ⇒ رونوشتی کنار
   *  (`<نام>.corrupt-<اندازه>`، یک بار برای هر اندازه) و `IOException` — هرگز «خالی» که نوشتنِ بعدی رویش بنشیند.
   *  (`LedgerArchive.compact`: بایگانیِ خراب پیش‌تر با «خالی + تازه‌ها» رونویسی می‌شد.)
   */
  fun <T> strict(file: File, readText: (File) -> String, decode: (String) -> T): T? {
    if (!file.exists()) return null
    val text = try {
      readText(file)
    } catch (e: AtRestException) {
      throw e
    } catch (e: Exception) {
      throw IOException(StrAppData.notReadKept(file.name), e)
    }
    return runCatching { decode(text) }.getOrElse { e ->
      val keep = File(file.absoluteFile.parentFile, "${file.name}.corrupt-${file.length()}")
      if (!keep.exists()) runCatching { DiskWrite.copy(file, keep) }
      throw IOException(StrAppData.damagedSetAside(file.name, keep.name), e)
    }
  }

  /** آیا این فایل دفترِ درستی است — نسخهٔ خودکار و پشتیبانِ ابری هرگز از دفترِ خراب ساخته نمی‌شوند */
  fun readable(file: File, json: Json, readText: (File) -> String): Boolean =
    file.isFile && file.length() > 0L && runCatching { decode(readText(file), json) is Read.Ok }.getOrDefault(false)
}
