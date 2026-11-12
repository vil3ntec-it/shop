package ir.vil3ntec.tohid.core.shopper

import kotlin.math.min
import kotlinx.serialization.Serializable

/*
 *  CRM-09 — دفترِ نشانیِ برنامهٔ خرید ⇄ دفترِ نشانیِ حسابِ مشتری (`/:slug/account/addresses`، CHK-09) و عکسِ دروازه/یادداشتِ
 *  صوتیِ هر نشانی (`/:slug/account/addresses/:id/media/:kind`، پرچمِ `shop.storefront.address_media`). همان درهای «نشانی‌های
 *  من» ِ وب (`store/acct.js`، `store/addr-media.js`)؛ سرورِ تازه‌ای نمی‌خواهد.
 *
 *    بی حساب ────── همه چیز روی همین دستگاه (شناسهٔ `adr-…`، [AddressBook]) — همان رفتارِ پیشین.
 *    با حساب ────── یک دور با یک درخواست در هر گام ([next] ⇒ [send] ⇒ [apply]، هر پاسخ روی حالِ **اکنون**؛ ویرایشِ میانهٔ دور
 *                   گم نمی‌شود): پاک‌شده‌های همین دستگاه (DELETE) ⇐ ویرایش‌های نرفته (PUT) ⇐ خواندنِ دفترِ حساب (GET) ⇐ نشانی‌های
 *                   فقط‌دستگاهی (POST، تا سقفِ ۱۰ ِ سرور؛ همانندِ نشانیِ حساب دوباره فرستاده نمی‌شود).
 *    حسابِ سرور مرجع است: نشانیِ حساب که جای دیگر پاک شد از گوشی هم می‌رود؛ نشانیِ تازهٔ وب/گوشیِ دیگر می‌آید.
 *    خروج ⇒ نشانی‌های حساب از دستگاه پاک ([signedOut])؛ فقط‌دستگاهی‌ها می‌مانند (مثلِ سبدِ حساب، CHK-03).
 *  ⛔ رسانه فقط برای نشانیِ حساب و فقط از دکانی که پرچم دارد؛ بایت هرگز روی دستگاه نگه داشته نمی‌شود. EXIF، نوع و سقف را سرور
 *     می‌سنجد (`lib/address-media.js`)؛ این‌جا فقط سقفِ ضبط (۶۰ ثانیه، همان وب) و «بزرگ‌تر از سقف ⇒ نفرست».
 */

/** کدام رسانه برای این نشانیِ حساب هست (`withMedia` ِ سرور؛ فقط بولی — بایت از درِ خودش) */
@Serializable
data class AddressMediaFlags(val gatePhoto: Boolean = false, val voiceNote: Boolean = false)

/** سقف‌های `config.addressMedia` ِ سرور (`mediaLimits` ِ همان پاسخ)؛ ۰ ⇒ نامعلوم */
@Serializable
data class AddressMediaLimits(val photoMax: Long = 0, val voiceMax: Long = 0)

@Serializable
data class AddressMediaMeta(val kind: String = "", val mime: String = "", val size: Long = 0, val createdAt: Long = 0)

/** پاسخِ `PUT …/media/:kind` (۲۰۱ تازه، ۲۰۰ جایگزین) */
@Serializable
data class AddressMediaPut(val media: AddressMediaMeta? = null, val replaced: Boolean = false)

/** یک گامِ دور: یک درخواست */
sealed class AddressStep {
  data class Drop(val id: String) : AddressStep()
  data class Push(val sent: SavedAddress) : AddressStep()
  data object Pull : AddressStep()
  data class Add(val sent: SavedAddress) : AddressStep()
}

/** حالِ همین دور (فقط حافظه): [full] ⇒ دیگر POST نه؛ [stopped] ⇒ شکستِ گذرا، دورِ بعد از نو */
data class AddressRound(
  val pulled: Boolean = false,
  val full: Boolean = false,
  val stopped: Boolean = false,
  val error: ShopperError? = null,
  val limits: AddressMediaLimits? = null,
)

