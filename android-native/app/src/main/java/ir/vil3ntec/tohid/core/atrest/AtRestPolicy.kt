package ir.vil3ntec.tohid.core.atrest

/**
 *  SEC-01 — کدام فایل رمز می‌شود، کدام ساده می‌ماند و کدام با پشتیبانِ اندروید نمی‌رود.
 *
 *  همه با نامِ نسبی به `filesDir` (جداکنندهٔ `/`)، بی اندروید، تا روی JVM آزموده شود.
 *
 *  ── ساده در فاز ۱ (عمداً) ─────────────────────────────────────────────
 *   • صفِ opها (`oplog-*.json`) و ژورنال‌های همگام‌سازی (`sync-journal-*`، `sync-v3-*`):
 *     باید **بی کلید** خوانده شوند تا وقتی دفترِ رمزدار باز نمی‌شود (گوشیِ تازه،
 *     Keystore ازدست‌رفته) تغییرهای نرفته هنوز به سرور برسند.
 *   • سایه و cursorِ Sync v1 (`shared_prefs/tohid-sync-v1.xml`).
 *   • `ledger-summary.json` (چند عدد برای نگهبانِ پس‌زمینه) و `sale-draft.json` (سبدِ نیمه‌کاره).
 */
object AtRestScope {

  const val LEDGER = "shop-data.json"
  const val SAFETY = "before-restore.json"
  const val SUMMARY = "ledger-summary.json"
  const val SNAPSHOTS = "auto-backups"

  /** آیا فاز ۱ این فایل را رمز می‌کند؟ */
  fun sealable(rel: String): Boolean = when {
    rel == LEDGER || rel == SAFETY -> true
    rel == SUMMARY -> false
    !rel.contains('/') && rel.startsWith("ledger-") && rel.endsWith(".json") -> true
    rel.startsWith("$SNAPSHOTS/") && rel.endsWith(".json") -> true
    else -> false
  }

  /** حالِ همگام‌سازی که روی دیسک ساده می‌ماند و به سایه/cursor/دفتر بسته است */
  fun syncState(rel: String): Boolean =
    rel.startsWith("oplog-") || rel.startsWith("sync-journal-") || rel.startsWith("sync-v3-")

  /** فایلی که کنار گذاشته شد و دیگر بکار نمی‌رود — برای فهرستِ صفحهٔ امنیت */
  fun leftover(rel: String): Boolean =
    rel.contains(".unreadable-") || rel.contains(".lost-") || rel.contains(".corrupt-") ||
      rel.endsWith(AtRestMigration.SIDECAR_SUFFIX)

  /** نامِ prefsی که سایه، cursor و شناسهٔ حسابِ همگام‌سازی را دارد */
  val SYNC_PREFS: Set<String> = setOf("tohid-sync-v1.xml")

  /**
   *  پشتیبانِ خودکار و انتقالِ اندروید (`TohidBackupAgent`) — بازبینیِ #331.
   *
   *  Keystore با پشتیبان نمی‌رود؛ دفترِ رمزدار روی گوشیِ تازه باز نمی‌شد ولی سایهٔ پُر و
   *  صف و cursor می‌رسید ⇒ همه‌چیز KEY_MISSING، و بازیابیِ پشتیبانِ کهنه روی سایهٔ تازه‌تر
   *  DELETE می‌ساخت. پس وقتی رمز در کار است (روشن، یا هنوز فایلِ رمزدار/کلیددانی هست):
   *  **هیچ‌کدام** — نه فایل‌های رمزپذیر، نه کلیددان، نه حالِ همگام‌سازی. گوشیِ تازه مثلِ نصبِ
   *  تازه بالا می‌آید و دفتر را از سرور (Snapshot) می‌گیرد. خاموش ⇒ همان قاعدهٔ XML، بایت‌به‌بایت.
   *
   *  @return `true` یعنی این فایلِ `filesDir` در پشتیبان نمی‌رود
   */
  fun skipInDeviceBackup(rel: String): Boolean =
    sealable(rel) || rel == SUMMARY || syncState(rel) || leftover(rel) ||
      rel == "atrest" || rel.startsWith("atrest/") || rel.startsWith("$SNAPSHOTS/") || rel == SNAPSHOTS

  /** آیا رمز در کار است — تصمیمِ پشتیبانِ اندروید */
  fun activeForBackup(wanted: Boolean, anySealed: Boolean, keyringPresent: Boolean): Boolean =
    wanted || anySealed || keyringPresent
}

/**
 *  ردهٔ شکستِ KEK (بازبینیِ #331): کدام خطای Keystore **برای همیشه** است.
 *
 *  همیشگی ⇒ `KEY_MISSING` ⇒ کلیددان `Lost` و راهِ «بازسازی از سرور» پیشنهاد می‌شود؛ نه
 *  `KEY_UNAVAILABLE`ی که تا ابد «دوباره امتحان کنید» می‌گفت. فقط نام و متن دیده می‌شود تا
 *  روی JVM آزموده شود (کلاس‌های `android.security.keystore` آن‌جا نیستند).
 */
object AtRestKeyFailure {

  private val PERMANENT_TYPES = setOf(
    "android.security.keystore.KeyPermanentlyInvalidatedException",
    "java.security.UnrecoverableKeyException",
    "java.security.UnrecoverableEntryException",
  )

  /** متنِ Keystore2/KeyMint برای کلیدی که بلوبش برای همیشه بکار نمی‌آید */
  private val PERMANENT_TEXT = listOf("invalid key blob", "key blob corrupted", "keyblob", "permanently invalidated")

  fun isPermanent(t: Throwable): Boolean =
    generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(8).any { c ->
      c.javaClass.name in PERMANENT_TYPES ||
        c is java.security.UnrecoverableEntryException ||
        c.message?.lowercase()?.let { m -> PERMANENT_TEXT.any { m.contains(it) } } == true
    }
}
