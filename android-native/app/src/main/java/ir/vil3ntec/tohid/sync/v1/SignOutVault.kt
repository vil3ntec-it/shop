package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.DiskWrite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 *  گاوصندوقِ «خروجِ اجباری» — تغییرهای نرفتهٔ یک حساب، **رمزدار** و **بسته به همان حساب**.
 *
 *  خروجِ اجباری (پس از دو تأیید و پشتیبانِ دیدنی) صفِ ساده را روی گوشی جا نمی‌گذارد تا هر کس پس از او وارد
 *  شود ببیند یا (با اشکالِ کلیدها) به دکانِ خودش بفرستد: opها با همان op_id این‌جا رمز می‌شوند (کلیدِ دستگاه،
 *  `AtRest.codec` — Keystore/Keychain/کلیددانِ کامپیوتر) و فقط وقتی همان حساب دوباره وارد شد جلوی صفِ خودش
 *  برمی‌گردند (`SyncV1Engine.restoreSealedFor`) و به سرور می‌روند.
 *
 *  ⛔ هرگز opی از صف بیرون نمی‌آید مگر گاوصندوق نوشته و **از روی دیسک دوباره باز** شده باشد ([seal]).
 *  رمز در دسترس نیست ⇒ `false` و صفِ ساده سرِ جایش (ایمنی پیش از پنهان‌کاری).
 */
object SignOutVault {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun dir(context: Context) = File(context.applicationContext.filesDir, "signout-vault")

  internal fun file(context: Context, userId: String): File =
    File(dir(context), "v-" + OpHash.sha256Hex("vault|${userId.trim()}").take(32) + ".atr")

  /** آیا این حساب تغییرِ نرفتهٔ رمزدار روی این دستگاه دارد */
  fun has(context: Context, userId: String): Boolean = userId.isNotBlank() && file(context, userId).exists()

  fun seal(context: Context, userId: String, shopId: String, ops: List<SyncOp>): Boolean {
    if (userId.isBlank()) return false
    val f = file(context, userId)
    //  گاوصندوقِ پیشینِ همین حساب (خروجِ اجباریِ قبلی که هنوز برنگشته) با همین‌ها یکی می‌شود — هیچ‌کدام گم نمی‌شود
    val earlier = open(context, userId).orEmpty()
    val ids = ops.mapTo(HashSet()) { it.opId }
    val all = earlier.filter { it.opId !in ids } + ops
    val body = buildJsonObject {
      put("v", JsonPrimitive(1))
      put("user", JsonPrimitive(userId))
      put("shop", JsonPrimitive(shopId))
      put("at", JsonPrimitive(System.currentTimeMillis()))
      put("ops", JsonArray(all.map { it.toJson() }))
    }.toString().toByteArray(Charsets.UTF_8)
    return try {
      val sealed = AtRest.codec(context).seal(body)
      f.parentFile?.mkdirs()
      DiskWrite.bytes(f, sealed)
      //  دوباره از روی دیسک: فقط پاکتی که باز شد و همهٔ opها را داشت پذیرفته است
      val back = open(context, userId)
      back != null && back.map { it.opId }.toSet().containsAll(ids)
    } catch (e: Exception) {
      false
    }
  }

  /** opهای رمزدارِ این حساب؛ نبود یا باز نشد ⇒ `null` (هرگز فهرستِ خالیِ ساختگی) */
  fun open(context: Context, userId: String): List<SyncOp>? {
    val f = file(context, userId)
    if (!f.exists()) return null
    return runCatching {
      val plain = AtRest.codec(context).open(f.readBytes())
      val tree = json.parseToJsonElement(String(plain, Charsets.UTF_8)) as JsonObject
      if ((tree["user"] as? JsonPrimitive)?.content != userId) return null
      (tree["ops"] as JsonArray).mapNotNull { SyncOp.fromJson(it as JsonObject) }
    }.getOrNull()
  }

  fun drop(context: Context, userId: String) {
    runCatching { file(context, userId).delete() }
  }
}