/** پاسخِ یک گام: [address] برای PUT/POST، [list] برای GET */
data class AddressReply(val ok: Boolean, val address: SavedAddress? = null, val list: AddressList? = null, val error: ShopperError? = null)

object AddressSync {
  /** پیشوندِ شناسهٔ فقط‌دستگاهی (`AddressModel.save`)؛ شناسهٔ سرور ULID ِ بی پیشوند است */
  const val LOCAL = "adr-"

  /** سقفِ گام‌های یک دور (ویرایشِ پیاپیِ میانهٔ دور هم بی‌پایان نمی‌چرخد) */
  const val MAX_STEPS = 40

  fun deviceOnly(id: String): Boolean = id.startsWith(LOCAL)

  fun input(a: SavedAddress): AddressInput = AddressInput(a.label, a.text, a.landmark, a.lat, a.lng)

  /** همان نشانی (برای «دوباره نفرست»): نام، متن، نشانه و پین */
  fun same(a: SavedAddress, b: SavedAddress): Boolean =
    a.label == b.label && a.text == b.text && a.landmark == b.landmark && a.lat == b.lat && a.lng == b.lng

  /** گامِ بعد، یا null (دور تمام شد) */
  fun next(s: ShopperState, r: AddressRound): AddressStep? {
    if (r.stopped) return null
    s.addressGone.firstOrNull()?.let { return AddressStep.Drop(it) }
    s.addresses.firstOrNull { !deviceOnly(it.id) && it.dirty }?.let { return AddressStep.Push(it) }
    if (!r.pulled) return AddressStep.Pull
    if (r.full || s.addresses.count { !deviceOnly(it.id) } >= AddressRules.MAX) return null
    return s.addresses.firstOrNull { deviceOnly(it.id) }?.let { AddressStep.Add(it) }
  }

  /** همان درها با نشستِ حساب (هم‌گام؛ از نخِ پس‌زمینه) */
  fun send(api: ShopperApi, slug: String, session: String, step: AddressStep): AddressReply {
    fun <T> reply(r: ShopperResult<T>, ok: (T) -> AddressReply): AddressReply = when (r) {
      is ShopperResult.Ok -> ok(r.value)
      is ShopperResult.Fail -> AddressReply(false, error = r.error)
    }
    return when (step) {
      is AddressStep.Drop -> reply(api.removeAddress(slug, session, step.id)) { AddressReply(true) }
      is AddressStep.Push -> reply(api.updateAddress(slug, session, step.sent.id, input(step.sent))) { AddressReply(true, address = it) }
      AddressStep.Pull -> reply(api.addressBook(slug, session)) { AddressReply(true, list = it) }
      is AddressStep.Add -> reply(api.addAddress(slug, session, input(step.sent))) { AddressReply(true, address = it) }
    }
  }

  /** بی اینترنت، نشستِ مرده، شلوغی یا خطای سرور ⇒ ایست تا دورِ بعد (هیچ چیز پاک یا کنار گذاشته نمی‌شود) */
  fun transient(e: ShopperError): Boolean = e.network || e.status == 0 || e.status == 401 || e.status == 429 || e.status >= 500

  /** پاسخ ⇒ حالِ تازه روی حالِ **اکنون** ([s] ممکن است از هنگامِ فرستادن عوض شده باشد) */
  fun apply(s: ShopperState, step: AddressStep, reply: AddressReply, r: AddressRound): Pair<ShopperState, AddressRound> {
    val e = reply.error
    if (!reply.ok && (e == null || transient(e))) return s to r.copy(stopped = true, error = e)
    return when (step) {
      //  پاک شد، یا پیش‌تر نبود (۴۰۴)، یا سرور نمی‌پذیرد ⇒ از صف بیرون (هرگز صفِ گیرکرده)
      is AddressStep.Drop -> s.copy(addressGone = s.addressGone - step.id) to r
      is AddressStep.Push -> pushed(s, step.sent, reply, e) to r
      AddressStep.Pull -> {
        val list = reply.list
        if (!reply.ok || list == null) s to r.copy(stopped = true, error = e)
        else merge(s, list.addresses) to r.copy(pulled = true, limits = list.mediaLimits ?: r.limits)
      }
      is AddressStep.Add -> {
        val a = reply.address
        if (reply.ok && a != null) adopt(s, step.sent, a) to r
        //  `too_many_addresses` یا نشانیِ ناپذیرفته ⇒ همین دور دیگر نه؛ نشانی روی دستگاه می‌ماند
        else s to r.copy(full = true, error = e)
      }
    }
  }

