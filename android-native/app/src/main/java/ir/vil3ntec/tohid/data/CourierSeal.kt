package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.PhotoSync

/**
 *  DLV-10 — نگه‌داریِ رمزدارِ کارت‌های مأموریت روی گوشیِ پیک (نشانی، نشانه و نامِ مشتری)، با همان رمزِ دفترِ دستگاه
 *  (SEC-01، `AtRest`: AES-256-GCM با کلیدِ Keystore/Keychain؛ تنظیمِ `atrest_encrypt`، پیش‌فرض **خاموش**). خالص: رمز و
 *  بازکردن از بیرون می‌آیند (`CourierStore` ⇐ `AtRest.encode`/`AtRest.open`).
 *
 *   • خاموش (یا کلید ساخته نشد ⇒ `AtRest.encode` همان بایت‌ها را برمی‌گرداند) ⇒ همان JSON ِ امروز، بایت‌به‌بایت.
 *   • روشن ⇒ `PREFIX` + base64 ِ پاکتِ رمزدار در همان کلیدِ ترجیحات.
 *   • خواندن هر دو شکل را می‌خواند (روشن/خاموش فقط نوشتن را عوض می‌کند). پاکتی که باز نشد ⇒ null ⇒ «کارتی نیست» تا پرسشِ
 *     بعدی؛ کارت‌ها فقط رونوشتِ آخرین پاسخِ سرورند و صفِ رویدادها (`queue`) جداست و دست نمی‌خورد.
 */
object CourierSeal {

  const val PREFIX = "atrest1:"

  /** [json] ⇐ آنچه در ترجیحات می‌نشیند؛ [seal] بایت‌های رمزدار یا همان بایت‌ها (خاموش/بی کلید) */
  fun wrap(json: String, seal: (ByteArray) -> ByteArray): String {
    val plain = json.encodeToByteArray()
    val out = runCatching { seal(plain) }.getOrNull() ?: return json
    return if (out.contentEquals(plain)) json else PREFIX + PhotoSync.b64(out)
  }

  /** نوشتهٔ ترجیحات ⇐ JSON؛ پاکتی که باز نشد ⇒ null (هرگز متنِ نیمه یا پاکتِ خام به رمزگشای کارت نمی‌رسد) */
  fun unwrap(stored: String?, open: (ByteArray) -> ByteArray): String? {
    if (stored == null || !stored.startsWith(PREFIX)) return stored
    return runCatching { open(PhotoSync.unb64(stored.substring(PREFIX.length))).decodeToString() }.getOrNull()
  }
}
