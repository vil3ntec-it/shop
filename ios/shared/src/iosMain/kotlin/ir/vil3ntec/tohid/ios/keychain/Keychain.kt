package ir.vil3ntec.tohid.ios.keychain

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import java.net.toByteArray
import java.net.toNSData

/*
 *  کلیددانِ iOS (Keychain) برای چند بایتِ رازِ برنامه — کلیدِ اصلیِ دفترِ رمزدار (`AtRestKeysIos`)،
 *  توکنِ ورود و آینهٔ هویتِ دستگاه (`IosDeviceIdentity`: همین «به دستگاهِ دیگر نمی‌رود» بازیابی را می‌شناسد). «پس از نخستین بازگشاییِ دستگاه، فقط همین دستگاه»
 *  (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`): نه در پشتیبانِ iCloud، نه انتقال به گوشیِ تازه —
 *  همان قاعدهٔ Android Keystore. خطا ⇐ [KeychainException] با کدِ OSStatus، هرگز «خالی» ِ بی‌صدا.
 */
class KeychainException(val status: Int, message: String) : Exception("$message (OSStatus $status)")

object Keychain {
  const val SERVICE = "ir.vil3ntec.tohid"

  /**
   *  فقط آزمون‌های شبیه‌ساز (`CoreFlowsE2EIosTest`، `DataSafetyIosTest`، `DeviceIdentityIosTest`): فرآیندِ بی بستهٔ آزمون
   *  (`test.kexe`) کلیددانِ iOS ندارد (OSStatus -25291، حتی با `e2e/test.entitlements`)، پس کلیددانی درونِ حافظهٔ همان فرآیند.
   *  در برنامه همیشه null و هر قلم در کلیددانِ iOS.
   */
  internal var memory: HashMap<String, ByteArray>? = null

  /*
   *  قفلِ «برگ» ِ همین کلیددانِ حافظه، نه `synchronized` ِ سراسریِ `ios/jvm/Jvm.kt`: درونش هیچ قفلِ دیگری گرفته نمی‌شود، پس
   *  ترتیبِ قفل‌ها وارونه نمی‌شود. با قفلِ سراسری، خواندنِ توکن روی رشتهٔ اصلی (درونِ `runLock` ِ Compose) پشتِ کارگری می‌ماند
   *  که قفلِ سراسری را داشت و منتظرِ همان `runLock` بود — گیرِ گاه‌به‌گاهِ `10-online-order` در #1037 و #1039.
   */
  private val memoryLock = NSLock()
  private inline fun <R> inMemory(block: () -> R): R {
    memoryLock.lock()
    try { return block() } finally { memoryLock.unlock() }
  }

  fun read(account: String): ByteArray? {
    memory?.let { m -> return inMemory { m[account]?.copyOf() } }
    return readKeychain(account)
  }

  private fun readKeychain(account: String): ByteArray? = memScoped {
    val q = query(account)
    CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
    CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
    val out = alloc<CFTypeRefVar>()
    val rc = SecItemCopyMatching(q, out.ptr)
    CFRelease(q)
    when (rc) {
      errSecSuccess -> (CFBridgingRelease(out.value) as? NSData)?.toByteArray()
      errSecItemNotFound -> null
      else -> throw KeychainException(rc, "خواندنِ کلیددان")
    }
  }

  /** فقط اگر نیست؛ بود ⇒ false (مقدارِ پیشین دست نمی‌خورد) */
  fun add(account: String, value: ByteArray): Boolean {
    memory?.let { m -> return inMemory { if (account in m) false else { m[account] = value.copyOf(); true } } }
    val q = addQuery(account, value)
    val rc = SecItemAdd(q, null)
    CFRelease(q)
    return when (rc) {
      errSecSuccess -> true
      errSecDuplicateItem -> false
      else -> throw KeychainException(rc, "نوشتنِ کلیددان")
    }
  }

  fun put(account: String, value: ByteArray) {
    delete(account)
    if (!add(account, value)) throw KeychainException(errSecDuplicateItem, "نوشتنِ کلیددان")
  }

  fun delete(account: String) {
    memory?.let { m -> inMemory { m.remove(account) }; return }
    val q = query(account)
    val rc = SecItemDelete(q)
    CFRelease(q)
    if (rc != errSecSuccess && rc != errSecItemNotFound) throw KeychainException(rc, "پاک کردنِ کلیددان")
  }

  /**
   *  آیا همان واژه‌نامه‌ای که [add] برای [account] به `SecItemAdd` می‌دهد ردهٔ دسترسیِ (`kSecAttrAccessible`) [accessible] را
   *  می‌خواهد — آزمونِ شبیه‌ساز (`DeviceIdentityIosTest`: آینهٔ هویتِ دستگاه «فقط همین دستگاه» است، نه ردهٔ جابه‌جاشونده).
   *  test.kexe کلیددانِ iOS ندارد (OSStatus -25291) ⇒ خودِ درخواستِ برنامه سنجیده می‌شود؛ هیچ قلمی نوشته نمی‌شود.
   */
  internal fun addRequestIs(account: String, accessible: CFTypeRef?): Boolean {
    val q = addQuery(account, byteArrayOf(0))
    val asked = CFDictionaryGetValue(q, kSecAttrAccessible)
    val same = asked != null && asked == accessible
    CFRelease(q)
    return same
  }

  /** درخواستِ `SecItemAdd` ِ [add]: همان پرسش، داده و «پس از نخستین بازگشایی، فقط همین دستگاه» */
  private fun addQuery(account: String, value: ByteArray): CFMutableDictionaryRef {
    val q = query(account)
    val data = CFBridgingRetain(value.toNSData())
    CFDictionaryAddValue(q, kSecValueData, data)
    CFRelease(data)
    CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
    return q
  }

  private fun query(account: String): CFMutableDictionaryRef {
    val q = CFDictionaryCreateMutable(null, 6, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
    put(q, kSecAttrService, SERVICE)
    put(q, kSecAttrAccount, account)
    return q
  }

  private fun put(q: CFMutableDictionaryRef, key: CFTypeRef?, value: String) {
    val v = CFBridgingRetain(value)
    CFDictionaryAddValue(q, key, v)
    CFRelease(v)
  }

  @Suppress("unused")
  private fun unused(p: CPointer<*>?) = p
}
