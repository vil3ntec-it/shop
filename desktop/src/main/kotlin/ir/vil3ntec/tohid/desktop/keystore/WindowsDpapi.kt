package ir.vil3ntec.tohid.desktop.keystore

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import ir.vil3ntec.tohid.core.i18n.StrAppData
import java.util.Base64

/**
 *  ویندوز: DPAPI با دامنهٔ **کاربرِ جاری** (`CryptProtectData` بی `CRYPTPROTECT_LOCAL_MACHINE`) از راهِ JNA ِ موجود
 *  (`net.java.dev.jna:jna`، از پیش با openpnp-capture در برنامه است) — نه PowerShell، نه خطِ فرمان.
 *
 *  بایت‌های پیچیده در فایلِ نشان (`keys/atrest-kek.os`) می‌مانند؛ فقط همان حسابِ ویندوز روی همان کامپیوتر بازشان
 *  می‌کند. کپیِ پوشهٔ داده یا دیسک به جای دیگر ⇒ باز نمی‌شود. ⚠️ بازنشانیِ گذرواژهٔ ویندوز به دستِ مدیر (نه عوض کردنش به
 *  دستِ خودِ کاربر) کلیدهای DPAPI را می‌بُرد ⇒ همان «کلید در دسترس نیست» و «بازسازی از سرور» (چک‌لیستِ FLAGS-ON).
 *  `CRYPTPROTECT_UI_FORBIDDEN`: هیچ پنجره‌ای.
 */
internal object WindowsDpapi : OsKeyStore {
  override val id: String = "dpapi"
  override val label: String get() = StrAppData.deskStoreDpapi

  private const val UI_FORBIDDEN = 0x1
  private const val TIMEOUT_S = 30L

  /** «آنتروپیِ» ثابتِ همین برنامه — برنامهٔ دیگرِ همین کاربر بی دانستنش `CryptUnprotectData` را بی‌واسطه نمی‌زند */
  private val ENTROPY = "ir.vil3ntec.tohid/atrest-kek/v1".toByteArray(Charsets.US_ASCII)

  @Suppress("FunctionName")
  interface Crypt32 : StdCallLibrary {
    fun CryptProtectData(
      pDataIn: Blob,
      szDataDescr: WString?,
      pOptionalEntropy: Blob?,
      pvReserved: Pointer?,
      pPromptStruct: Pointer?,
      dwFlags: Int,
      pDataOut: Blob,
    ): Boolean

    fun CryptUnprotectData(
      pDataIn: Blob,
      ppszDataDescr: Pointer?,
      pOptionalEntropy: Blob?,
      pvReserved: Pointer?,
      pPromptStruct: Pointer?,
      dwFlags: Int,
      pDataOut: Blob,
    ): Boolean
  }

  @Suppress("FunctionName")
  interface Kernel32 : StdCallLibrary {
    fun LocalFree(hMem: Pointer?): Pointer?
  }

  /** `DATA_BLOB` */
  class Blob : Structure() {
    @JvmField var cbData: Int = 0
    @JvmField var pbData: Pointer? = null
    override fun getFieldOrder(): List<String> = listOf("cbData", "pbData")
  }

  private class Libs(val crypt: Crypt32, val kernel: Kernel32)

  private val libs: Libs by lazy {
    Libs(Native.load("Crypt32", Crypt32::class.java), Native.load("Kernel32", Kernel32::class.java))
  }

  private fun memoryOf(bytes: ByteArray): Memory = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }

  private fun blobOf(m: Memory, size: Int): Blob = Blob().apply { cbData = size; pbData = m }

  override fun protect(ref: String, key: ByteArray): String = Bounded.run(TIMEOUT_S) {
    val l = libs
    val input = memoryOf(key)
    val entropy = memoryOf(ENTROPY)
    try {
      val out = Blob()
      if (!l.crypt.CryptProtectData(blobOf(input, key.size), WString("My Shop"), blobOf(entropy, ENTROPY.size), null, null, UI_FORBIDDEN, out)) {
        throw KeyStoreFailure(StrAppData.deskOsRefused("CryptProtectData ${Native.getLastError()}"))
      }
      val p = out.pbData ?: throw KeyStoreFailure(StrAppData.deskOsRefused("CryptProtectData null"))
      try {
        Base64.getEncoder().encodeToString(p.getByteArray(0, out.cbData))
      } finally {
        l.kernel.LocalFree(p)
      }
    } finally {
      input.clear()
    }
  }

  override fun unprotect(ref: String, blob: String): ByteArray = Bounded.run(TIMEOUT_S) {
    val data = runCatching { Base64.getDecoder().decode(blob) }.getOrNull()
      ?.takeIf { it.isNotEmpty() }
      ?: throw KeyStoreFailure(StrAppData.deskOsNotFound, notFound = true)
    val l = libs
    val input = memoryOf(data)
    val entropy = memoryOf(ENTROPY)
    val out = Blob()
    if (!l.crypt.CryptUnprotectData(blobOf(input, data.size), null, blobOf(entropy, ENTROPY.size), null, null, UI_FORBIDDEN, out)) {
      throw KeyStoreFailure(StrAppData.deskOsRefused("CryptUnprotectData ${Native.getLastError()}"))
    }
    val p = out.pbData ?: throw KeyStoreFailure(StrAppData.deskOsRefused("CryptUnprotectData null"))
    try {
      p.getByteArray(0, out.cbData)
    } finally {
      p.clear(out.cbData.toLong())
      l.kernel.LocalFree(p)
    }
  }

  /** بایت‌های پیچیده در خودِ فایلِ نشان‌اند؛ چیزی در ویندوز نمی‌ماند که پاک شود */
  override fun remove(ref: String) = Unit
}
