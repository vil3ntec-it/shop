package ir.vil3ntec.tohid.data.repo

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.storefront.MarketListing
import ir.vil3ntec.tohid.core.storefront.MarketListingDto
import ir.vil3ntec.tohid.core.storefront.MarketListingRepository
import ir.vil3ntec.tohid.core.storefront.MarketLocationDto
import ir.vil3ntec.tohid.core.storefront.OutboxEntry
import ir.vil3ntec.tohid.core.storefront.PresenceDeclaration
import ir.vil3ntec.tohid.core.storefront.SendOutcome
import ir.vil3ntec.tohid.core.storefront.StorefrontChange
import ir.vil3ntec.tohid.core.storefront.StorefrontOutbox
import ir.vil3ntec.tohid.core.storefront.StorefrontRules
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  صفِ آفلاینِ «ویترین و بازار» — سیم‌کشیِ برنامه دورِ منطقِ خالصِ `StorefrontOutbox` (core).
 *
 *  هر ذخیره (تنظیمات، ارسال و زمان، بازار، اعلامِ حضور) اول این‌جا روی دیسک می‌نشیند و بعد
 *  فرستاده می‌شود؛ «ثبت شد» فقط وقتی به صفحه می‌رسد که سرور ۲xx داده باشد (§۳۱.۲ بندِ ۲).
 *  بی اینترنت ویرایش گم نمی‌شود و با باز شدنِ برنامه یا همین صفحه دوباره فرستاده می‌شود.
 *
 *  پیش از هر PUT وضعِ فعلیِ سرور خوانده می‌شود: اگر از دستگاهِ دیگری عوض شده باشد،
 *  ویرایشِ این دستگاه بی‌پرسش رویش نمی‌نشیند (CONFLICT) — مگر سرور همین حالا همان را
 *  داشته باشد (پاسخِ PUT ِ پیشین گم شده بود ولی رسیده بود) که یعنی «ثبت شد».
 */
object StorefrontQueue {

  private const val PREFS = "tohid-storefront"
  private const val KEY = "outbox-v1"
  /** اعلامِ نرسیدهٔ نسخه‌های پیشین (فقط یک اعلام، بی دکان) */
  private const val LEGACY_PRESENCE = "pending-presence"

  /** گزارشِ یک ردیف پس از پاسخ — صفحه از آن «ثبت شد» یا پیامِ رد را نشان می‌دهد */
  data class Report(val change: StorefrontChange, val outcome: SendOutcome, val message: String)

  val entries = mutableStateOf<List<OutboxEntry>>(emptyList())
  private val _reports = MutableSharedFlow<Report>(extraBufferCapacity = 16)
  val reports: SharedFlow<Report> = _reports
  val sending = mutableStateOf(false)

  private val mutex = Mutex()
  private var loaded = false

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  @Synchronized
  fun load(context: Context) {
    if (loaded) return
    val p = prefs(context)
    var q = StorefrontOutbox.decode(p.getString(KEY, null))
    val shop = ShopRole.shopId(context)
    PresenceDeclaration.decode(p.getString(LEGACY_PRESENCE, null))?.let { legacy ->
      if (shop.isNotBlank()) q = StorefrontOutbox.put(q, OutboxEntry(legacy.eventId, StorefrontChange.PRESENCE, shop,
        PresenceDeclaration.encode(legacy), "", legacy.clientEventAt))
    }
    p.edit().putString(KEY, StorefrontOutbox.encode(q)).remove(LEGACY_PRESENCE).apply()
    entries.value = q
    loaded = true
  }

  @Synchronized
  private fun update(context: Context, f: (List<OutboxEntry>) -> List<OutboxEntry>) {
    load(context)
    val q = f(entries.value)
    //  commit (نه apply): ویرایشِ کاربر پیش از هر فرستادنی روی دیسک باشد
    prefs(context).edit().putString(KEY, StorefrontOutbox.encode(q)).commit()
    entries.value = q
  }

  fun pending(context: Context, change: StorefrontChange): OutboxEntry? {
    load(context)
    return StorefrontOutbox.pendingOf(entries.value, ShopRole.shopId(context), change)
  }

  fun pendingAll(context: Context): List<OutboxEntry> {
    load(context)
    return StorefrontOutbox.pendingFor(entries.value, ShopRole.shopId(context))
  }

