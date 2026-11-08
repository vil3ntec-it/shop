package ir.vil3ntec.tohid.core.sync

/**
 *  «شناسهٔ همگام‌سازیِ» هر حساب روی هر دستگاه (`device_id` ِ push/pull ِ Sync v1 و سریِ سند) — منطقِ خالصِ مشترکِ
 *  گوشی، کامپیوتر و آیفون.
 *
 *  ── اشکالی که این می‌بندد (سفرِ J2b، `server/test/e2e/journeys-a.e2e.mjs`) ─────────────────────
 *  پس از #986 دفتر، صف، سایه و cursor مالِ **حساب**اند (`SyncV1Keys`)، ولی سرور هنوز با `device_id` ِ **دستگاه**
 *  (`SyncStore.deviceUid`) کار می‌کرد. صاحب روی گوشی خارج شد و شاگرد با کدِ شاگرد روی **همان گوشی** وارد شد:
 *   • pull ِ سرور opهای «همین دستگاه» را نمی‌دهد ⇒ فروش‌های شاگرد هرگز به دفترِ صاحب روی همان گوشی نمی‌رسید
 *     (و برعکس) — موجودی و جمعِ فروشِ آن گوشی برای همیشه با سرور و دستگاه‌های دیگر فرق می‌کرد؛
 *   • «حذفِ ردیفِ خودش» (`data.delete.own`) با همان شناسه می‌سنجد ⇒ شاگرد ردیف‌هایی را که صاحب از همان گوشی
 *     ساخته بود پاک می‌کرد؛ تعارضِ میانِ آن دو حساب هم «همان دستگاه» دیده و ثبت نمی‌شد.
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  قاعده: نخستین حسابی که روی این دستگاه همگام شد (یا هر حسابی که پیش از این نسخه با شناسهٔ خام همگام شده بود)
 *  همان شناسهٔ خام را نگه می‌دارد — هیچ چیزی برای دکانِ امروز عوض نمی‌شود، سریِ سند (`A1`) هم همان. حسابِ
 *  دیگری که **تازه** روی همین دستگاه شروع می‌کند (هرگز از این دستگاه push نکرده) شناسهٔ خودش را می‌گیرد
 *  (`<خام>-<برچسبِ حساب>`) — پیش از نخستین push و pull اش انتخاب و نگه داشته می‌شود. حسابی که پیش‌تر با شناسهٔ خام
 *  push کرده هرگز شناسه‌اش عوض نمی‌شود: opهای خودش پس از cursor با شناسهٔ تازه «دیگری» دیده می‌شدند و `$inc` ِ
 *  موجودی دو بار می‌نشست.
 *  Kotlin ِ خالص (commonMain)، بی java.*؛ قرینهٔ `replicaChoose` در `server/test/e2e/lib/native.mjs`.
 */
object SyncReplica {

  /** سقفِ `device_id` روی سرور ۶۴ نویسه است (`validate.id`) */
  private const val MAX = 64

  /**
   *  @param device شناسهٔ خامِ دستگاه (`SyncStore.deviceUid`)
   *  @param account حسابِ واردشده (خالی ⇒ شناسهٔ خام)
   *  @param rawOwner حسابی که شناسهٔ خام را گرفته است (خالی ⇒ هنوز هیچ)
   *  @param fresh این حساب از این دستگاه هرگز push نکرده (`SyncStateV1.lastPushAt == 0`)
   */
  fun choose(device: String, account: String, rawOwner: String, fresh: Boolean): String {
    val a = account.trim()
    if (a.isEmpty() || rawOwner.isBlank() || rawOwner.trim() == a || !fresh) return device
    return scoped(device, a)
  }

  /** `<خام>-<برچسب>` — پایدار برای همان حساب روی همان دستگاه، با نویسه‌های مجازِ سرور */
  fun scoped(device: String, account: String): String {
    val t = tag(account.trim())
    return device.take(MAX - t.length - 1) + "-" + t
  }

  /** FNV-1a ِ ۶۴ بیتی ِ حساب ⇒ base36 (بی وابستگی؛ فقط برای جدا کردن، نه امنیت) */
  fun tag(account: String): String {
    var h = -0x340d631b7bdddcdbL
    for (b in account.encodeToByteArray()) {
      h = h xor (b.toLong() and 0xff)
      h *= 0x100000001b3L
    }
    return h.toULong().toString(36)
  }
}
