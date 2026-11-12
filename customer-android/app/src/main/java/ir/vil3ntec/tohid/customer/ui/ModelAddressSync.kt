package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.shopper.AddressMedia
import ir.vil3ntec.tohid.core.shopper.AddressMediaGate
import ir.vil3ntec.tohid.core.shopper.AddressMediaLimits
import ir.vil3ntec.tohid.core.shopper.AddressRound
import ir.vil3ntec.tohid.core.shopper.AddressSync
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.MapPinFeature
import ir.vil3ntec.tohid.core.shopper.PinMap
import ir.vil3ntec.tohid.core.shopper.PlaceText
import ir.vil3ntec.tohid.core.shopper.SavedAddress
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import kotlinx.coroutines.Job

/*
 *  برنامهٔ خرید — CRM-09: دفترِ نشانیِ حساب و عکسِ دروازه/یادداشتِ صوتیِ هر نشانی (فایلِ جدا تا مدل از سقفِ اندازه نگذرد).
 *  قاعده‌ها همه در core ([AddressSync]، [AddressMedia])؛ این‌جا فقط ترتیبِ درخواست‌ها، حالِ صفحه و میکروفونِ سکو.
 *  ⛔ بایتِ عکس و صدا فقط در حافظهٔ همین اجرا و فقط برای نشانیِ باز؛ هرگز روی دستگاه نوشته نمی‌شود.
 */

/** حالِ دفترِ نشانیِ حساب و رسانهٔ نشانیِ باز در همین اجرا (کنارِ [AccountBox]) */
class AddressBox {
  internal var running = false
  internal var again = false
  /** پیامِ زیرِ «نشانی‌های من» (هنوز نرسیده / دفترِ حساب پر) */
  var note: String? by mutableStateOf(null)
    internal set
  var limits: AddressMediaLimits? by mutableStateOf(null)
    internal set

  // ── رسانهٔ نشانیِ باز ──
  /** نشانی‌ای که برگه‌اش باز است */
  var openId: String? by mutableStateOf(null)
    internal set
  var busy: Boolean by mutableStateOf(false)
    internal set
  var mediaNote: String? by mutableStateOf(null)
    internal set
  /** عکسِ ذخیره‌شدهٔ همین نشانی (فقط برای دیدن؛ از درِ خودش) */
  var photo: ByteArray? by mutableStateOf(null)
    internal set
  var recording: Boolean by mutableStateOf(false)
    internal set
  /** یادداشتِ ضبط‌شده و هنوز نفرستاده */
  var take: ByteArray? by mutableStateOf(null)
    internal set
  var elapsed: Long by mutableStateOf(0L)
    internal set
  internal var startedAt = 0L
  internal var micTurn = 0
  internal var photoJob: Job? = null
}

// ── دفترِ نشانیِ حساب ──

/** دکانِ همین لحظه با پرچمِ «انتخاب روی نقشه» (و الگوی درستِ تکه‌ها) */
fun CustomerModel.mapFeature(): MapPinFeature? = PinMap.usable(store.valueOrNull()?.features?.mapPin)

/**
 *  یک دورِ هم‌گامیِ دفترِ نشانی با حساب از راهِ [slug] (ورود به دکان، ورود به حساب، هر نوشتنِ دفتر). بی نشست یا دکانِ بی حساب
 *  ⇒ هیچ (همه روی دستگاه)؛ دوری در راه است ⇒ پس از آن یک دورِ دیگر.
 */
fun CustomerModel.syncAddresses(slug: String = storeSlug) {
  val s = signedIn() ?: return
  if (slug.isBlank() || account.features[slug]?.addresses != true) return
  val box = account.places
  if (box.running) {
    box.again = true
    return
  }
  box.running = true
  box.again = false
  addressStep(slug, s.session, AddressRound(), 0)
}