  /** گذاشتن در صف؛ دکانِ نامعلوم ⇒ null (چیزی نوشته نمی‌شود تا روی دکانِ اشتباه ننشیند) */
  fun enqueue(context: Context, change: StorefrontChange, body: String, base: String, id: String? = null): OutboxEntry? {
    val shop = ShopRole.shopId(context)
    if (shop.isBlank()) return null
    val now = System.currentTimeMillis()
    val e = OutboxEntry(id ?: StorefrontOutbox.newId(change, now, kotlin.random.Random.nextLong()), change, shop, body, base, now)
    update(context) { StorefrontOutbox.put(it, e) }
    return e
  }

  /** «باز هم بفرست» برای ردیفِ ناسازگار: بی سنجشِ دوباره روی سرور می‌نشیند */
  fun keepMine(context: Context, id: String) = update(context) { StorefrontOutbox.keepMine(it, id, "") }

  fun discard(context: Context, id: String) = update(context) { StorefrontOutbox.discard(it, id) }

  /**
   *  فرستادنِ صفِ همین دکان به ترتیب. با نخستین HOLD می‌ایستد (شبکه نیست؛ بقیه هم نمی‌روند).
   *  `force` = کاربر «بفرست» زده ⇒ پس‌کشی نادیده.
   */
  suspend fun flush(context: Context, force: Boolean = false) {
    load(context)
    if (!Backend.isReady(context)) return
    val shop = ShopRole.shopId(context)
    mutex.withLock {
      sending.value = true
      try {
        val seen = HashSet<String>()
        while (true) {
          val e = StorefrontOutbox.next(entries.value, shop, System.currentTimeMillis(), force) ?: break
          if (!seen.add(e.id)) break
          if (send(context, e) == SendOutcome.HOLD) break
        }
      } finally {
        sending.value = false
      }
    }
  }

  private suspend fun send(context: Context, e: OutboxEntry): SendOutcome {
    val api = Backend.api(context)
    val body: JsonObject? = if (e.change == StorefrontChange.PRESENCE) null
      else runCatching { ApiJson.format.parseToJsonElement(e.body).jsonObject }.getOrNull()
    if (e.change != StorefrontChange.PRESENCE && body == null) return finish(context, e, SendOutcome.REJECTED, "دادهٔ این تغییر روی دستگاه خراب بود؛ دوباره ذخیره کنید.")

    return try {
      when (e.change) {
        StorefrontChange.SETTINGS -> {
          val repo = StorefrontRepository(api)
          val cur = repo.config().valueOrNull()
          if (cur != null && StorefrontOutbox.isConflict(e, settingsBase(cur.settings))) {
            return if (cur.settings != null && settingsBody(draftOf(cur.settings)) == body) {
              StorefrontGate.config.value = cur
              finish(context, e, SendOutcome.CONFIRMED, "تنظیماتِ ویترین روی سرور ثبت شد")
            } else conflict(context, e)
          }
          val saved = ApiJson.decode<StorefrontSettingsDto>(api.put(StorefrontRepository.ROOT, body!!)["settings"] ?: JsonNull)
          StorefrontGate.config.value = StorefrontGate.config.value?.copy(settings = saved)
          return finish(context, e, SendOutcome.CONFIRMED, "تنظیماتِ ویترین روی سرور ثبت شد")
        }
        StorefrontChange.DELIVERY -> {
          val repo = StorefrontDeliveryRepository(api)
          val cur = repo.load().valueOrNull()
          if (cur != null && StorefrontOutbox.isConflict(e, deliveryBase(cur))) {
            return if (sameDelivery(cur, body!!)) finish(context, e, SendOutcome.CONFIRMED, "ارسال و زمان روی سرور ثبت شد")
            else conflict(context, e)
          }
          ApiJson.decode<StorefrontDeliveryDto>(api.put(StorefrontDeliveryRepository.PATH, body!!))
          return finish(context, e, SendOutcome.CONFIRMED, "ارسال و زمان روی سرور ثبت شد")
        }
        StorefrontChange.MARKET -> {
          val cur = MarketListingRepository(api).load().valueOrNull()
          if (cur != null && StorefrontOutbox.isConflict(e, marketBase(cur.listing))) {
            return if (sameMarket(cur.listing, body!!)) finish(context, e, SendOutcome.CONFIRMED, "نمایش در بازار روی سرور ثبت شد")
            else conflict(context, e)
          }
          val saved = MarketListing.parseSaved(api.put(MarketListing.ROOT, body!!))
          return finish(context, e, SendOutcome.CONFIRMED,
            if (saved.listed) "ثبت شد — دکان در بازار نشان داده می‌شود" else "ثبت شد — دکان در بازار نشان داده نمی‌شود")
        }
        StorefrontChange.PRESENCE -> {
          val p = PresenceDeclaration.decode(e.body)
            ?: return finish(context, e, SendOutcome.REJECTED, "اعلامِ ذخیره‌شده خراب بود؛ دوباره اعلام کنید.")
          val r = StorefrontRepository(api).declare(p, "")
          r.onFailure { throw it }
          val res = r.valueOrNull()!!
          StorefrontGate.config.value = StorefrontGate.config.value?.let { c ->
            c.copy(settings = c.settings?.copy(status = res.status, presenceMode = res.status.mode, declaredUntil = res.status.declaredUntil))
          }
          val tz = StorefrontGate.config.value?.settings?.timezone ?: "Asia/Kabul"
          return finish(context, e, SendOutcome.CONFIRMED, when (res.applied) {
            false -> "ثبت شد، ولی وضعیتِ تازه‌تری از دستگاهِ دیگر جلوتر است؛ همان می‌ماند."
            else -> "ثبت شد — زمانِ سرور: " + StorefrontRules.toFa(ir.vil3ntec.tohid.core.storefront.StorefrontOwner.clock(res.serverReceivedAt, tz))
          })
        }
      }
    } catch (c: kotlinx.coroutines.CancellationException) {
      throw c
    } catch (t: Throwable) {
      val f = t as? ApiFailure ?: ApiFailure.fromException(t)
      finish(context, e, StorefrontOutbox.classify(f.status, f.code, f.retryable), f.userMessage)
    }
  }

