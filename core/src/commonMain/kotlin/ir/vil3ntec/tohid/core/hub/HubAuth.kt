package ir.vil3ntec.tohid.core.hub

import ir.vil3ntec.tohid.core.audit.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 *  ورود به هاب با **همان توکنِ سرور** که دستگاه دارد (`docs/SYN-09-HUB-fa.md` §۳).
 *
 *  هاب نمی‌تواند توکن را خودش بسنجد (توکن‌ها تصادفی‌اند و فقط سرور می‌شناسدشان). پس:
 *  - تا اینترنت هست، توکن نزدِ سرور سنجیده می‌شود (`GET /sync/v3/hello` با همان توکن و `X-Device`)؛
 *    سرور «فقط دکان»، نشستِ زنده و پیوندِ دستگاه را همان‌جا می‌سنجد.
 *  - هاب فقط **درهمِ** توکن را نگه می‌دارد (با پیشوندِ جدا، نه همان درهمِ سرور)، کنارِ شناسهٔ دستگاه.
 *  - بی اینترنت فقط توکنی پذیرفته است که در `OFFLINE_TTL_MS` ِ گذشته سنجیده شده باشد؛ توکنِ ناشناس رد.
 *  - هر توکن به یک دستگاه بسته است؛ همان توکن با شناسهٔ دیگر رد.
 *  - ⛔ هاب فقط مالِ **دکانِ خودش** است: توکنی که سرور (یا فهرستِ ۲۴ ساعته) به دکانِ دیگری می‌بندد ۴۰۳
 *    `hub_other_shop` می‌گیرد و دسته‌اش هرگز به صف نمی‌رسد (صفِ هاب را پر نمی‌کند). همان درهم با دکانِ دیگر به
 *    خاطر سپرده می‌شود تا تکرارش بی تماس با سرور رد شود؛ هنگامِ سرریزِ فهرست، اول همین‌ها بیرون می‌روند.
 *  - ساعتِ عقب‌رفته (سنجیده‌شده «در آینده») یعنی بی اعتبار تا سنجشِ دوباره.
 */
@Serializable
data class HubPass(val hash: String, val deviceId: String, val verifiedAt: Long, val shopId: String = "")

@Serializable
data class HubPasses(val version: Int = 1, val passes: List<HubPass> = emptyList())

enum class HubRefusal(val status: Int, val code: String) {
  MALFORMED(401, "hub_auth_required"),
  MISMATCH(403, "hub_device_mismatch"),
  OTHER_SHOP(403, "hub_other_shop"),
  INVALID(401, "hub_invalid_token"),
  UNVERIFIED(401, "hub_unverified"),
  STALE(401, "hub_stale"),
}

sealed class HubVerdict {
  data class Accept(val pass: HubPass, val offline: Boolean) : HubVerdict()
  data class Refuse(val reason: HubRefusal) : HubVerdict()
}

/** آنچه باید روی فهرستِ سنجیده‌ها بنشیند — جدا از تصمیم، تا تماس با سرور بیرون از قفل باشد */
sealed class HubPassChange {
  object None : HubPassChange()
  data class Put(val pass: HubPass) : HubPassChange()
  data class Remove(val hash: String) : HubPassChange()
}

object HubAuth {
  /** پس از این، توکن دوباره نزدِ سرور سنجیده می‌شود (اگر سرور در دسترس است) */
  const val RECHECK_MS: Long = 10 * 60_000L
  /** بی اینترنت، تا این مدت پس از آخرین سنجش */
  const val OFFLINE_TTL_MS: Long = 24 * 3_600_000L
  const val MAX_PASSES: Int = 256

  private val json = Json { ignoreUnknownKeys = true }

  fun hashOf(token: String): String = Sha256.hexOf("tohid-hub-v1:$token")

  data class Result(val verdict: HubVerdict, val change: HubPassChange)