private fun CustomerModel.addressStep(slug: String, session: String, r: AddressRound, n: Int) {
  val step = if (n >= AddressSync.MAX_STEPS || signedIn()?.session != session) null else AddressSync.next(saved, r)
  if (step == null) {
    addressesDone(slug, r)
    return
  }
  request({ ShopperResult.Ok(AddressSync.send(api, slug, session, step)) }) { res ->
    val reply = res.valueOrNull() ?: return@request addressesDone(slug, r.copy(stopped = true))
    //  نشست در این میان بسته شد ⇒ پاسخ روی دفترِ بی حساب نمی‌نشیند
    if (signedIn()?.session != session) return@request addressesDone(slug, r.copy(stopped = true))
    val before = saved.addresses
    val (next, round) = AddressSync.apply(saved, step, reply, r)
    if (next != saved) persist(next)
    AddressSync.renamed(before, next.addresses).forEach { (old, new) -> book.follow(old, new); if (form.addressId == old) updateForm(form.copy(addressId = new)) }
    addressStep(slug, session, round, n + 1)
  }
}

private fun CustomerModel.addressesDone(slug: String, r: AddressRound) {
  val box = account.places
  box.running = false
  r.limits?.let { box.limits = it }
  val pending = saved.addressGone.isNotEmpty() || saved.addresses.any { it.dirty || AddressSync.deviceOnly(it.id) }
  val left = AddressSync.leftOnDevice(saved)
  box.note = when {
    signedIn() == null -> null
    r.stopped && pending -> PlaceText.addressesLater
    !r.stopped && left > 0 -> PlaceText.addressesLeft(left)
    else -> null
  }
  if (box.again) syncAddresses(slug)
}

// ── عکسِ دروازه و یادداشتِ صوتی ──

/** دکانی که درِ رسانه را دارد: همین دکان با پرچمِ `shop.storefront.address_media` */
private fun CustomerModel.mediaSlug(): String? = storeSlug.takeIf { it.isNotBlank() && account.features[it]?.addressMedia == true }

fun CustomerModel.mediaGate(id: String?): AddressMediaGate = AddressMedia.gate(mediaSlug() != null, signedIn() != null, id)

fun CustomerModel.addressOf(id: String?): SavedAddress? = id?.let { i -> saved.addresses.firstOrNull { it.id == i } }

/** برگهٔ نشانی باز شد: حالِ رسانهٔ نشانیِ پیشین پاک؛ عکسِ ذخیره‌شده فقط اگر سرور گفته هست */
fun CustomerModel.openAddressMedia(id: String?) {
  val box = account.places
  closeAddressMedia()
  box.openId = id
  val slug = mediaSlug() ?: return
  val s = signedIn() ?: return
  if (id == null || mediaGate(id) != AddressMediaGate.READY || addressOf(id)?.media?.gatePhoto != true) return
  box.photoJob = request({ api.addressMediaBytes(slug, s.session, id, AddressMedia.PHOTO) }) { r ->
    if (box.openId == id) box.photo = r.valueOrNull()
  }
}

/** برگه بسته شد: میکروفون بسته، بایت‌ها از حافظه */
fun CustomerModel.closeAddressMedia() {
  val box = account.places
  if (box.recording) voice?.cancel()
  box.micTurn++
  box.photoJob?.cancel()
  box.photoJob = null
  box.openId = null
  box.busy = false
  box.mediaNote = null
  box.photo = null
  box.recording = false
  box.take = null
  box.elapsed = 0
}

private inline fun CustomerModel.withMedia(id: String, go: (slug: String, session: String) -> Unit) {
  val slug = mediaSlug()
  val s = signedIn()
  if (slug == null || s == null || mediaGate(id) != AddressMediaGate.READY || account.places.busy) return
  go(slug, s.session)
}

/** عکسِ آمادهٔ گزینشگرِ سکو ([ChatPhoto.main]: JPEG ِ بی EXIF) ⇒ `PUT …/media/gate-photo` */
fun CustomerModel.sendGatePhoto(id: String, photo: ChatPhoto?) {
  val box = account.places
  if (photo == null) return
  if (AddressMedia.tooBig(photo.main.size.toLong(), box.limits?.photoMax ?: 0)) {
    box.mediaNote = PlaceText.amTooLarge
    return
  }
  withMedia(id) { slug, session ->
    box.busy = true
    box.mediaNote = PlaceText.amSending
    request({ api.putAddressMedia(slug, session, id, AddressMedia.PHOTO, photo.main) }) { r ->
      box.busy = false
      if (r is ShopperResult.Ok) {
        persist(AddressMedia.mark(saved, id, AddressMedia.PHOTO, true))
        if (box.openId == id) box.photo = photo.main
        box.mediaNote = PlaceText.amSent
      } else {
        box.mediaNote = r.errorOrNull()?.let { PlaceText.mediaError(it) }
      }
    }
  }
}