  private fun pushed(s: ShopperState, sent: SavedAddress, reply: AddressReply, e: ShopperError?): ShopperState {
    val server = reply.address
    return when {
      //  همان که فرستاده شد هنوز همان است ⇒ نسخهٔ سرور؛ در میانه دوباره عوض شد ⇒ همان «نرفته» برای گامِ بعد
      reply.ok && server != null -> s.copy(addresses = s.addresses.map { c ->
        if (c.id == sent.id && c == sent) server.copy(media = c.media, dirty = false) else c
      })
      //  جای دیگر پاک شده بود ⇒ ویرایشِ این دستگاه گم نمی‌شود: نشانیِ فقط‌دستگاهی و POST ِ دوباره
      e?.status == 404 -> s.copy(addresses = s.addresses.map { c ->
        if (c.id == sent.id) c.copy(id = LOCAL + c.id, dirty = false, media = null) else c
      })
      //  سرور این ویرایش را نمی‌پذیرد ⇒ خواندنِ بعدی نسخهٔ سرور را می‌آورد (صفِ گیرکرده نه)
      else -> s.copy(addresses = s.addresses.map { c -> if (c.id == sent.id) c.copy(dirty = false) else c })
    }
  }

  private fun adopt(s: ShopperState, sent: SavedAddress, server: SavedAddress): ShopperState {
    val cur = s.addresses.firstOrNull { it.id == sent.id }
      //  در میانه پاک شد ⇒ همان نشانیِ تازهٔ سرور هم پاک شود
      ?: return s.copy(addressGone = (s.addressGone + server.id).distinct())
    val next = if (cur == sent) server.copy(dirty = false) else cur.copy(id = server.id, dirty = true, media = null)
    return s.copy(addresses = s.addresses.map { if (it.id == sent.id) next else it })
  }

  /** دفترِ حساب ⇒ دستگاه: نسخهٔ سرور برای نشانی‌های حساب، تازه‌های جای دیگر، و همانندِ نشانیِ حساب ⇐ همان شناسه */
  fun merge(s: ShopperState, remote: List<SavedAddress>): ShopperState {
    val gone = s.addressGone.toSet()
    val byId = remote.associateBy { it.id }
    val synced = s.addresses.filter { !deviceOnly(it.id) }.map { it.id }.toSet()
    val taken = HashSet<String>()
    val out = ArrayList<SavedAddress>()
    for (a in s.addresses) {
      when {
        deviceOnly(a.id) -> {
          val twin = remote.firstOrNull { same(it, a) && it.id !in gone && it.id !in taken }
          when {
            twin == null -> out += a
            twin.id in synced -> Unit   //  همانندِ نشانیِ حسابی که روی دستگاه هست ⇒ نسخهٔ دوم لازم نیست
            else -> { taken += twin.id; out += twin }
          }
        }
        a.dirty -> out += a.copy(media = byId[a.id]?.media ?: a.media)
        else -> byId[a.id]?.let { out += it }   //  نبود ⇒ جای دیگر پاک شد
      }
    }
    val have = out.map { it.id }.toHashSet()
    for (x in remote) if (x.id !in have && x.id !in gone) out += x
    return s.copy(addresses = out)
  }

  /** نوشتنِ دفتر روی دستگاه (افزودن/ویرایش/پاک کردن): نشانیِ حسابِ پاک‌شده ⇐ صفِ DELETE */
  fun written(s: ShopperState, list: List<SavedAddress>): ShopperState {
    val kept = list.map { it.id }.toHashSet()
    val dropped = s.addresses.filter { !deviceOnly(it.id) && it.id !in kept }.map { it.id }
    return s.copy(addresses = list, addressGone = (s.addressGone + dropped).distinct())
  }

