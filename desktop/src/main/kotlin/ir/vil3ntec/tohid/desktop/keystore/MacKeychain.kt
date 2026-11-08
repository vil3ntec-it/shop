package ir.vil3ntec.tohid.desktop.keystore

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import ir.vil3ntec.tohid.core.i18n.StrAppData

/**
 *  مک: Keychain ِ کاربر (گذرواژهٔ عمومی، سرویسِ [OsKeyStores.SERVICE]، حسابِ `ref`) از راهِ JNA ِ موجود — نه
 *  `/usr/bin/security` (کلید هرگز در خطِ فرمان نیست، چون اصلاً فرمانی نیست).
 *
 *  `SecKeychain…GenericPassword` ِ قدیمی (هنوز در macOS 15 هست) را برگزیدیم، نه `SecItemAdd` با `CFDictionary`:
 *  چند تابعِ C ِ ساده که JNA بی کتابخانهٔ تازه صدا می‌زند. نبودنِ تابع ⇒ `LinkageError` ⇒ همان فایلِ پیشین.
 *
 *  ⚠️ دسترسی در Keychain مالِ «همین برنامه» است؛ پس از به‌روزرسانیِ برنامهٔ امضانشده مک ممکن است یک بار بپرسد
 *  («Always Allow»). «Deny» ⇒ «کلید در دسترس نیست» تا اجرای بعد، هرگز کلیدِ تازه.
 */
internal class MacKeychain(private val interactive: Boolean) : OsKeyStore {
  override val id: String = "keychain"
  override val label: String get() = StrAppData.deskStoreKeychain

  @Suppress("FunctionName")
  interface Security : Library {
    fun SecKeychainAddGenericPassword(
      keychain: Pointer?,
      serviceNameLength: Int,
      serviceName: ByteArray,
      accountNameLength: Int,
      accountName: ByteArray,
      passwordLength: Int,
      passwordData: ByteArray,
      itemRef: PointerByReference?,
    ): Int

    fun SecKeychainFindGenericPassword(
      keychainOrArray: Pointer?,
      serviceNameLength: Int,
      serviceName: ByteArray,
      accountNameLength: Int,
      accountName: ByteArray,
      passwordLength: IntByReference?,
      passwordData: PointerByReference?,
      itemRef: PointerByReference?,
    ): Int

    fun SecKeychainItemModifyAttributesAndData(itemRef: Pointer, attrList: Pointer?, length: Int, data: ByteArray): Int

    fun SecKeychainItemFreeContent(attrList: Pointer?, data: Pointer?): Int

    fun SecKeychainItemDelete(itemRef: Pointer): Int

    fun SecKeychainSetUserInteractionAllowed(state: Boolean): Int
  }

  @Suppress("FunctionName")
  interface CoreFoundation : Library {
    fun CFRelease(cf: Pointer)
  }

  private val service = OsKeyStores.SERVICE.toByteArray(Charsets.UTF_8)
  private val timeout = if (interactive) 120L else 30L

  /** کار با Keychain؛ در حالتِ بی‌پنجره (آزمون) پنجرهٔ قفل هرگز باز نمی‌شود و همان دم شکست می‌خورد */
  private fun <T> call(block: (MacKeychainLibs) -> T): T = Bounded.run(timeout) {
    val l = libs
    if (!interactive) l.sec.SecKeychainSetUserInteractionAllowed(false)
    try {
      block(l)
    } finally {
      if (!interactive) l.sec.SecKeychainSetUserInteractionAllowed(true)
    }
  }

  override fun protect(ref: String, key: ByteArray): String = call { l ->
    val acc = ref.toByteArray(Charsets.UTF_8)
    var st = l.sec.SecKeychainAddGenericPassword(null, service.size, service, acc.size, acc, key.size, key, null)
    if (st == DUPLICATE) {
      //  همان حساب (همان کلید، چون نام از نشانِ کلید است) از کوچِ نیمه‌کارهٔ پیشین ⇒ بازنویسیِ همان مقدار
      val item = PointerByReference()
      st = l.sec.SecKeychainFindGenericPassword(null, service.size, service, acc.size, acc, null, null, item)
      if (st == 0) {
        val ref0 = item.value
        try {
          st = l.sec.SecKeychainItemModifyAttributesAndData(ref0, null, key.size, key)
        } finally {
          l.cf.CFRelease(ref0)
        }
      }
    }
    if (st != 0) throw failure(st)
    ""
  }

  override fun unprotect(ref: String, blob: String): ByteArray = call { l ->
    val acc = ref.toByteArray(Charsets.UTF_8)
    val len = IntByReference()
    val data = PointerByReference()
    val st = l.sec.SecKeychainFindGenericPassword(null, service.size, service, acc.size, acc, len, data, null)
    if (st != 0) throw failure(st)
    val p = data.value ?: throw failure(NOT_FOUND)
    try {
      p.getByteArray(0, len.value)
    } finally {
      p.clear(len.value.toLong())
      l.sec.SecKeychainItemFreeContent(null, p)
    }
  }

  override fun remove(ref: String) {
    call { l ->
      val acc = ref.toByteArray(Charsets.UTF_8)
      val item = PointerByReference()
      if (l.sec.SecKeychainFindGenericPassword(null, service.size, service, acc.size, acc, null, null, item) == 0) {
        val ref0 = item.value
        try {
          l.sec.SecKeychainItemDelete(ref0)
        } finally {
          l.cf.CFRelease(ref0)
        }
      }
    }
  }

  private fun failure(st: Int): KeyStoreFailure {
    val why = when (st) {
      NOT_FOUND -> StrAppData.deskOsNotFound
      INTERACTION_NOT_ALLOWED, AUTH_FAILED, USER_CANCELED, INTERACTION_REQUIRED -> StrAppData.deskOsLocked
      NO_SUCH_KEYCHAIN, NO_DEFAULT_KEYCHAIN -> StrAppData.deskOsMissing
      else -> StrAppData.deskOsRefused("OSStatus $st")
    }
    return KeyStoreFailure("$why (OSStatus $st)", notFound = st == NOT_FOUND)
  }

  private companion object {
    const val NOT_FOUND = -25300
    const val DUPLICATE = -25299
    const val AUTH_FAILED = -25293
    const val NO_SUCH_KEYCHAIN = -25294
    const val NO_DEFAULT_KEYCHAIN = -25307
    const val INTERACTION_NOT_ALLOWED = -25308
    const val INTERACTION_REQUIRED = -25315
    const val USER_CANCELED = -128
  }

  private val libs: MacKeychainLibs get() = MacLibs.libs
}

/** بارِ تنبلِ چارچوب‌ها: یک بار در هر اجرا؛ شکست ⇒ `LinkageError` در همان صدا (⇐ `Bounded`) */
private object MacLibs {
  /** چارچوب‌های سیستم از macOS 11 روی دیسک نیستند (کاشِ dyld)؛ مسیرِ کامل همان را باز می‌کند */
  private fun <T : Library> load(framework: String, type: Class<T>): T =
    runCatching { Native.load("/System/Library/Frameworks/$framework.framework/$framework", type) }
      .getOrElse { Native.load(framework, type) }

  val libs: MacKeychainLibs by lazy {
    MacKeychainLibs(load("Security", MacKeychain.Security::class.java), load("CoreFoundation", MacKeychain.CoreFoundation::class.java))
  }
}

internal class MacKeychainLibs(val sec: MacKeychain.Security, val cf: MacKeychain.CoreFoundation)
