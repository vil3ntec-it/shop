package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 *  همگام‌سازیِ زندهٔ تنظیماتِ **دکان** (`ShopSettingsKeys.SYNCED`) با `/api/shop/settings-sync` — DATA-SAFETY،
 *  ۱۴۰۵/۰۸/۱۸. پیش‌تر این تنظیم‌ها فقط با پشتیبانِ ابریِ دوازده‌ساعته به سرور می‌رسیدند.
 *
 *  ⛔ **پیش‌فرض خاموش**: پرچمِ سرورِ `shop.settings_sync`. خاموش (یا سرورِ کهنه، ۴۰۴) ⇒ `GET` همان `{enabled:false}` و
 *     هیچ تنظیمی نوشته یا فرستاده نمی‌شود — رفتارِ پیشین بیت‌به‌بیت.
 *  ⛔ فقط کلیدهای «مالِ دکان»؛ چاپگر، کشو، بارکدخوان، صفحه و آموزش هرگز (`ShopSettingsKeys`).
 *  ⛔ قاعدهٔ تعارض و نخستین بار: `ShopSettingsPlan` (آخرین نویسنده با ساعتِ سرور، هر کلید جدا). فرستادن فقط اگر سرور
 *     می‌گوید این کاربر می‌نویسد (`permissions.write`: صاحب و مدیر)؛ سرور هم خودش می‌سنجد.
 *
 *  کی: هر وصلِ سوکتِ زنده (`hello`، حداکثر هر [MIN_MS])، خبرِ `settings` ِ دستگاهِ دیگر (فوری) و هر دورِ کارگرِ
 *  همگام‌سازی (`SyncV1Worker`). سایه و cursor مالِ همین دستگاه‌اند (`tohid-settings-sync`، به نامِ دکان).
 */
object ShopSettingsSync {

  private const val PREFS = "tohid-settings-sync"
  private const val PATH = "/shop/settings-sync"

  /** کمترین فاصلهٔ دو دورِ ناخواسته (نه خبرِ `settings`) */
  const val MIN_MS = 60_000L

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()
  private val json = Json { ignoreUnknownKeys = true }

  /** راهِ شبکه — آزمون‌ها سرورِ ساختگی می‌دهند */
  interface Port {
    suspend fun pull(since: Long): JsonObject?
    suspend fun push(body: JsonObject): JsonObject?
  }

  private class Live(private val app: Context) : Port {
    override suspend fun pull(since: Long): JsonObject? = runCatching { Backend.api(app).get("$PATH?since=$since") }.getOrNull()
    override suspend fun push(body: JsonObject): JsonObject? = runCatching { Backend.api(app).put(PATH, body) }.getOrNull()
  }

  /** گزارشِ یک دور (آزمون و سابقه) */
  class Report(val enabled: Boolean, val applied: Int, val pushed: Int)

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private fun shop(context: Context) = LedgerOwner.currentShop(context)

  /** هر وصلِ سوکت و هر دورِ کارگر؛ [force] (خبرِ `settings`) بی فاصله */
  fun kick(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis()) {
    val app = context.applicationContext
    val s = shop(app)
    if (s.isBlank() || !TrainingMode.networkAllowed()) return
    val at = "at:$s"
    if (!force && now - prefs(app).getLong(at, 0L) < MIN_MS) return
    prefs(app).edit().putLong(at, now).apply()
    scope.launch { runCatching { cycle(app, Live(app)) } }
  }

  /** یک دور: Pull (همهٔ صفحه‌ها) ⇐ نقشه ⇐ نشاندن ⇐ فرستادن ⇐ Pull ِ دوباره اگر چیزی رفت */
  suspend fun cycle(context: Context, port: Port): Report = withContext(Dispatchers.IO) {
    lock.withLock {
      val app = context.applicationContext
      val s = shop(app)
      if (s.isBlank()) return@withLock Report(false, 0, 0)
      val first = once(app, s, port) ?: return@withLock Report(false, 0, 0)
      if (first.pushed == 0) return@withLock first
      //  نوشته‌های خودمان (و هر چه میانِ دو درخواست رسید) با cursor ِ پیشین دوباره خوانده می‌شوند
      val again = once(app, s, port) ?: return@withLock first
      Report(true, first.applied + again.applied, first.pushed + again.pushed)
    }
  }