  /**
   *  @param hubShop دکانِ حسابِ خودِ کامپیوترِ هاب — خالی ⇒ هیچ توکنی پذیرفته نیست
   *  @param check فقط وقتی صدا زده می‌شود که لازم است (توکنِ ناشناس یا کهنه)
   */
  fun decide(passes: HubPasses, token: String?, deviceId: String?, hubShop: String, now: Long, check: () -> CloudAnswer): Result {
    if (token == null || token.length !in 20..4096 || !HubQueue.validDevice(deviceId)) {
      return Result(HubVerdict.Refuse(HubRefusal.MALFORMED), HubPassChange.None)
    }
    if (hubShop.isBlank()) return Result(HubVerdict.Refuse(HubRefusal.OTHER_SHOP), HubPassChange.None)
    val h = hashOf(token)
    val p = passes.passes.firstOrNull { it.hash == h }
    if (p != null && p.deviceId != deviceId) return Result(HubVerdict.Refuse(HubRefusal.MISMATCH), HubPassChange.None)
    //  دکانِ توکن عوض نمی‌شود؛ دکانِ هاب شاید (صاحبِ کامپیوتر دکانِ دیگری برگزید) ⇒ همان‌جا رد
    if (p != null && p.shopId.isNotEmpty() && p.shopId != hubShop) {
      return Result(HubVerdict.Refuse(HubRefusal.OTHER_SHOP), HubPassChange.None)
    }
    val known = p?.takeIf { it.shopId == hubShop }
    val age = known?.let { now - it.verifiedAt }
    if (known != null && age != null && age in 0..RECHECK_MS) return Result(HubVerdict.Accept(known, offline = false), HubPassChange.None)
    val answer = check()
    return when (answer.check) {
      CloudCheck.VALID -> {
        if (answer.shopId != hubShop) {
          val mark = answer.shopId.takeIf { it.isNotEmpty() }?.let { HubPassChange.Put(HubPass(h, deviceId!!, now, it)) }
          return Result(HubVerdict.Refuse(HubRefusal.OTHER_SHOP), mark ?: HubPassChange.Remove(h))
        }
        val np = HubPass(h, deviceId!!, now, hubShop)
        Result(HubVerdict.Accept(np, offline = false), HubPassChange.Put(np))
      }
      CloudCheck.INVALID -> Result(HubVerdict.Refuse(HubRefusal.INVALID), HubPassChange.Remove(h))
      CloudCheck.UNREACHABLE -> when {
        known == null -> Result(HubVerdict.Refuse(HubRefusal.UNVERIFIED), HubPassChange.None)
        age != null && age in 0..OFFLINE_TTL_MS -> Result(HubVerdict.Accept(known, offline = true), HubPassChange.None)
        else -> Result(HubVerdict.Refuse(HubRefusal.STALE), HubPassChange.None)
      }
    }
  }

  /** @param hubShop در سرریز، درهم‌های دکانِ دیگر پیش از درهم‌های همین دکان بیرون می‌روند */
  fun apply(passes: HubPasses, change: HubPassChange, hubShop: String = ""): HubPasses = when (change) {
    HubPassChange.None -> passes
    is HubPassChange.Remove -> passes.copy(passes = passes.passes.filter { it.hash != change.hash })
    is HubPassChange.Put -> {
      val rest = passes.passes.filter { it.hash != change.pass.hash }
      val all = rest + change.pass
      val keep = if (all.size <= MAX_PASSES) all else all.sortedWith(compareBy({ it.shopId == hubShop }, { it.verifiedAt })).takeLast(MAX_PASSES)
      passes.copy(passes = keep.sortedBy { it.verifiedAt })
    }
  }

  fun revoke(passes: HubPasses, token: String): HubPasses = apply(passes, HubPassChange.Remove(hashOf(token)))

  fun encode(p: HubPasses): String = json.encodeToString(HubPasses.serializer(), p)

  fun decode(text: String?): HubPasses? = text?.let {
    runCatching { json.decodeFromString(HubPasses.serializer(), it) }.getOrNull()
  }?.takeIf { it.version == 1 }
}

/**
 *  سطلِ ژتون برای هر کلید (نشانیِ IP ِ شبکهٔ دکان). ⚠️ همزمانی با فراخوان است (یک قفل).
 *  بیش از `maxKeys` کلید ⇒ کهنه‌ترین کنار می‌رود، تا حافظه با نشانی‌های ساختگی پر نشود.
 */
class HubLimiter(private val perMinute: Int, private val burst: Int, private val maxKeys: Int = 1_024) {
  private class Bucket(var tokens: Double, var at: Long)
  private val buckets = LinkedHashMap<String, Bucket>()

  fun allow(key: String, now: Long): Boolean {
    val b = buckets.getOrPut(key) {
      if (buckets.size >= maxKeys) buckets.keys.firstOrNull()?.let { buckets.remove(it) }
      Bucket(burst.toDouble(), now)
    }
    val elapsed = (now - b.at).coerceAtLeast(0)
    b.tokens = (b.tokens + elapsed * perMinute / 60_000.0).coerceAtMost(burst.toDouble())
    b.at = now
    if (b.tokens < 1.0) return false
    b.tokens -= 1.0
    return true
  }
}