fun CustomerModel.deleteAddressMedia(id: String, kind: String) {
  val box = account.places
  withMedia(id) { slug, session ->
    box.busy = true
    request({ api.removeAddressMedia(slug, session, id, kind) }) { r ->
      box.busy = false
      if (r is ShopperResult.Ok) {
        persist(AddressMedia.mark(saved, id, kind, false))
        if (kind == AddressMedia.PHOTO && box.openId == id) box.photo = null
        box.mediaNote = PlaceText.amDeleted
      } else {
        box.mediaNote = r.errorOrNull()?.let { PlaceText.mediaError(it) }
      }
    }
  }
}

/** میکروفونِ سکو هست؟ (آیفون هنوز نه ⇒ ردیفِ یادداشت فقط «پاک کردن» ِ آن‌چه از وب آمده) */
fun CustomerModel.canRecordAddressVoice(): Boolean = voice?.canRecord == true

/** «ضبطِ یادداشت»: اجازه فقط با همین زدن؛ پاسخِ دیرِ میکروفون پس از بستنِ برگه ⇒ میکروفون بسته */
fun CustomerModel.startAddressVoice(id: String) {
  val box = account.places
  val d = voice ?: return
  if (box.recording || box.busy || mediaGate(id) != AddressMediaGate.READY) return
  val turn = ++box.micTurn
  box.take = null
  box.mediaNote = null
  d.record { err ->
    if (box.micTurn != turn || box.openId != id) {
      if (err == null) d.cancel()
      return@record
    }
    if (err != null) {
      box.mediaNote = PlaceText.amMic
      return@record
    }
    box.recording = true
    box.startedAt = clock()
    box.elapsed = 0
    box.mediaNote = PlaceText.amRecording
  }
}

/** هنگامِ ضبط (صفحه هر ۲۵۰ms): ۶۰ ثانیه یا نزدیکِ سقفِ حجم ⇒ ایستادن */
fun CustomerModel.tickAddressVoice() {
  val box = account.places
  if (!box.recording) return
  box.elapsed = clock() - box.startedAt
  if (AddressMedia.stop(box.elapsed, voice?.recorded() ?: 0, box.limits?.voiceMax ?: 0)) stopAddressVoice()
}

/** «پایانِ ضبط» ⇒ آماده برای «فرستادن» یا «لغو» */
fun CustomerModel.stopAddressVoice() {
  val box = account.places
  if (!box.recording) return
  box.recording = false
  val bytes = voice?.finish()
  box.take = bytes?.takeIf { it.isNotEmpty() }
  box.mediaNote = when {
    box.take == null -> PlaceText.amMic
    AddressMedia.tooBig(bytes!!.size.toLong(), box.limits?.voiceMax ?: 0) -> PlaceText.amTooLarge
    else -> PlaceText.amReady
  }
}

fun CustomerModel.cancelAddressVoice() {
  val box = account.places
  if (box.recording) voice?.cancel()
  box.micTurn++
  box.recording = false
  box.take = null
  box.mediaNote = null
}

/** «فرستادن» ⇒ `PUT …/media/voice-note`؛ بزرگ‌تر از سقف ⇒ فقط «لغو» */
fun CustomerModel.sendAddressVoice(id: String) {
  val box = account.places
  val take = box.take ?: return
  if (AddressMedia.tooBig(take.size.toLong(), box.limits?.voiceMax ?: 0)) {
    box.mediaNote = PlaceText.amTooLarge
    return
  }
  withMedia(id) { slug, session ->
    box.busy = true
    box.mediaNote = PlaceText.amSending
    request({ api.putAddressMedia(slug, session, id, AddressMedia.VOICE, take) }) { r ->
      box.busy = false
      if (r is ShopperResult.Ok) {
        persist(AddressMedia.mark(saved, id, AddressMedia.VOICE, true))
        if (box.take === take) box.take = null
        box.mediaNote = PlaceText.amSent
      } else {
        box.mediaNote = r.errorOrNull()?.let { PlaceText.mediaError(it) }
      }
    }
  }
}