  private suspend fun once(app: Context, s: String, port: Port): Report? {
    val p = prefs(app)
    val cursor = p.getLong("cursor:$s", 0L)
    val shadow = readShadow(p.getString("shadow:$s", null))
    val rows = ArrayList<ShopSettingsPlan.Remote>()
    var since = cursor
    var canWrite = false
    //  حداکثر چند صفحه در یک دور (۱۰۰۰ ردیف در هر صفحه؛ سقفِ سرور ۵۰۰ کلید). cursor فقط تا آخرین ردیفِ خوانده‌شده
    var pages = 0
    while (pages++ < 5) {
      val body = port.pull(since) ?: return null
      if (body["enabled"]?.jsonPrimitive?.booleanOrNull != true) return null
      canWrite = (body["permissions"] as? JsonObject)?.get("write")?.jsonPrimitive?.booleanOrNull == true
      val page = (body["rows"] as? JsonArray).orEmpty().mapNotNull { remoteOf(it as? JsonObject) }
      rows += page
      since = maxOf(since, page.maxOfOrNull { it.rev } ?: since)
      if (body["more"]?.jsonPrimitive?.booleanOrNull != true || page.isEmpty()) break
    }
    val local = readLocal(app)
    val plan = ShopSettingsPlan.plan(local, shadow, rows, first = cursor == 0L && shadow.isEmpty(), canWrite = canWrite)

    //  نشاندنِ مقدارِ سرور — هر انبار یک `commit`. ⛔ کلیدی که کاربر میانِ خواندن و نوشتن عوض کرد دست نمی‌خورد:
    //  دورِ بعد همان تغییر فرستاده می‌شود (وگرنه مقدارِ سرور آن را بی‌صدا می‌برد)
    val now = readLocal(app)
    for ((store, ids) in plan.apply.keys.groupBy { it.substringBefore('/') }) {
      val edit = app.getSharedPreferences(store, Context.MODE_PRIVATE).edit()
      for (id in ids) {
        if (!same(now[id], local[id])) continue
        val name = id.substringAfter('/')
        when (val raw = ShopSettingsKeys.raw(plan.apply[id])) {
          null -> edit.remove(name)
          is Boolean -> edit.putBoolean(name, raw)
          is String -> edit.putString(name, raw)
          is Int -> edit.putInt(name, raw)
          is Long -> edit.putLong(name, raw)
          is Float -> edit.putFloat(name, raw)
          is Set<*> -> edit.putStringSet(name, raw.filterIsInstance<String>().toSet())
          else -> Unit
        }
      }
      edit.commit()
    }
    //  LOC-03: آغازِ هفته، روزهای تعطیل و پایانِ روزِ رسیده از دستگاهِ دیگر همین حالا در صفحه‌ها، نه پس از بازکردنِ دوباره
    if (plan.apply.keys.any { it in DAY_KEYS }) ShopDayChoice.load(app)
    val nextShadow = LinkedHashMap(plan.shadow)
    var pushed = 0
    if (plan.push.isNotEmpty()) {
      val body = buildJsonObject {
        put("device_id", JsonPrimitive(deviceId(app)))
        put("entries", buildJsonArray {
          for ((id, v) in plan.push) add(buildJsonObject {
            put("store", JsonPrimitive(id.substringBefore('/')))
            put("key", JsonPrimitive(id.substringAfter('/')))
            put("t", JsonPrimitive(v?.t ?: "del"))
            put("v", v?.v ?: JsonNull)
          })
        })
      }
      val ok = port.push(body)
      if (ok != null && ok["enabled"]?.jsonPrimitive?.booleanOrNull == true) {
        for ((id, v) in plan.push) nextShadow[id] = v
        pushed = plan.push.size
      }
    }
    p.edit().putString("shadow:$s", writeShadow(nextShadow)).putLong("cursor:$s", since).commit()
    return Report(true, plan.apply.size, pushed)
  }

  /** کلیدهای `ShopDayChoice` (همان انبارِ `tohid-policy`) */
  private val DAY_KEYS = setOf("tohid-policy/day_end_hour", "tohid-policy/week_start", "tohid-policy/closed_days")

  private fun same(a: ShopSettingsKeys.Value?, b: ShopSettingsKeys.Value?): Boolean =
    if (a == null || b == null) a == null && b == null else a.same(b)

  private fun deviceId(app: Context): String =
    runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(app).deviceId() }.getOrDefault("")

  private fun remoteOf(o: JsonObject?): ShopSettingsPlan.Remote? {
    o ?: return null
    val store = (o["store"] as? JsonPrimitive)?.content ?: return null
    val key = (o["key"] as? JsonPrimitive)?.content ?: return null
    val t = (o["t"] as? JsonPrimitive)?.content ?: return null
    val rev = (o["rev"] as? JsonPrimitive)?.longOrNull ?: return null
    return ShopSettingsPlan.Remote(store, key, t, o["v"], rev)
  }

  /** مقدارِ همین حالای هر کلیدِ «مالِ دکان» روی این دستگاه */
  internal fun readLocal(app: Context): Map<String, ShopSettingsKeys.Value?> {
    val out = HashMap<String, ShopSettingsKeys.Value?>()
    for ((store, keys) in ShopSettingsKeys.SYNCED.groupBy { it.store }) {
      val all = runCatching { app.getSharedPreferences(store, Context.MODE_PRIVATE).all }.getOrNull().orEmpty()
      for (k in keys) out[k.id] = ShopSettingsKeys.encode(k, all[k.name])
    }
    return out
  }

  private fun readShadow(text: String?): Map<String, ShopSettingsKeys.Value?> {
    val o = runCatching { json.parseToJsonElement(text ?: return emptyMap()).jsonObject }.getOrNull() ?: return emptyMap()
    val out = HashMap<String, ShopSettingsKeys.Value?>()
    for ((id, e) in o) {
      val k = ShopSettingsKeys.SYNCED.firstOrNull { it.id == id } ?: continue
      val x = e as? JsonObject
      out[id] = x?.let { ShopSettingsKeys.decode(k, (it["t"] as? JsonPrimitive)?.content.orEmpty(), it["v"]) }
    }
    return out
  }

  private fun writeShadow(m: Map<String, ShopSettingsKeys.Value?>): String = buildJsonObject {
    for ((id, v) in m) put(id, if (v == null) JsonNull else buildJsonObject { put("t", JsonPrimitive(v.t)); put("v", v.v) })
  }.toString()
}
