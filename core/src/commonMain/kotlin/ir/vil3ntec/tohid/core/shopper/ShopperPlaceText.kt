package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperText.asIs
import ir.vil3ntec.tohid.core.shopper.ShopperText.digits
import ir.vil3ntec.tohid.core.shopper.ShopperText.t

/**
 *  نوشته‌های «جا» ِ برنامهٔ خرید (CHK-08 نقشه، CRM-09 دفترِ نشانیِ حساب و رسانهٔ آن) — فایلِ جدا چون [ShopperText] به سقفِ
 *  اندازه رسیده؛ همان [t]/[asIs] ِ آن و همان واژه‌های وب (`kit/i18n/map.*.json`، کلیدهای `am.*` ِ `acct.*.json`).
 *  آزمون: `PlaceTextCoverageTest` (هر سه زبان پُر، انگلیسی بی حرفِ فارسی، مقدارِ جای خالی در دری دست‌نخورده).
 */
object PlaceText {
  // ── نقشه (همان `map.*` ِ وب) ──
  val mapOpen get() = t("انتخاب روی نقشه", "Choose on map", "په نقشه کې وټاکئ")
  val mapView get() = t("نقشه برای پینِ نشانی", "Map for the address pin", "د پتې د پن لپاره نقشه")
  val mapHelp get() = t(
    "نقشه را بکشید یا با دکمه‌ها جابه‌جا کنید تا نشانهٔ وسط روی درِ خانه بیفتد؛ سپس «همین‌جا» را بزنید.",
    "Drag the map or use the buttons until the centre mark is on the door, then press “Here”.",
    "نقشه کش کړئ یا تڼۍ وکاروئ ترڅو منځنۍ نښه د کور پر دروازه ولوېږي؛ بیا «همدلته» ووهئ.",
  )
  val mapIn get() = t("نزدیک‌تر", "Zoom in", "نږدې")
  val mapOut get() = t("دورتر", "Zoom out", "لرې")
  val mapPick get() = t("همین‌جا", "Here", "همدلته")
  val mapClose get() = t("بستنِ نقشه", "Close map", "نقشه وتړئ")
  val mapNorth get() = t("بالا", "Up", "پورته")
  val mapSouth get() = t("پایین", "Down", "ښکته")
  val mapWest get() = t("چپ", "Left", "کیڼ")
  val mapEast get() = t("راست", "Right", "ښي")
  fun mapPicked(coords: String) = t("پین روی نقشه برگزیده شد (${asIs(digits(coords))}).", "Pin chosen on the map ($coords).", "پن پر نقشه وټاکل شو (${digits(coords)}).")
  fun mapWhere(coords: String) = t("وسطِ نقشه: ${asIs(digits(coords))}", "Map centre: $coords", "د نقشې منځ: ${digits(coords)}")
  fun mapSource(name: String) = t("دادهٔ نقشه: © ${asIs(name)}", "Map data: © $name", "د نقشې معلومات: © $name")
  val mapFail get() = t(
    "نقشه نیامد (اینترنت یا سرورِ نقشه)؛ «موقعیتِ فعلیِ من» یا نشانی بس است.",
    "The map did not load (internet or map server); your current location or the address is enough.",
    "نقشه رانغله (انټرنېټ یا د نقشې سرور)؛ «زما اوسنی ځای» یا پته بس ده.",
  )

  // ── دفترِ نشانیِ حساب (CRM-09) ──
  val addressesAccount get() = t(
    "این نشانی‌ها همراهِ حسابِ شما در همهٔ دستگاه‌ها و ویترینِ وب‌اند.",
    "These addresses follow your account on all your devices and in the web shop.",
    "دا پتې ستاسو له حساب سره په ټولو وسیلو او وېب دوکان کې دي.",
  )
  fun addressesLeft(n: Int) = t(
    "${asIs(ShopperText.num(n.toLong()))} نشانی فقط روی همین گوشی ماند؛ دفترِ حساب پر است (حداکثر ۱۰).",
    "$n address(es) stayed on this phone only; your account's address book is full (10 at most).",
    "${ShopperText.num(n.toLong())} پته یوازې په دې موبایل کې پاتې شوه؛ د حساب د پتو کتاب ډک دی (تر ۱۰).",
  )
  val addressesLater get() = t(
    "نشانی‌ها هنوز به حسابِ شما نرسیده‌اند؛ با اینترنت خودش دوباره می‌فرستد.",
    "Your addresses haven't reached your account yet; they will be sent again when you're online.",
    "ستاسو پتې لا حساب ته نه دي رسېدلې؛ له انټرنېټ سره به بیا ولېږل شي.",
  )

