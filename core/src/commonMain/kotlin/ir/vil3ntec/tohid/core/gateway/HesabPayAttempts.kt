package ir.vil3ntec.tohid.core.gateway

import kotlin.random.Random

/**
 *  تلاشِ پرداخت با حساب‌پی برای یک فاکتورِ صندوق — کلیدِ یکتایی، شناسهٔ قصد و ازسرگیری.
 *
 *  ── چرا نگه داشته می‌شود ───────────────────────────────────────────
 *  کلیدِ یکتایی **پیش از** نخستین درخواست ساخته و ذخیره می‌شود؛ قطعِ شبکه یا تکرارِ «ساختن»
 *  همان قصد را برمی‌گرداند، نه قصدِ دوم. شناسهٔ قصد هم کنارِ کلیدِ فاکتور (`SaleDraft.key`) می‌ماند:
 *  اگر برنامه وسطِ پرداخت بسته شد، بازکردنِ دوبارهٔ همان فاکتور همان قصد را می‌پاید و اگر در
 *  نبودنِ برنامه پرداخت شده بود، فروش را می‌بندد — قصدِ تازه ساخته نمی‌شود.
 *
 *  یک قصد حداکثر یک فروش را می‌بندد: `refId` = کلیدِ فاکتور و `SalesEngine.record(saleId = key)`
 *  فروشِ دوم با همان شناسه را رد می‌کند.
 *
 *  ── شکلِ ذخیره ─────────────────────────────────────────────────────
 *  هر رکورد یک خط: `v1|saleKey|amount|key|intentId|status|createdAt|expiresAt|skew`؛ همهٔ
 *  شناسه‌ها `[A-Za-z0-9_.:-]` اند، پس `|` و خطِ تازه در آن‌ها نیست. خطِ خراب نادیده گرفته می‌شود.
 */
data class PendingPayment(
  /** کلیدِ فاکتور = شناسهٔ فروشی که ثبت می‌شود = `refId` ِ قصد */
  val saleKey: String,
  val amountMinor: Long,
  val idempotencyKey: String,
  val intentId: String? = null,
  /** آخرین وضعیتِ دیده‌شده (`created` تا پیش از پاسخِ سرور) */
  val status: String = IntentStatus.CREATED.wire,
  val createdAt: Long,
  val expiresAt: Long? = null,
  /** ساعتِ دستگاه منهای ساعتِ سرور، تخمینی (شمارشِ معکوس) */
  val skewMs: Long = 0,
)

object HesabPayAttempts {

  /** کلیدِ یکتاییِ تازه: `pos-<کلیدِ فاکتور>-<۱۲ نویسهٔ تصادفی>` (۸ تا ۱۲۸ نویسهٔ مجازِ سرور) */
  fun newKey(saleKey: String, random: Random = Random.Default): String {
    val safe = saleKey.filter { it.isLetterOrDigit() && it.code < 128 }.take(64)
    val tail = CharArray(12) { ALPHABET[random.nextInt(ALPHABET.length)] }.concatToString()
    return "pos-$safe-$tail"
  }

  sealed interface Plan {
    /** همان قصد را ادامه بده (یا اگر پرداخت شده، فروش را ببند) */
    data class Resume(val pending: PendingPayment) : Plan

    /** قصدِ زندهٔ قبلی مبلغِ دیگری دارد (سبد عوض شد) ⇐ اول لغو، بعد تلاشِ تازه */
    data class Replace(val old: PendingPayment, val fresh: PendingPayment) : Plan

    /** تلاشِ تازه با کلیدِ تازه */
    data class Fresh(val pending: PendingPayment) : Plan
  }

  private val DEAD = setOf(IntentStatus.FAILED.wire, IntentStatus.EXPIRED.wire, IntentStatus.CANCELLED.wire)

  /**
   *  با رکوردِ ذخیره‌شدهٔ همین فاکتور چه کنیم؟
   *   - همان مبلغ و هنوز نمرده (created، pending، paid، نامعلوم) ⇐ ازسرگیری؛
   *   - مرده (failed، expired، cancelled) ⇐ تلاشِ تازه با کلیدِ تازه؛
   *   - مبلغِ دیگر و قصدِ شاید زنده ⇐ اول لغوِ قبلی؛ پرداخت‌شده هرگز لغو نمی‌شود و ازسر گرفته
   *     می‌شود (صفحه می‌گوید مبلغ نمی‌خواند و فروش را نمی‌بندد).
   */
  fun plan(stored: PendingPayment?, saleKey: String, amountMinor: Long, now: Long, random: Random = Random.Default): Plan {
    val fresh = PendingPayment(saleKey, amountMinor, newKey(saleKey, random), createdAt = now)
    if (stored == null || stored.saleKey != saleKey) return Plan.Fresh(fresh)
    if (stored.status == IntentStatus.PAID.wire || stored.status == IntentStatus.REFUNDED.wire) return Plan.Resume(stored)
    if (stored.status in DEAD) return Plan.Fresh(fresh)
    if (stored.amountMinor == amountMinor) return Plan.Resume(stored)
    return if (stored.intentId != null) Plan.Replace(stored, fresh) else Plan.Fresh(fresh)
  }

