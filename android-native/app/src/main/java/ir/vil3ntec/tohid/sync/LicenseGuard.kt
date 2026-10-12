package ir.vil3ntec.tohid.sync

import android.content.Context

/**
 *  یک جا برای پرسیدنِ «اشتراک باز است یا نه».
 *
 *  پیش از این، هر جایی که می‌خواست بداند، خودش `License.status(...)` را
 *  صدا می‌زد. آن تابع فقط امضا و تاریخ را می‌سنجد و دو چیزِ بیرونی را
 *  نمی‌داند: ساعتِ گوشی که دستِ کاربر است، و اینکه خودِ برنامه دست‌کاری
 *  شده یا نه. پس هر صدازننده باید آن‌ها را جدا حساب می‌کرد و هیچ‌کدام
 *  نمی‌کردند.
 *
 *  حالا همه از همین‌جا می‌پرسند و هر سه بررسی یک‌جا انجام می‌شود.
 */
object LicenseGuard {

  /** یک روز ارفاق: تنظیمِ منطقهٔ زمانی یا ساعتِ تابستانی، دست‌کاری نیست */
  private const val CLOCK_SLACK = 24L * 60 * 60 * 1000

  /**
   *  ساعتی که می‌شود به آن تکیه کرد.
   *
   *  بالاترین ساعتی که تا حالا دیده‌ایم ذخیره می‌شود. اگر ساعتِ گوشی از
   *  آن عقب‌تر بیفتد، همان عددِ بالاتر ملاک است — وگرنه عقب بردنِ ساعتِ
   *  گوشی، مجوزِ تمام‌شده را دوباره معتبر می‌کرد.
   */
  fun trustedNow(state: SyncStore): Long {
    val phone = System.currentTimeMillis()
    state.clockSeen = phone
    val seen = state.clockSeen
    return if (phone < seen - CLOCK_SLACK) seen else phone
  }

  fun status(context: Context, state: SyncStore): License.Status {
    // فایلِ دست‌کاری‌شده اشتراک نمی‌گیرد
    if (!Integrity.isGenuine(context)) {
      return License.Status(License.State.INVALID, reason = "tampered")
    }
    val online = License.status(
      state.license,
      state.publicKey,
      state.deviceUid,
      trustedNow(state),
      state.accountId,
    )
    if (online.state == License.State.ACTIVE) return online
    //  مجوزِ سرور نیست یا تمام شده ⇒ کدِ آفلاینِ همین کامپیوتر، اگر زده شده
    return offline(context, state) ?: online
  }

  /* ── کدِ اشتراکِ آفلاین ─────────────────────────────────────────── */

  /** «شناسهٔ کامپیوتر» که برنامه نشان می‌دهد و پنل با آن کد می‌سازد */
  fun computerId(context: Context): String =
    OfflineCode.computerId(ir.vil3ntec.tohid.ui.Platform.machineFingerprint(context))

  private fun check(context: Context, state: SyncStore, code: String): OfflineCode.Verdict =
    OfflineCode.verify(
      code, state.publicKey, ir.vil3ntec.tohid.ui.Platform.machineFingerprint(context),
      state.accountId, trustedNow(state),
    )

  /** وضعیتِ «فعال» از کدِ آفلاینِ ذخیره‌شده، یا null */
  fun offline(context: Context, state: SyncStore): License.Status? {
    val code = state.offlineCode ?: return null
    val p = (check(context, state, code) as? OfflineCode.Verdict.Ok)?.code ?: return null
    val ends = if (p.permanent) p.issuedAt + 50L * 365 * 86_400_000L else p.endsAt
    return License.Status(
      License.State.ACTIVE,
      License.Payload(
        deviceUid = state.deviceUid, accountId = state.accountId,
        issuedAt = p.issuedAt, notBefore = p.issuedAt, expiresAt = ends, subscriptionEndsAt = ends,
        features = emptyList(), core = emptyList(), plan = "offline", planTitle = p.planTitle,
      ),
    )
  }

  /**
   *  زدنِ کد در برنامه ⇒ null یعنی نشست؛ وگرنه پیامِ فارسیِ دلیل.
   *
   *  ⛔ «یک بار»: سریالی که یک بار این‌جا نشسته (و بعد برداشته یا با کدِ
   *  دیگری جایگزین شده) دوباره پذیرفته نمی‌شود.
   */
  fun applyOffline(context: Context, state: SyncStore, raw: String): String? {
    val v = check(context, state, raw)
    if (v is OfflineCode.Verdict.Bad) return OfflineCode.reasonText(v.reason)
    val p = (v as OfflineCode.Verdict.Ok).code
    val current = state.offlineCode?.let { OfflineCode.parse(it)?.serial }
    if (p.serial == current) return null
    if (p.serial in state.offlineUsed) return OfflineCode.reasonText("used")
    current?.let { state.markOfflineUsed(it) }
    state.markOfflineUsed(p.serial)
    state.offlineCode = OfflineCode.normalized(raw)
    return null
  }

  /** سرور گفت این کد دیگر کار نمی‌کند (باطل، دکانِ دیگر، حسابِ دیگر) ⇒ برداشته می‌شود */
  fun dropOffline(state: SyncStore) {
    state.offlineCode?.let { OfflineCode.parse(it)?.serial }?.let { state.markOfflineUsed(it) }
    state.offlineCode = null
  }
}
