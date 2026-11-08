package ir.vil3ntec.tohid.desktop.keystore

import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.i18n.StrAppData
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  رمزِ رازهای نشست (`SecretBox`: توکن‌های ورود، رازِ دستگاهِ شاگرد، فایل‌های هاب) — AES-256-GCM با کلیدی که
 *  [KekVault] با [KekVault.Slot.SESSION] نگه می‌دارد: همان فایلِ `keys/session.key` ِ پیشین، و پشتِ پرچمِ
 *  `desk.keystore` همان کوچ به کلیددانِ سیستم با همهٔ قاعده‌های KEK ِ دفتر (بایت‌ها هرگز عوض نمی‌شوند، کوچ پس از
 *  بازخوانی، فایلِ ساده تا اجرای بعدی که همین کلید رازی را واقعاً باز کرد، قفل ⇒ همان فایل، پرچمِ خاموش ⇒ برگشتِ فایل).
 *  شکلِ رمزشده (`v1:` + base64(iv ‖ متن ‖ برچسب)) همان پیشین است ⇒ چیزی دوباره رمز نمی‌شود.
 *
 *  ── کلید همین حالا در دسترس نیست ([State.Unavailable]: کلیددان قفل/بی‌جواب و فایلی نیست، نشانِ خراب یا بیگانه) ──
 *   • ⛔ **هرگز کلیدِ تازه** — کلیدِ تازه توکن‌ها، رازِ دستگاهِ شاگرد و صفِ هاب را برای همیشه بی‌صاحب می‌کرد.
 *   • [open] ⇒ `null` (یعنی «نشستی نیست»؛ هرگز استثنا) و [lost] روشن می‌شود تا برنامه پیامِ «دوباره وارد شوید» بدهد.
 *   • [seal] ⇒ فقط در حافظهٔ همین اجرا (`m1:`): ورودِ دوباره تا بستنِ برنامه کار می‌کند و هیچ کلیدی روی دیسک ساخته
 *     نمی‌شود. رازِ رمزشدهٔ پیشین فقط وقتی جایش را می‌دهد که خودِ کاربر دوباره وارد شود.
 *   • [persistent] ⇒ `false`: هاب (کلیدِ TLS، صف) روشن نمی‌شود تا چیزی را با رازی که پس از بستن نمی‌ماند ننویسد.
 *  دفتر، صف و نشانه‌های حساب هرگز از این‌جا دست نمی‌خورند (`SafeSignOut` / `SignOutVault` همان می‌مانند).
 *
 *  هیچ کلیدی ([State.Empty]: نه فایل، نه نشان — نصبِ تازه، یا فایلِ خراب بی هیچ نسخهٔ دیگر) ⇒ کلید با نخستین [seal]
 *  ساخته می‌شود، همان رفتارِ پیشین؛ فایلِ خراب پیش از آن کنار می‌رود (`.bad-<زمان>`)، نه رونویسی.
 *
 *  حالت یک بار در هر اجرا سنجیده می‌شود (هر بار پرسیدن پنجرهٔ Keychain را تکرار می‌کرد). آزمون: `SessionKeyVaultTest`.
 */
class SessionBox(private val vault: KekVault, private val random: SecureRandom = SecureRandom()) {

  sealed class State {
    /** کلید(ها) برای باز کردن: اول کلیدِ درست، فایلِ سادهٔ ناهمخوان فقط نامزدِ دوم — برچسبِ GCM داور است */
    class Ready internal constructor(internal val keys: List<ByteArray>) : State()

    /** هنوز کلیدی نیست — با نخستین [seal] ساخته می‌شود */
    object Empty : State()

    /** @param why برای صفحهٔ امنیت و پیامِ ورودِ دوباره (از `StrAppData`، به زبانِ همان دم) */
    data class Unavailable(val why: String) : State()
  }

  private val lock = Any()
  @Volatile private var state: State? = null
  private val memory = ConcurrentHashMap<String, String>()

  /** رازِ رمزشده‌ای (`v1:`) بود که چون کلید در دسترس نبود باز نشد ⇒ «دوباره وارد شوید» */
  @Volatile var lost: Boolean = false
    private set

  /** حالِ کلید در همین اجرا — بارِ نخست می‌سنجد (شاید کلیددانِ سیستم را صدا بزند) */
  fun state(): State = state ?: synchronized(lock) { state ?: resolve().also { state = it } }

