package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 *  خواندنِ فایلِ پشتیبانِ امروز — **فقط خواندن**.
 *
 *  دو قالب، همان دو قالبی که `BackupBundle.read`ِ برنامه می‌شناسد:
 *   ‌• ZIP: `shop-data.json` + `photos/<شناسه>.jpg` (عکس‌ها فقط شمرده می‌شوند،
 *     باز نمی‌شوند و هیچ‌جا نوشته نمی‌شوند)
 *   ‌• JSONِ ساده: همان `shop-data.json` (خروجیِ `ShopStore.exportBackup()` یا
 *     پشتیبانِ نسخهٔ وب)
 *
 *  تبدیلِ JSON به `ShopData` با **همان** `Models.kt`ِ برنامه (کپیِ خودکار در
 *  ساخت) و همان تنظیمِ `Json`ِ `ShopStore` انجام می‌شود، و همان قاعدهٔ
 *  `ShopStore.parseBackup`: فایلی که مُهرِ `exportedAt` ندارد و هیچ فهرستی‌اش
 *  پر نیست، پشتیبانِ فروشگاه نیست.
 */
object BackupReader {

  const val LEDGER_ENTRY = "shop-data.json"
  private const val PHOTO_DIR = "photos/"
  private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

  /** همان تنظیمِ `ShopStore.json` */
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
  }

  data class Opened(
    val data: ShopData,
    val format: String,
    val exportedAt: String?,
    val hasStoreName: Boolean,
    val storeName: String,
    val photos: Int,
    val bytes: Int,
    val sha256: String,
  )

  fun read(file: File): Opened = read(file.readBytes())

  fun read(bytes: ByteArray): Opened {
    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val isZip = bytes.size >= ZIP_MAGIC.size && bytes.copyOf(ZIP_MAGIC.size).contentEquals(ZIP_MAGIC)
    var photos = 0
    val text: String = if (!isZip) {
      bytes.toString(Charsets.UTF_8)
    } else {
      var ledger: String? = null
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
          val name = entry.name
          when {
            !entry.isDirectory && name.endsWith(LEDGER_ENTRY) && !name.contains(PHOTO_DIR) ->
              ledger = zip.readBytes().toString(Charsets.UTF_8)
            !entry.isDirectory && name.startsWith(PHOTO_DIR) && name.endsWith(".jpg") -> photos++
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
      ledger ?: error("این فایل، پشتیبانِ فروشگاه نیست (ZIP بی $LEDGER_ENTRY)")
    }
    val tree = json.parseToJsonElement(text) as? JsonObject
      ?: error("فایل پشتیبان معتبر نیست")
    val data = parse(tree)
    val settings = tree["settings"] as? JsonObject
    val storeName = (settings?.get("storeName") as? JsonPrimitive)?.contentOrNull ?: ""
    return Opened(
      data = data,
      format = if (isZip) "zip" else "json",
      exportedAt = (tree["exportedAt"] as? JsonPrimitive)?.contentOrNull,
      hasStoreName = storeName.isNotBlank(),
      storeName = storeName,
      photos = photos,
      bytes = bytes.size,
      sha256 = sha,
    )
  }

  fun parse(raw: String): ShopData = parse(json.parseToJsonElement(raw).jsonObject)

  private fun parse(tree: JsonObject): ShopData {
    val parsed = json.decodeFromJsonElement(ShopData.serializer(), tree)
    if (!tree.containsKey("exportedAt") && size(parsed) == 0) error("این فایل، پشتیبانِ فروشگاه نیست")
    return parsed
  }

  /** همان `ShopStore.backupSize` */
  fun size(d: ShopData): Int =
    d.products.size + d.sales.size + d.debtors.size + d.expenses.size +
      d.suppliers.size + d.purchases.size + d.warehouseEntries.size + d.transactions.size

  fun encode(d: ShopData): String = json.encodeToString(ShopData.serializer(), d)
}