  /** خروج: نشانی‌های حساب از دستگاه (در حساب می‌مانند)؛ فقط‌دستگاهی‌ها می‌مانند */
  fun signedOut(s: ShopperState): ShopperState =
    s.copy(addresses = s.addresses.filter { deviceOnly(it.id) }, addressGone = emptyList())

  /**
   *  شناسه‌هایی که در یک گام عوض شدند (POST ⇐ شناسهٔ سرور، همانندِ حساب، PUT ِ ۴۰۴ ⇐ فقط‌دستگاهی): کهنه ⇐ تازه — تا برگهٔ
   *  ویرایشِ باز و نشانیِ برگزیدهٔ برگهٔ ثبت همان نشانی را دنبال کنند (نه نشانیِ دوم)
   */
  fun renamed(before: List<SavedAddress>, after: List<SavedAddress>): Map<String, String> {
    val was = before.map { it.id }.toHashSet()
    val now = after.map { it.id }.toHashSet()
    val fresh = after.filter { it.id !in was }
    return before.filter { it.id !in now }.mapNotNull { a ->
      (fresh.firstOrNull { it.id == LOCAL + a.id || (a.id.startsWith(LOCAL) && same(it, a)) } ?: fresh.firstOrNull { same(it, a) })?.let { a.id to it.id }
    }.toMap()
  }

  /** چند نشانی پس از دورِ کامل هنوز فقط روی همین دستگاه است (دفترِ حساب پر بود) */
  fun leftOnDevice(s: ShopperState): Int = s.addresses.count { deviceOnly(it.id) }

  /** نشستِ ثبت فقط وقتی نشانیِ حساب برگزیده شده (`address.savedId` ⇒ پیوندِ رسانه به سفارش، `linkOrder` ِ سرور) */
  fun checkoutSession(req: CheckoutRequest, session: String?): String? = session?.takeIf { req.address?.savedId != null }
}

/** بخشِ رسانه در برگهٔ نشانی */
enum class AddressMediaGate { HIDDEN, SIGN_IN, SAVE_FIRST, READY }

object AddressMedia {
  const val PHOTO = "gate-photo"
  const val VOICE = "voice-note"

  /** سقفِ ضبطِ یادداشت — همان ۶۰ ثانیهٔ «نشانی‌های من» ِ وب */
  const val VOICE_MAX_MS = 60_000L

  /** [flag] پرچمِ دکانِ همین لحظه؛ بی نشست ⇒ «وارد شوید»؛ نشانیِ فقط‌دستگاهی ⇒ «اول در حساب ذخیره شود» */
  fun gate(flag: Boolean, signedIn: Boolean, id: String?): AddressMediaGate = when {
    !flag -> AddressMediaGate.HIDDEN
    !signedIn -> AddressMediaGate.SIGN_IN
    id == null || AddressSync.deviceOnly(id) -> AddressMediaGate.SAVE_FIRST
    else -> AddressMediaGate.READY
  }

  /** ضبط بایستد؟ ۶۰ ثانیه، یا نزدیکِ سقفِ حجمِ سرور (همان حاشیهٔ [ChatVoice.HEAD]) */
  fun stop(elapsedMs: Long, bytes: Long, cap: Long): Boolean =
    elapsedMs >= VOICE_MAX_MS || (cap > 0 && bytes > 0 && bytes >= cap - min(ChatVoice.HEAD, cap / 4))

  /** بزرگ‌تر از سقفِ سرور ⇒ پیش از فرستادن «بزرگ است» (نه ۴۱۳) */
  fun tooBig(size: Long, cap: Long): Boolean = cap > 0 && size > cap

  /** پس از PUT/DELETE: نشانِ همان نشانی روی دستگاه */
  fun mark(s: ShopperState, id: String, kind: String, has: Boolean): ShopperState = s.copy(addresses = s.addresses.map { a ->
    if (a.id != id) a
    else {
      val m = a.media ?: AddressMediaFlags()
      a.copy(media = if (kind == PHOTO) m.copy(gatePhoto = has) else m.copy(voiceNote = has))
    }
  })
}