  private fun conflict(context: Context, e: OutboxEntry): SendOutcome {
    update(context) { StorefrontOutbox.markConflict(it, e) }
    _reports.tryEmit(Report(e.change, SendOutcome.HOLD, StorefrontOutbox.CONFLICT_NOTE))
    //  ناسازگاری جلوی ردیف‌های دیگر را نمی‌گیرد
    return SendOutcome.REJECTED
  }

  private fun finish(context: Context, e: OutboxEntry, outcome: SendOutcome, message: String): SendOutcome {
    update(context) { StorefrontOutbox.afterSend(it, e, outcome, message, System.currentTimeMillis()) }
    _reports.tryEmit(Report(e.change, outcome, message))
    return outcome
  }

  /* ------------------------------ پایه‌ها (نشانِ وضعِ سرور) ------------------------------ */

  fun settingsBase(s: StorefrontSettingsDto?): String = s?.updatedAt?.toString() ?: "none"
  fun marketBase(l: MarketListingDto): String = l.updatedAt?.toString() ?: "none"
  fun deliveryBase(d: StorefrontDeliveryDto): String =
    StorefrontOutbox.fingerprint(ApiJson.format.encodeToString(StorefrontDeliveryDto.serializer(), d))

  /* ------------------------------ تنظیمات: فرم ⇄ بدنه ------------------------------ */

  fun draftOf(s: StorefrontSettingsDto): StorefrontRules.SettingsDraft = StorefrontRules.SettingsDraft(
    slug = s.slug, title = s.title, tagline = s.tagline,
    phone = s.contactPhone.replace(Regex("^\\+93"), "0"), accent = s.accent, notice = s.notice, enabled = s.enabled,
    delivery = s.fulfillment.delivery, pickup = s.fulfillment.pickup,
    fee = StorefrontRules.formatMinor(s.deliveryFeeMinor), minOrder = StorefrontRules.formatMinor(s.minOrderMinor),
    timezone = s.timezone,
    days = (0..6).associateWith { d -> StorefrontRules.formatDay(s.weeklyHours[d.toString()].orEmpty().filter { it.size == 2 }.map { it[0] to it[1] }) },
    closed = s.closedDates.joinToString("، "),
  )

  /** بدنهٔ `PUT /api/shop/storefront` از فرمِ سنجیده؛ فرمِ نادرست ⇒ null */
  fun settingsBody(d: StorefrontRules.SettingsDraft): JsonObject? {
    val c = StorefrontRules.checkSettings(d)
    if (!c.ok) return null
    return buildJsonObject {
      put("slug", d.slug.trim().lowercase())
      put("enabled", d.enabled)
      put("title", d.title.trim())
      put("tagline", d.tagline.trim())
      put("contactPhone", d.phone.trim())
      put("accent", d.accent.trim().lowercase())
      put("notice", d.notice.trim())
      put("timezone", d.timezone.trim())
      put("weeklyHours", StorefrontRepository.weeklyHoursJson(c.hours))
      put("closedDates", JsonArray(c.closedDates.map { JsonPrimitive(it) }))
      put("acceptDelivery", d.delivery)
      put("acceptPickup", d.pickup)
      put("deliveryFeeMinor", c.feeMinor)
      put("minOrderMinor", c.minOrderMinor)
    }
  }