  /** رکورد پس از پاسخِ سرور؛ `localNow` برای تخمینِ اختلافِ ساعت از `createdAt` ِ قصدِ تازه */
  fun withIntent(p: PendingPayment, i: PaymentIntent, localNow: Long? = null): PendingPayment {
    val skew = if (localNow != null && i.createdAt != null && p.intentId == null) localNow - i.createdAt else p.skewMs
    return p.copy(intentId = i.id, status = i.status.wire, expiresAt = i.expiresAt ?: p.expiresAt, skewMs = skew)
  }

  // ── ذخیره ─────────────────────────────────────────────────────────

  /** سقفِ رکوردها و عمرِ هر رکورد — فاکتورِ رهاشده دفتر را پر نکند */
  const val MAX_RECORDS = 20
  const val MAX_AGE_MS = 48 * 60 * 60 * 1000L

  fun encode(list: List<PendingPayment>): String = list.joinToString("\n") { p ->
    listOf(
      "v1", p.saleKey, p.amountMinor.toString(), p.idempotencyKey, p.intentId.orEmpty(), p.status,
      p.createdAt.toString(), p.expiresAt?.toString().orEmpty(), p.skewMs.toString(),
    ).joinToString("|")
  }

  fun decode(raw: String?): List<PendingPayment> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split('\n').mapNotNull { line ->
      val f = line.trim().split('|')
      if (f.size != 9 || f[0] != "v1") return@mapNotNull null
      val amount = f[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
      val created = f[6].toLongOrNull() ?: return@mapNotNull null
      if (!Wire.isToken(f[1]) || !Wire.isToken(f[3], min = 8)) return@mapNotNull null
      val intent = f[4].ifEmpty { null }
      if (intent != null && !Wire.isToken(intent)) return@mapNotNull null
      PendingPayment(
        saleKey = f[1], amountMinor = amount, idempotencyKey = f[3], intentId = intent,
        status = f[5].ifEmpty { IntentStatus.CREATED.wire }, createdAt = created,
        expiresAt = f[7].toLongOrNull(), skewMs = f[8].toLongOrNull() ?: 0,
      )
    }
  }

  /** افزودن/جایگزینیِ رکوردِ همان فاکتور؛ رکوردهای کهنه و بیش از سقف حذف */
  fun upsert(list: List<PendingPayment>, p: PendingPayment, now: Long): List<PendingPayment> =
    (list.filter { it.saleKey != p.saleKey && now - it.createdAt < MAX_AGE_MS } + p).takeLast(MAX_RECORDS)

  fun remove(list: List<PendingPayment>, saleKey: String): List<PendingPayment> = list.filter { it.saleKey != saleKey }

  private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
}

/** جای نگه‌داریِ رکوردها روی همین دستگاه — پلتفرم می‌دهد (اندروید: SharedPreferences) */
interface PendingStore {
  fun read(): String?
  fun write(value: String)
}

/** دفترِ تلاش‌ها روی [PendingStore] — همهٔ خواندن/نوشتن از همین‌جا */
class PendingPayments(private val store: PendingStore, private val now: () -> Long) {
  fun all(): List<PendingPayment> = HesabPayAttempts.decode(runCatching { store.read() }.getOrNull())

  fun of(saleKey: String): PendingPayment? = all().lastOrNull { it.saleKey == saleKey }

  fun save(p: PendingPayment) {
    runCatching { store.write(HesabPayAttempts.encode(HesabPayAttempts.upsert(all(), p, now()))) }
  }

  fun forget(saleKey: String) {
    val list = all()
    if (list.any { it.saleKey == saleKey }) runCatching { store.write(HesabPayAttempts.encode(HesabPayAttempts.remove(list, saleKey))) }
  }

  /** رکوردِ فاکتوری که دیگر ثبت شده (`posted`) پاک می‌شود */
  fun prune(posted: (String) -> Boolean) {
    val list = all()
    val keep = list.filter { !posted(it.saleKey) && now() - it.createdAt < HesabPayAttempts.MAX_AGE_MS }
    if (keep.size != list.size) runCatching { store.write(HesabPayAttempts.encode(keep)) }
  }
}
