package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 *  پشتیبانِ سرورِ فاکتورهای نگه‌داشته روی این دستگاه — شرح و قاعده‌ها: `HeldSyncEngine` (`HeldSync.kt`).
 *
 *  کی: بازشدنِ صندوق و سپس هر [MIN_MS] تا صندوق باز است (`rememberHeldInvoices`)، هر وصلِ سوکتِ زنده (`hello`)، خبرِ
 *  `held` ِ دستگاهِ دیگر (فوری) و هر تغییرِ دفترِ نگه‌داشته‌ها وقتی پرچم روشن است (فوری).
 *  ⛔ پرچمِ `shop.held_sync` خاموش ⇒ تنها کار یک `GET` در هر [MIN_MS] است؛ هیچ نوشتنی (نه `tohid-held-sync`، نه فایلِ
 *     نگه‌داشته‌ها). حالتِ آموزشی (OPS-07) هرگز: دفترِ آموزشی وصل نمی‌شود و شبکه بسته است.
 *  «در دستِ دستگاهِ X» پیش از بازگرداندن ([claim]، حداکثر [CLAIM_WAIT_MS]؛ نرسید ⇒ همان بی شبکه) و «یک فاکتور در دو
 *  دستگاه» ([conflicts]): شرح سرِ `HeldSync.kt`. خاموش ⇒ [claims] `false` و هیچ درخواستی.
 */
object HeldSync {

  private const val PREFS = "tohid-held-sync"
  private const val PATH = "/shop/held-invoices"

  /** کمترین فاصلهٔ دو دورِ ناخواسته */
  const val MIN_MS = 60_000L

  /** بیشترین انتظارِ صندوق برای پاسخِ «در دستِ کیست» — پس از آن همان بازگرداندنِ بی شبکه */
  const val CLAIM_WAIT_MS = 3_000L

  private val _conflicts = MutableStateFlow<Map<String, HeldConflict>>(emptyMap())
  /** «یک فاکتور در دو دستگاه» ِ دیده‌نشدهٔ همین دکان روی این دستگاه — صندوق نشانش می‌دهد تا کاربر ببیند */
  val conflicts: StateFlow<Map<String, HeldConflict>> = _conflicts.asStateFlow()

  /** سبدِ صندوقِ باز (`CartStore`) — فاکتورِ همین حالا دوباره نگه‌داشته نشود */
  @Volatile var cart: HeldCart? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val engines = HashMap<String, HeldSyncEngine>()
  @Volatile private var lastAt = 0L

  private class Live(private val app: Context, private val device: () -> String) : HeldSyncEngine.Port {
    /** شناسهٔ دستگاه در نشانی — فقط نویسه‌های امن (همان `ID_RE` ِ سرور) */
    private fun dev(): String = runCatching(device).getOrDefault("").filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

    override suspend fun pull(since: Long): JsonObject? = runCatching { Backend.api(app).get("$PATH?since=$since") }.getOrNull()
    override suspend fun put(id: String, body: JsonObject): HeldSyncEngine.Reply = reply { Backend.api(app).put("$PATH/$id", body) }
    override suspend fun delete(id: String, version: Long, reason: String): HeldSyncEngine.Reply =
      reply { Backend.api(app).delete("$PATH/$id?version=$version&reason=$reason&device_id=${dev()}") }
    override suspend fun delete(id: String, version: Long, reason: String, base: Long): HeldSyncEngine.Reply =
      reply { Backend.api(app).delete("$PATH/$id?version=$version&reason=$reason&base=$base&device_id=${dev()}") }
    /**
     *  `HttpEngine` مسدودکننده است (`HttpURLConnection`)؛ `withTimeoutOrNull` دورِ خودِ درخواست تا پایانِ آن برنمی‌گشت. پس
     *  درخواست در پس‌زمینه و صندوق حداکثر [CLAIM_WAIT_MS] منتظر؛ پاسخِ دیررس بی‌اثر است (همان «بی شبکه»: کلیدِ تازه و
     *  برداشتنِ در صف با `base` — سنگِ قبرِ خودِ همین دستگاه ⇒ سرور می‌پذیرد، دیگری ⇒ «یک فاکتور در دو دستگاه»).
     */
    override suspend fun claim(id: String, version: Long, base: Long, force: Boolean): HeldSyncEngine.Reply {
      val call = scope.async {
        reply { Backend.api(app).delete("$PATH/$id?version=$version&reason=resumed&base=$base&claim=1${if (force) "&force=1" else ""}&device_id=${dev()}") }
      }
      return withTimeoutOrNull(CLAIM_WAIT_MS) { call.await() } ?: HeldSyncEngine.Reply(0)
    }

