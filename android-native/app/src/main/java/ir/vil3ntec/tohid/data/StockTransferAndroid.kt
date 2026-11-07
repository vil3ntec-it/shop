package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.ledger.AppendOnce
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.stock.StockTransfer
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/**
 *  INV-11 — مرزِ پلتفرمِ «انتقالِ موجودی»: پرچم و اجازه از سرور، نمای نگه‌داشتهٔ سرور برای بی‌اینترنت، پیش‌نویسِ
 *  همین دستگاه، و ثبتِ گام در دفتر.
 *
 *  ⛔ **پیش‌فرض خاموش**: فقط وقتی `GET /shop/catalog/config` ‹`enabled`› (پرچمِ `shop.catalog`) و ‹`permissions.move`›
 *     (`stock.move` ِ سرور) هر دو روشن‌اند ([on])؛ خاموش ⇒ هیچ دکمه‌ای، هیچ ردیفی، هیچ درخواستی جز همان یک پرسشِ
 *     پرچم — انبار دقیقاً همان امروز. بی اینترنت آخرین مقدارِ همان دکان می‌ماند.
 *  ⛔ نوشتن فقط [record]: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ دیسک (همان `StockPlacesPref.record`)، کلید = شناسهٔ ردیف
 *     که صفحه پیش از ثبت ساخته ⇒ دو ضربه یا تکرار پس از قطع یک گام. سپس همگام‌سازی همان لحظه صدا زده می‌شود.
 */
object StockTransferFlag {

  private const val PREFS = "tohid-stock-transfer"
  private const val CONFIG = "/shop/catalog/config"
  private const val LIST = "/shop/catalog/transfers"
  const val CHECK_MS = 10 * 60_000L

  /** آخرین پاسخِ پرچم برای دکانِ فعال (`null` ⇒ هنوز پرسیده نشده) — منوی انبار با آن دیده/پنهان می‌شود */
  val visible = mutableStateOf<Boolean?>(null)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun key(context: Context, k: String) = LedgerOwner.currentShop(context).ifBlank { "-" } + ":" + k

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(key(context, "on"), false) }.getOrDefault(false)

  /** پرچم و اجازه را بپرس (نه زودتر از [CHECK_MS] مگر [force]) ⇒ مقدارِ همین حالا */
  suspend fun refresh(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis()): Boolean {
    val app = context.applicationContext
    if (visible.value == null) visible.value = on(app)
    val at = key(app, "at")
    if (!force && now - prefs(app).getLong(at, 0L) < CHECK_MS) return on(app)
    if (!Backend.isReady(app) || !TrainingMode.networkAllowed()) return on(app)
    return try {
      val c = Backend.api(app).get(CONFIG)
      val enabled = c["enabled"]?.jsonPrimitive?.booleanOrNull == true
      val move = (c["permissions"] as? JsonObject)?.get("move")?.jsonPrimitive?.booleanOrNull == true
      prefs(app).edit().putBoolean(key(app, "on"), enabled && move).putLong(at, now).apply()
      visible.value = enabled && move
      enabled && move
    } catch (c: CancellationException) {
      throw c
    } catch (f: Exception) {
      //  خطای ماندگار (۴۰۳ ِ نقش، حسابِ بی دکان) ⇒ خاموش؛ قطعِ شبکه ⇒ همان آخرین مقدار
      if (!(f is ApiFailure && (f.retryable || f is ApiFailure.RateLimited || f is ApiFailure.ServerFault))) {
        prefs(app).edit().putBoolean(key(app, "on"), false).putLong(at, now).apply()
        visible.value = false
      }
      on(app)
    }
  }

  /* ------------------------------------------------------------ نمای سرور */

  data class Snapshot(
    val transfers: List<StockTransfer.Transfer>,
    val outcomes: Map<String, StockTransfer.Outcome>,
    /** زمانِ آخرین پاسخِ سرور؛ ۰ ⇒ هرگز */
    val at: Long,
    /** همین بار از سرور آمد (نه از نگه‌داشته) */
    val live: Boolean,
  )

  fun cached(context: Context): Snapshot {
    val raw = runCatching { prefs(context).getString(key(context, "cache"), null) }.getOrNull()
    val body = raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
      ?: return Snapshot(emptyList(), emptyMap(), 0, false)
    val (ts, os) = StockTransfer.listOf(body)
    return Snapshot(ts, os, body["at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0, false)
  }

  /**
   *  فهرستِ سرور و نتیجهٔ گام‌های نامعلومِ [steps]؛ نتیجه‌ها ماندگارند (گامِ ثبت/ردشده دیگر عوض نمی‌شود).
   *  بی اینترنت ⇒ همان نگه‌داشته با `live = false`.
   */
  suspend fun fetch(context: Context, steps: List<StockTransfer.Step>, now: Long = System.currentTimeMillis()): Snapshot {
    val app = context.applicationContext
    val old = cached(app)
    if (!Backend.isReady(app) || !TrainingMode.networkAllowed()) return old
    val keys = StockTransfer.unresolved(steps, old.outcomes)
    return try {
      val q = if (keys.isEmpty()) "" else "&keys=" + keys.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
      val body = Backend.api(app).get("$LIST?limit=100$q")
      val (ts, os) = StockTransfer.listOf(body)
      val live = steps.map { it.key }.toSet()
      val known = (old.outcomes + os).filterKeys { it in live }
      prefs(app).edit().putString(
        key(app, "cache"),
        buildJsonObject {
          put("at", now)
          put("transfers", StockTransfer.transfersJson(ts))
          put("steps", StockTransfer.outcomesJson(known))
        }.toString(),
      ).apply()
      Snapshot(ts, known, now, true)
    } catch (c: CancellationException) {
      throw c
    } catch (_: Exception) {
      old
    }
  }

  /* ------------------------------------------------------------ پیش‌نویس */

  fun draft(context: Context): StockTransfer.Draft? {
    val raw = runCatching { prefs(context).getString(key(context, "draft"), null) }.getOrNull() ?: return null
    return runCatching { StockTransfer.draftOf(Json.parseToJsonElement(raw).jsonObject) }.getOrNull()
  }

  fun saveDraft(context: Context, d: StockTransfer.Draft?) {
    val e = prefs(context).edit()
    if (d == null) e.remove(key(context, "draft")) else e.putString(key(context, "draft"), StockTransfer.draftJson(d).toString())
    e.apply()
  }

  /* ------------------------------------------------------------ ثبت */

  sealed interface Saved {
    /** نشست؛ [again] ⇒ همین کلید پیش‌تر نشسته بود */
    data class Ok(val again: Boolean) : Saved
    /** قفلِ نرم یا دیسک، یا دفترِ دیسک خوانده نشد */
    data object NotSaved : Saved
  }

  suspend fun record(context: Context, store: ShopStore, step: StockTransfer.Step, user: String?, today: String): Saved {
    val out = AppendOnce.append<ShopData>(
      step.key, StockTransferBook::has,
      { cur, _ -> StockTransferBook.append(cur, step, user, today) },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    val saved = when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true)
      AppendOnce.Outcome.Skipped -> Saved.Ok(again = true)
      AppendOnce.Outcome.Unread, AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
    if (saved is Saved.Ok && !saved.again) runCatching { AutoSync.now(context, store) }
    return saved
  }
}