  /** حالِ سنجیده، بی سنجیدن (صفحهٔ امنیت هرگز به‌خاطرِ خودش کلیددانِ سیستم را صدا نمی‌زند) */
  fun peek(): State? = state

  /** همان حالِ [KekVault] برای صفحهٔ امنیت (`null` تا کلیدی ساخته یا خوانده نشده) */
  val vaultStatus: KekVault.Status? get() = vault.status

  /** رمزِ این اجرا پس از بستن هم باز می‌شود؟ (هاب فقط آن‌وقت روشن می‌شود) */
  fun persistent(): Boolean = state() !is State.Unavailable

  private fun resolve(): State = try {
    State.Ready(vault.forRead())
  } catch (e: AtRestException) {
    when {
      e.kind == AtRestException.Kind.KEY_UNAVAILABLE -> State.Unavailable(e.message ?: StrAppData.deskOsMissing)
      //  نشان هست (خراب یا مالِ کلیدِ دیگر) و فایلِ سالمی نیست: شاید با درست شدنِ نشان برگردد ⇒ هرگز کلیدِ تازه کنارش
      vault.osFile.exists() -> State.Unavailable(e.message ?: StrAppData.deskOsBroken)
      //  نه فایل، نه نشان (یا فایلِ خراب بی هیچ نسخهٔ دیگر): چیزی نیست که بی‌صاحب شود
      else -> {
        vault.retireBrokenPlain()
        State.Empty
      }
    }
  } catch (e: Exception) {
    State.Unavailable(StrAppData.deskKeyUnread(e.javaClass.simpleName))
  }

  /** کلید برای رمز کردن؛ `null` ⇒ همین حالا نه (فقط حافظه) */
  private fun sealKey(): ByteArray? = when (val s = state()) {
    is State.Ready -> s.keys.first()
    is State.Unavailable -> null
    State.Empty -> synchronized(lock) {
      when (val again = state) {
        is State.Ready -> again.keys.first()
        is State.Unavailable -> null
        else -> try {
          vault.forWrite().also { state = State.Ready(listOf(it)) }
        } catch (e: AtRestException) {
          state = State.Unavailable(e.message ?: StrAppData.deskKeyMissing)
          null
        } catch (e: Exception) {
          state = State.Unavailable(StrAppData.deskKeyUnread(e.javaClass.simpleName))
          null
        }
      }
    }
  }

  fun seal(plain: String): String {
    val key = sealKey() ?: return inMemory(plain)
    val iv = ByteArray(IV).also { random.nextBytes(it) }
    val c = Cipher.getInstance(AES).apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG, iv)) }
    val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
    return PREFIX + Base64.getEncoder().encodeToString(iv + body)
  }

  /** باز نشد (کلید نیست، فایل دستکاری شده، رازِ اجرای پیشینِ بی‌کلید) ⇒ `null`، یعنی «نشستی نیست»؛ هرگز استثنا */
  fun open(sealed: String): String? {
    if (sealed.startsWith(MEMORY)) return memory[sealed]
    if (!sealed.startsWith(PREFIX)) return null
    val raw = runCatching { Base64.getDecoder().decode(sealed.removePrefix(PREFIX)) }.getOrNull() ?: return null
    if (raw.size <= IV) return null
    val keys = when (val s = state()) {
      is State.Ready -> s.keys
      is State.Unavailable -> {
        lost = true
        return null
      }
      State.Empty -> return null
    }
    for (k in keys) {
      val text = runCatching {
        val c = Cipher.getInstance(AES).apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG, raw, 0, IV)) }
        String(c.doFinal(raw, IV, raw.size - IV), Charsets.UTF_8)
      }.getOrNull() ?: continue
      //  «اجرای بعدیِ موفق»: همین کلید رازی را واقعاً باز کرد ⇒ فایلِ ساده (اگر کوچ تمام است) پاک می‌شود
      vault.opened(k)
      return text
    }
    return null
  }

  private fun inMemory(plain: String): String {
    val handle = MEMORY + KekVault.hex(ByteArray(16).also { random.nextBytes(it) })
    memory[handle] = plain
    return handle
  }

  /** «برنامه بسته و دوباره باز شد» — برای آزمون */
  fun relaunch() = synchronized(lock) {
    vault.relaunch()
    state = null
    lost = false
    memory.clear()
  }

  private companion object {
    const val PREFIX = "v1:"
    const val MEMORY = "m1:"
    const val AES = "AES/GCM/NoPadding"
    const val IV = 12
    const val TAG = 128
  }
}