    private inline fun reply(call: () -> JsonObject): HeldSyncEngine.Reply = try {
      HeldSyncEngine.Reply(200, call())
    } catch (c: kotlinx.coroutines.CancellationException) {
      throw c
    } catch (f: ApiFailure) {
      HeldSyncEngine.Reply(f.status, error = f.detail)
    } catch (_: Exception) {
      HeldSyncEngine.Reply(0)
    }
  }

  private class Prefs(private val app: Context, private val shop: String) : HeldSyncEngine.StateStore {
    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun read(): HeldSyncState = HeldSyncEngine.decodeState(prefs().getString("state:$shop", null))
    override fun update(f: (HeldSyncState) -> HeldSyncState): HeldSyncState = synchronized(HeldSync) {
      val cur = read()
      val next = f(cur)
      if (next != cur) {
        if (next == HeldSyncState()) prefs().edit().remove("state:$shop").commit()
        else prefs().edit().putString("state:$shop", HeldSyncEngine.encodeState(next)).commit()
      }
      next
    }
  }

  private fun shop(app: Context): String = LedgerOwner.currentShop(app)

  private fun engine(app: Context): HeldSyncEngine? {
    val s = shop(app)
    if (s.isBlank() || TrainingMode.on) return null
    return synchronized(engines) {
      engines.getOrPut(s) {
        val device = { runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(app).deviceId() }.getOrDefault("") }
        HeldSyncEngine(
          HeldInvoices.of(app), Live(app, device), Prefs(app, s),
          clock = { System.currentTimeMillis() },
          series = { ir.vil3ntec.tohid.sync.DocSeries.current(app) },
          activeKey = { cart?.snapshot()?.key },
          deviceId = device,
        )
      }
    }
  }

  /** `HeldInvoices.of` (دفترِ واقعی، نه آموزشی): هر تغییرِ دفتر ⇐ صف، فقط وقتی پرچم روشن است */
  fun attach(context: Context, store: HeldInvoiceStore) {
    val app = context.applicationContext ?: context
    store.onChange = { before, after, why ->
      if (!TrainingMode.on && engine(app)?.changed(before, after, why) == true) kick(app, force = true)
    }
  }

  /** دور در پس‌زمینه؛ [force] (تغییرِ محلی، خبرِ `held`) بی فاصله */
  fun kick(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext ?: context
    if (!TrainingMode.networkAllowed()) return
    if (!force && now - lastAt < MIN_MS) return
    lastAt = now
    val e = engine(app) ?: return
    scope.launch { runCatching { e.cycle() }; publish(e) }
  }

  private fun publish(e: HeldSyncEngine) { _conflicts.value = runCatching { e.conflicts() }.getOrDefault(emptyMap()) }

  /** پشتیبانِ سرور برای همین دکان روی این دستگاه روشن است (پرچم دیده شده) — خاموش ⇒ همان رفتارِ بی پشتیبان */
  fun claims(context: Context): Boolean =
    !TrainingMode.on && runCatching { engine(context.applicationContext ?: context)?.enabled == true }.getOrDefault(false)

  /** «در دستِ دستگاهِ X» پیش از بازگرداندن؛ خاموش، آموزش یا بی دکان ⇒ `Free` بی هیچ درخواستی */
  suspend fun claim(context: Context, id: String, force: Boolean): HeldClaim {
    val app = context.applicationContext ?: context
    if (TrainingMode.on || !TrainingMode.networkAllowed()) return HeldClaim.Free
    val e = engine(app) ?: return HeldClaim.Free
    //  وضع با `commit()` ِ هم‌گام نوشته می‌شود — نه روی رشتهٔ صفحه
    return try {
      withContext(Dispatchers.IO) { e.claim(id, force) }
    } catch (c: kotlinx.coroutines.CancellationException) {
      throw c
    } catch (_: Exception) {
      HeldClaim.Free
    } finally {
      publish(e)
    }
  }

  /** کاربر «یک فاکتور در دو دستگاه» را دید */
  fun ack(context: Context, id: String) {
    val e = runCatching { engine(context.applicationContext ?: context) }.getOrNull() ?: return
    runCatching { e.ack(id) }
    publish(e)
  }

  /** نگه‌دارندهٔ این فاکتور کسِ دیگری است (به گفتهٔ سرور) ⇒ دور انداختنش «لغوِ فاکتور» می‌خواهد */
  fun othersHeld(context: Context, id: String): Boolean =
    runCatching { engine(context.applicationContext ?: context)?.othersHeld(id) == true }.getOrDefault(false)
}
