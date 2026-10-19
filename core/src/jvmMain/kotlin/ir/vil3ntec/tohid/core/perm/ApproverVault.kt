package ir.vil3ntec.tohid.core.perm

/**
 *  گاوصندوقِ PIN و کلیدِ تأییدِ هر کاربر روی همین دستگاه — STF-02 و STF-04.
 *
 *  یک «ردیف» برای هر کاربری که روی این دستگاه PIN گذاشته:
 *   • همه: نمک + اثرِ PBKDF2ِ PIN + شمارِ خطا/قفل (`PinGuard`) — برای تعویضِ
 *     سریعِ کاربر و قفلِ صندوق؛
 *   • صاحب دکان و مدیر: جفت‌کلیدِ ES256؛ کلیدِ خصوصی با همان PIN بسته است و
 *     کلیدِ عمومی (`spki`) یک بار، آنلاین، در سرور ثبت می‌شود
 *     (`POST /api/shop/approver-keys`) و شناسه‌اش `keyId` است.
 *  هیچ چیزِ این ردیف همگام نمی‌شود؛ برنامه آن را در انبارِ خصوصیِ خودش نگه می‌دارد
 *  (`encode`/`decode`، متنِ یک‌خطی‌به‌ازای‌هر‌فیلد).
 */
object ApproverVault {

  data class Record(
    val userId: String,
    val name: String,
    val role: String,
    val salt: String,
    val iterations: Int,
    val check: String,
    val failures: Int = 0,
    val locked: Boolean = false,
    val keyId: String = "",
    val spki: String = "",
    val keySalt: String = "",
    val sealed: String = "",
    /** کلیدِ عمومی در سرور ثبت شد؟ تا نشده، برگه‌اش را سرور نمی‌شناسد */
    val registered: Boolean = false,
  ) {
    val hasKey: Boolean get() = keyId.isNotEmpty() && sealed.isNotEmpty()
    val guard: PinGuard.State get() = PinGuard.State(failures, locked)
  }

  data class Outcome(val record: Record, val ok: Boolean)

  /** فقط این نقش‌ها چیزی را «مجاز» دارند که تأییدش معنا داشته باشد (همان سرور) */
  val APPROVER_ROLES: List<String> = listOf("owner", "manager")

  /** ردیفِ تازه — PIN باید `PinGuard.newPinProblem` نداشته باشد */
  fun create(userId: String, name: String, role: String, pin: String, iterations: Int = PinCrypto.ITERATIONS): Record {
    require(PinGuard.newPinProblem(pin) == null) { "PIN باید ۶ رقمِ غیرساده باشد" }
    val salt = PinCrypto.salt()
    val base = Record(
      userId = userId, name = name, role = role,
      salt = PinCrypto.b64(salt), iterations = iterations, check = PinCrypto.pinHash(pin, salt, iterations),
    )
    if (role !in APPROVER_ROLES) return base
    val (pkcs8, spki) = PinCrypto.newKeyPair()
    val keySalt = PinCrypto.salt()
    return base.copy(
      keyId = PinCrypto.keyId(spki), spki = PinCrypto.b64(spki),
      keySalt = PinCrypto.b64(keySalt), sealed = PinCrypto.seal(pkcs8, pin, keySalt, iterations),
    )
  }

  /** یک تلاش؛ قفل‌شده ⇐ همیشه ناموفق (فقط PINِ تازه پس از ورودِ آنلاین بازش می‌کند) */
  fun verify(r: Record, pin: String): Outcome {
    if (!PinGuard.canTry(r.guard)) return Outcome(r, false)
    val ok = PinGuard.isWellFormed(pin) &&
      PinCrypto.matches(pin, PinCrypto.unb64(r.salt), r.iterations, r.check)
    val g = PinGuard.after(r.guard, ok)
    return Outcome(r.copy(failures = g.failures, locked = g.locked), ok)
  }

  /** پیش‌نویسِ برگه (بی امضا) برای همین تأییدکننده */
  fun draft(
    r: Record, shop: String, need: ApprovalSlip.Need, reason: String, nowMs: Long,
    ttlMs: Long = 10 * 60_000L,
  ): ApprovalSlip.Slip = ApprovalSlip.Slip(
    shop = shop, doc = need.doc, perm = need.perm, pctBp = need.pctBp, amount = need.amount,
    by = r.userId, key = r.keyId, iat = nowMs, exp = nowMs + minOf(ttlMs, ApprovalSlip.TTL_MAX_MS),
    nonce = PinCrypto.nonce(), reason = reason.replace('\n', ' ').replace('\r', ' ').trim().take(ApprovalSlip.MAX_REASON),
  )

  /**
   *  PIN ⇐ امضا. ردیفِ برگشتی (شمارِ خطا) را برنامه باید ذخیره کند.
   *  @return برگهٔ امضاشده، یا null (PINِ غلط، قفل، بی‌کلید، یا پیش‌نویسِ نادرست)
   */
  fun approve(r: Record, pin: String, draft: ApprovalSlip.Slip): Pair<Record, ApprovalSlip.Slip?> {
    if (!r.hasKey || draft.by != r.userId || draft.key != r.keyId) return r to null
    val v = verify(r, pin)
    if (!v.ok) return v.record to null
    val pkcs8 = PinCrypto.open(r.sealed, pin, PinCrypto.unb64(r.keySalt), r.iterations) ?: return v.record to null
    val sig = PinCrypto.sign(pkcs8, ApprovalSlip.payload(draft))
    pkcs8.fill(0)
    val signed = draft.copy(sig = sig)
    return v.record to (if (ApprovalSlip.check(signed) == null) signed else null)
  }

  private val FIELDS = listOf(
    "userId", "name", "role", "salt", "iterations", "check", "failures", "locked",
    "keyId", "spki", "keySalt", "sealed", "registered",
  )

  /** ردیف ⇐ متن؛ نام با base64 تا هر نویسه‌ای بماند */
  fun encode(r: Record): String {
    val name = PinCrypto.b64(r.name.toByteArray(Charsets.UTF_8))
    val values = listOf(
      r.userId, name, r.role, r.salt, r.iterations.toString(), r.check, r.failures.toString(), r.locked.toString(),
      r.keyId, r.spki, r.keySalt, r.sealed, r.registered.toString(),
    )
    return FIELDS.zip(values).joinToString("\n") { (k, v) -> "$k=$v" }
  }

  fun decode(s: String?): Record? {
    if (s.isNullOrBlank()) return null
    val m = s.lineSequence().mapNotNull { line ->
      val i = line.indexOf('=')
      if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
    }.toMap()
    return runCatching {
      Record(
        userId = m.getValue("userId"),
        name = String(PinCrypto.unb64(m["name"] ?: ""), Charsets.UTF_8),
        role = m.getValue("role"),
        salt = m.getValue("salt"),
        iterations = m.getValue("iterations").toInt(),
        check = m.getValue("check"),
        failures = m["failures"]?.toIntOrNull() ?: 0,
        locked = m["locked"] == "true",
        keyId = m["keyId"] ?: "",
        spki = m["spki"] ?: "",
        keySalt = m["keySalt"] ?: "",
        sealed = m["sealed"] ?: "",
        registered = m["registered"] == "true",
      )
    }.getOrNull()?.takeIf { it.userId.isNotEmpty() && it.iterations > 0 }
  }
}