  // ── عکسِ دروازه و یادداشتِ صوتی (همان `am.*` ِ وب) ──
  val amNote get() = t(
    "اختیاری: پیک با عکسِ دروازه و چند جملهٔ شما خانه را زودتر پیدا می‌کند. فقط دکانی که با این نشانی از آن خرید می‌کنید می‌بیند.",
    "Optional: a gate photo and a few words help the courier find you faster. Only shops you order from with this address can see them.",
    "اختیاري: د دروازې انځور او ستاسو څو خبرې پیک سره کور ژر موندلو کې مرسته کوي. یوازې هغه دوکان یې ویني چې تاسو له دې پتې سره ترې پېرود کوئ.",
  )
  val amSignIn get() = t(
    "برای افزودنِ عکسِ دروازه یا یادداشتِ صوتی، اول در برگهٔ دکان وارد حسابِ خود شوید.",
    "To add a gate photo or a voice note, first sign in to your account on the shop page.",
    "د دروازې انځور یا غږيز یادښت ورزیاتولو لپاره، لومړی د دوکان په پاڼه کې خپل حساب ته ننوځئ.",
  )
  val amSaveFirst get() = t(
    "عکسِ دروازه و یادداشتِ صوتی پس از رسیدنِ این نشانی به حسابِ شما افزوده می‌شوند؛ اول «ذخیره» را بزنید.",
    "A gate photo and voice note can be added once this address reaches your account; press “Save” first.",
    "د دروازې انځور او غږيز یادښت هغه وخت ورزیاتېږي چې دا پته ستاسو حساب ته ورسېږي؛ لومړی «خوندي» ووهئ.",
  )
  val amPhoto get() = t("عکسِ دروازه", "Gate photo", "د دروازې انځور")
  val amVoice get() = t("یادداشتِ صوتی", "Voice note", "غږيز یادښت")
  val amAdd get() = t("افزودنِ عکس", "Add photo", "انځور ورزیاتول")
  val amChange get() = t("عوض کردنِ عکس", "Change photo", "انځور بدلول")
  val amDelete get() = t("پاک کردن", "Delete", "ړنګول")
  val amSaved get() = t("ذخیره شده", "Saved", "خوندي شوی")
  val amNone get() = t("ندارد", "None", "نشته")
  val amRecord get() = t("ضبطِ یادداشت", "Record a note", "یادښت ثبتول")
  val amRerecord get() = t("ضبطِ دوباره", "Record again", "بیا ثبتول")
  val amStop get() = t("پایانِ ضبط", "Stop recording", "ثبت پای ته رسول")
  val amRecording get() = t("در حالِ ضبط… (حداکثر ۶۰ ثانیه)", "Recording… (up to 60 seconds)", "ثبت روان دی… (تر ۶۰ ثانیو)")
  val amReady get() = t("یادداشت ضبط شد؛ بفرستید یا لغو کنید.", "Note recorded — send it or cancel.", "یادښت ثبت شو؛ ویې لېږئ یا لغوه یې کړئ.")
  val amSend get() = t("فرستادن", "Send", "لېږل")
  val amCancel get() = t("لغو", "Cancel", "لغوه")
  val amSending get() = t("در حالِ فرستادن…", "Sending…", "لېږل کېږي…")
  val amSent get() = t("ذخیره شد.", "Saved.", "خوندي شو.")
  val amDeleted get() = t("پاک شد.", "Deleted.", "ړنګ شو.")
  val amMic get() = t("میکروفون در دسترس نیست یا اجازه داده نشد.", "The microphone is not available or permission was not given.", "مایکروفون نشته یا اجازه ورنکړل شوه.")
  val amTooLarge get() = t("فایل بزرگ‌تر از حدِ مجاز است.", "The file is larger than allowed.", "فایل له اجازې څخه لوی دی.")

  /** خطای درهای رسانه: کدهای `am.err.*` ِ وب، وگرنه همان [ShopperText.error] */
  fun mediaError(e: ShopperError): String = when (e.code) {
    "unsupported_media" -> t("این فایل پذیرفته نیست؛ عکس یا صدای ضبط‌شده بفرستید.", "This file is not accepted; send a photo or a recorded voice note.", "دا فایل نه منل کېږي؛ انځور یا ثبت شوی غږ ولېږئ.")
    "too_large", "image_too_large_dimensions" -> amTooLarge
    "address_not_found" -> t("این نشانی دیگر نیست.", "This address no longer exists.", "دا پته نوره نشته.")
    "address_media_disabled" -> t("این بخش در این ویترین نیست.", "This isn't available in this shop.", "دا برخه په دې دوکان کې نشته.")
    else -> ShopperText.error(e)
  }
}
