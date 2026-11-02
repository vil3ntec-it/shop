package ir.vil3ntec.tohid.ios.keychain

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
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
 *  کلیددانِ iOS (Keychain) برای چند بایتِ رازِ برنامه — کلیدِ اصلیِ دفترِ رمزدار (`AtRestKeysIos`) و
 *  توکنِ ورود. «پس از نخستین بازگشاییِ دستگاه، فقط همین دستگاه»
 *  (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`): نه در پشتیبانِ iCloud، نه انتقال به گوشیِ تازه —
 *  همان قاعدهٔ Android Keystore. خطا ⇐ [KeychainException] با کدِ OSStatus، هرگز «خالی» ِ بی‌صدا.
 */
class KeychainException(val status: Int, message: String) : Exception("$message (OSStatus $status)")

object Keychain {
  const val SERVICE = "ir.vil3ntec.tohid"

  fun read(account: String): ByteArray? = memScoped {
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
    val q = query(account)
    val data = CFBridgingRetain(value.toNSData())
    CFDictionaryAddValue(q, kSecValueData, data)
    CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
    val rc = SecItemAdd(q, null)
    CFRelease(q)
    CFRelease(data)
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
    val q = query(account)
    val rc = SecItemDelete(q)
    CFRelease(q)
    if (rc != errSecSuccess && rc != errSecItemNotFound) throw KeychainException(rc, "پاک کردنِ کلیددان")
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
