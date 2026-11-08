'use strict';
/**
 * «شناسهٔ همگام‌سازیِ» حسابِ دوم روی همان دستگاه — قرینهٔ `core/sync/SyncReplica.kt` (برنامه) و پایهٔ
 * `server/test/e2e/lib/native.mjs` (دستگاهِ آزمون).
 *
 * برنامه برای نخستین حسابِ هر دستگاه همان `SyncStore.deviceUid` را می‌فرستد؛ حسابِ دیگری که تازه روی همان
 * دستگاه شروع می‌کند (شاگرد با کدِ شاگرد پس از خروجِ صاحب) `<deviceUid>-<tag(userId)>`. بی این، pull ِ سرور
 * opهای «همین دستگاه» را به حسابِ دیگرِ همان گوشی نمی‌داد و «حذفِ ردیفِ خودش» ردیف‌های صاحب را مالِ شاگرد می‌دید.
 * SEC-AUTH-14 (`sync-v1-auth.checkDevice`) همین یک شناسه را هم برای همان کاربر روی همان دستگاه می‌پذیرد.
 */
const MAX = 64;

/** FNV-1a ِ ۶۴ بیتی ⇒ base36 — همان `SyncReplica.tag` */
function tag(account) {
  let h = 0xcbf29ce484222325n;
  for (const b of Buffer.from(String(account), 'utf8')) {
    h ^= BigInt(b);
    h = (h * 0x100000001b3n) & 0xffffffffffffffffn;
  }
  return h.toString(36);
}

/** `SyncReplica.scoped` */
function scoped(device, account) {
  const t = tag(String(account).trim());
  return `${String(device).slice(0, MAX - t.length - 1)}-${t}`;
}

/** `SyncReplica.choose` */
function choose(device, account, rawOwner, fresh) {
  const a = String(account || '').trim();
  const owner = String(rawOwner || '').trim();
  if (!a || !owner || owner === a || !fresh) return device;
  return scoped(device, a);
}

module.exports = { tag, scoped, choose, MAX };
