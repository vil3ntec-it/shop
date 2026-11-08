package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.desktop.keystore.DeskKeystoreFlag
import ir.vil3ntec.tohid.desktop.keystore.KekVault
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStore
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStores
import ir.vil3ntec.tohid.desktop.keystore.SessionBox
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 *  رمزِ محلیِ رازها (توکنِ نشست، رازِ دستگاهِ شاگرد، فایل‌های هاب) — AES-256-GCM.
 *
 *  کلید در `<پوشهٔ داده>/keys/session.key` است با اجازهٔ فقط-صاحب (روی
 *  لینوکس و مک ۶۰۰؛ روی ویندوز پوشهٔ `LOCALAPPDATA` خودش مالِ همان
 *  کاربر است). جدا از فایلِ تنظیمات، تا پشتیبان یا کپیِ تنظیمات نشست را
 *  با خودش نبرد. پشتِ پرچمِ `desk.keystore` (پیش‌فرض خاموش) همان کلید با
 *  DPAPI ویندوز / Keychain مک / Secret Service لینوکس، با همان سازوکارِ KEK ِ
 *  دفتر (`keystore/KekVault.kt`، مدخل و نامِ جدا) — منطق در `keystore/SessionBox.kt`.
 *
 *  باز نشد (کلید عوض شده، فایل دستکاری شده، کلیددان قفل) ⇒ `null`، یعنی
 *  «نشستی نیست» — کاربر دوباره وارد می‌شود؛ هیچ‌وقت استثنا، و هرگز کلیدِ
 *  تازه‌ای که رازهای پیشین را بی‌صاحب کند.
 */
object SecretBox {

  /** آزمون: کلیددانِ ساختگی (یا «هیچ») به‌جای کلیددانِ سیستم؛ خودِ `null` ⇒ همان سیستم */
  @Volatile internal var storeOverride: (() -> OsKeyStore?)? = null

  /** یک نمونه در هر اجرای برنامه (کوچ و «اجرای بعدیِ موفق» به همین بسته‌اند) */
  internal val box: SessionBox by lazy {
    SessionBox(
      KekVault(
        dir = File(DesktopContext.root, "keys"),
        flag = { DeskKeystoreFlag.on(DesktopContext.root) },
        store = { storeOverride.let { if (it != null) it() else OsKeyStores.forThisOs(slot = KekVault.Slot.SESSION) } },
        restrict = ::restrict,
        slot = KekVault.Slot.SESSION,
      ),
    )
  }

  /** همان اجازهٔ فقط-صاحبِ پیشین: فایل ۶۰۰، پوشه ۷۰۰ (روی ویندوز بی‌اثر) */
  private fun restrict(f: File) {
    runCatching {
      Files.setPosixFilePermissions(f.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
      Files.setPosixFilePermissions(
        f.absoluteFile.parentFile.toPath(),
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
      )
    }
  }

  fun seal(plain: String): String = box.seal(plain)

  fun open(sealed: String): String? = box.open(sealed)

  /** رازِ این اجرا پس از بستن هم باز می‌شود؟ نه ⇒ هاب روشن نمی‌شود (کلیدِ TLS و صفش را با رازِ گذرا نمی‌نویسد) */
  fun persistent(): Boolean = box.persistent()

  /** پیامِ «دوباره وارد شوید» اگر نشستِ ذخیره‌شده‌ای بود که چون کلید در دسترس نبود باز نشد؛ وگرنه `null` */
  fun lostNotice(): String? {
    val s = box.state() as? SessionBox.State.Unavailable ?: return null
    return if (box.lost) StrAppData.deskSessionLost(s.why) else null
  }

  /**
   *  حالِ کلیدِ نشست برای صفحهٔ امنیت (کنارِ حالِ کلیدِ دفتر در `AtRestKeys.note`) — فقط آنچه همین اجرا سنجیده
   *  (`Main` در آغاز می‌سنجد)؛ خودش کلیددانِ سیستم را صدا نمی‌زند.
   */
  fun note(): String {
    val state = box.peek()
    if (state is SessionBox.State.Unavailable) return StrAppData.deskSessionUnavailable(state.why)
    return when (val s = box.vaultStatus) {
      is KekVault.Status.Protected -> StrAppData.deskSessionOs(OsKeyStores.label(s.store)) + when (s.plain) {
        KekVault.Plain.KEPT -> StrAppData.deskSessionPlainPending
        KekVault.Plain.STRAY -> StrAppData.deskOsPlainStray
        KekVault.Plain.NONE -> ""
      }
      is KekVault.Status.Fallback -> StrAppData.deskSessionFallback(s.reason)
      KekVault.Status.Off -> StrAppData.deskSessionFile
      null -> StrAppData.deskSessionNone
    }
  }

  /** آزمون: «برنامه بسته و دوباره باز شد» برای کلیدِ نشست */
  internal fun relaunchForTest() = box.relaunch()
}