  /** آنچه کاربر می‌بیند وقتی تنظیماتش هنوز در صف است: همان مقدارهای صف روی دادهٔ سرور */
  fun settingsWithPending(s: StorefrontSettingsDto?, pending: OutboxEntry?): StorefrontSettingsDto? {
    val b = pending?.body?.let { runCatching { ApiJson.format.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return s
    val base = s ?: StorefrontSettingsDto()
    fun str(k: String, d: String) = (b[k] as? JsonPrimitive)?.contentOrNull ?: d
    fun bool(k: String, d: Boolean) = (b[k] as? JsonPrimitive)?.booleanOrNull ?: d
    fun long(k: String, d: Long) = (b[k] as? JsonPrimitive)?.longOrNull ?: d
    val hours = runCatching {
      (b["weeklyHours"] as? JsonObject)?.mapValues { (_, v) -> v.jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.int } } }
    }.getOrNull() ?: base.weeklyHours
    val closed = runCatching { (b["closedDates"] as? JsonArray)?.map { it.jsonPrimitive.content } }.getOrNull() ?: base.closedDates
    return base.copy(
      slug = str("slug", base.slug), enabled = bool("enabled", base.enabled), title = str("title", base.title),
      tagline = str("tagline", base.tagline), contactPhone = str("contactPhone", base.contactPhone),
      accent = str("accent", base.accent), notice = str("notice", base.notice), timezone = str("timezone", base.timezone),
      weeklyHours = hours, closedDates = closed,
      fulfillment = StorefrontFulfillmentDto(bool("acceptDelivery", base.fulfillment.delivery), bool("acceptPickup", base.fulfillment.pickup)),
      deliveryFeeMinor = long("deliveryFeeMinor", base.deliveryFeeMinor), minOrderMinor = long("minOrderMinor", base.minOrderMinor),
    )
  }

  /* ------------------------------ بازار ------------------------------ */

  fun marketWithPending(l: MarketListingDto, pending: OutboxEntry?): MarketListingDto {
    val b = pending?.body?.let { runCatching { ApiJson.format.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return l
    val loc = when (val v = b["location"]) {
      null -> l.location
      is JsonObject -> {
        val lat = (v["lat"] as? JsonPrimitive)?.doubleOrNull
        val lng = (v["lng"] as? JsonPrimitive)?.doubleOrNull
        if (lat != null && lng != null) MarketLocationDto(lat, lng) else l.location
      }
      else -> null
    }
    return l.copy(
      listed = (b["listed"] as? JsonPrimitive)?.booleanOrNull ?: l.listed,
      category = (b["category"] as? JsonPrimitive)?.contentOrNull ?: l.category,
      area = (b["area"] as? JsonPrimitive)?.contentOrNull ?: l.area,
      location = loc,
    )
  }

  private fun sameMarket(server: MarketListingDto, body: JsonObject): Boolean {
    val mine = marketWithPending(server.copy(location = server.location), OutboxEntry("x", StorefrontChange.MARKET, "x", body.toString(), "", 0))
    return mine.listed == server.listed && mine.category == server.category && mine.area == server.area &&
      (!body.containsKey("location") || mine.location == server.location)
  }

  /* ------------------------------ ارسال و زمان ------------------------------ */

  fun deliveryWithPending(d: StorefrontDeliveryDto, pending: OutboxEntry?): StorefrontDeliveryDto {
    val b = pending?.body?.let { runCatching { ApiJson.format.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return d
    return runCatching { ApiJson.decode<StorefrontDeliveryDto>(b) }.getOrDefault(d)
  }

  private fun sameDelivery(server: StorefrontDeliveryDto, body: JsonObject): Boolean {
    val mine = runCatching { ApiJson.decode<StorefrontDeliveryDto>(body) }.getOrNull() ?: return false
    fun strip(x: StorefrontDeliveryDto) = x.copy(zones = x.zones.map { it.copy(id = "") }, slotRules = x.slotRules.map { it.copy(id = "") })
    return strip(mine) == strip(server)
  }
}
