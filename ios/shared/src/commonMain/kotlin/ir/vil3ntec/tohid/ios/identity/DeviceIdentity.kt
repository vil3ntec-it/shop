package ir.vil3ntec.tohid.ios.identity

/*
 *  هویتِ دستگاهِ آیفون پس از بازیابی از پشتیبان — منطقِ خالص (commonMain، بی java.*؛ آزمون: ‎jvmTest/…/identity/DeviceIdentityJvmTest‎).
 *  لایهٔ نازکِ iOS (کلیددان، پرونده، NSUserDefaults): ‎iosMain/…/ios/identity/IosDeviceIdentity.kt‎.
 *
 *  ── اشکالی که این می‌بندد ───────────────────────────────────────────────
 *  اندروید پنج پروندهٔ «فقط همین دستگاه» را از پشتیبان کنار می‌گذارد (`backup_rules.xml`: `tohid-sync` با `device_uid` و
 *  `replica_owner`، توکن‌ها، PIN ِ شاگرد، سریِ سند)؛ گوشیِ بازیابی‌شده شناسهٔ تازه می‌گیرد، سریِ سندش را از سرور، و با ورودِ
 *  دوباره دستگاهِ تازه است. NSUserDefaults و `Application Support` ِ آیفون همیشه در پشتیبانِ iCloud/Finder اند ⇒ آیفونِ
 *  تازه‌ای که از پشتیبانِ آیفونِ دیگر بالا بیاید همان `device_uid` و `replica` را داشت: دو گوشی با یک شناسه (op ِ
 *  هم‌شناسه که سرور «همین دستگاه» می‌بیند و به دیگری نمی‌دهد، «A1-155» ِ دوتایی، مجوزِ یک دستگاه روی دو گوشی).
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  ── قاعدهٔ تشخیص (هر دو سو مهم است: تشخیصِ نادرست شناسه و مجوزِ گوشیِ بازیابی‌نشده را عوض می‌کند) ──
 *  سه نشانه:
 *   • prefs (با پشتیبان می‌رود): `device_uid` و `device_mirrored` = «این نسخه هویت را روی دستگاهی آینه کرده است»؛
 *   • کلیددان، «پس از نخستین بازگشایی، فقط همین دستگاه»: به دستگاهِ دیگر نمی‌رود (بازیابی روی **همان** دستگاه برش می‌گرداند)؛
 *   • پرونده‌ای بیرون از پشتیبان (`NSURLIsExcludedFromBackupKey`) در ظرفِ همین برنامه: با به‌روزرسانی می‌ماند، با پشتیبان نه.
 *  «بازیابی روی دستگاهِ دیگر» فقط وقتی همهٔ این‌ها با هم: `device_mirrored` هست (پس نبودنِ آینه معنی دارد) **و** کلیددان
 *  یقیناً همان شناسه را ندارد **و** پرونده یقیناً همان شناسه را ندارد. هر نشانهٔ ناخوانا (پیش از نخستین بازگشایی، خطای
 *  کلیددان یا پرونده) ⇒ هیچ تصمیمی و هیچ نوشتنی؛ بارِ بعد. نصبِ نسخهٔ پیشین (بی `device_mirrored`) هرگز «بازیابی» دیده
 *  نمی‌شود — آینه نوشته می‌شود و شناسه می‌ماند (پشتیبانِ گرفته‌شده پیش از نخستین اجرای این نسخه پس شناخته نمی‌شود: همان
 *  رفتارِ امروز، نه چرخشِ نابه‌جا).
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  همه `internal`: نه API ِ Swift، نه نامی در چارچوبِ ObjC (`TohidShared<نام>`، ‎ObjcNamesJvmTest‎).
 */

/** خواندنِ یک نشانهٔ «فقط همین دستگاه» */
internal sealed interface Probe {
  /** یقیناً نیست (errSecItemNotFound، پرونده نیست) */
  object Absent : Probe {
    override fun toString() = "Absent"
  }

  data class Value(val uid: String) : Probe

  /** نمی‌دانیم (دستگاه قفل، خطای کلیددان یا پرونده) ⇒ هیچ تصمیمی */
  data class Unavailable(val why: String) : Probe
}

/** آنچه هنگامِ آغاز دیده شد */
internal data class Seen(
  /** دادهٔ حفاظت‌شده خواناست (پس از نخستین بازگشایی) — پیش از آن NSUserDefaults خالی می‌نماید */
  val protectedData: Boolean,
  val uid: String?,
  val mirrored: Boolean,
  val rotatingFrom: String?,
  val rotatingTo: String?,
  val keychain: Probe,
  val marker: Probe,
)

internal sealed interface Verdict {
  /** هیچ تصمیمی؛ هیچ نوشتنی */
  data class Undecided(val why: String) : Verdict

  /** هنوز هیچ شناسه‌ای: شناسهٔ تازه و آینه */
  object Fresh : Verdict {
    override fun toString() = "Fresh"
  }

  /** همین دستگاه؛ [repair] ⇒ آینه، پرونده یا نشان دوباره نوشته می‌شود (شناسه هرگز) */
  data class Same(val repair: Boolean) : Verdict

  /** نصبِ پیش از این نسخه: آینه نوشته می‌شود، شناسه هرگز عوض نمی‌شود */
  object Upgrade : Verdict {
    override fun toString() = "Upgrade"
  }

  /** بازیابی روی دستگاهِ دیگر: شناسه و replica ِ تازه، همان رفتارِ اندروید پس از بازیابی */
  data class Restored(val old: String) : Verdict

  /** چرخشِ نیمه‌کاره (بسته شدنِ برنامه وسطِ کار): همان شناسهٔ تازه تا ته */
  data class Resume(val from: String, val to: String) : Verdict
}

/** کلیدها و نام‌ها — همان نام‌های اندروید؛ کارِ دیگری که با هویتِ دستگاه هماهنگ می‌شود فقط از همین‌ها */
internal object DeviceIdentityKeys {
  const val SUITE = "tohid-sync"
  const val DEVICE = "device_uid"
  const val REPLICA_OWNER = "replica_owner"
  const val MIRRORED = "device_mirrored"
  const val ROTATING_FROM = "device_rotating_from"
  const val ROTATING_TO = "device_rotating_to"

  /** `SyncStateV1.replica` ِ هر حساب: `<کلیدِ حساب>.replica` */
  const val V1_SUITE = "tohid-sync-v1"
  const val REPLICA_SUFFIX = ".replica"

  /**
   *  همان شش پروندهٔ بیرون از پشتیبانِ اندروید (`backup_rules.xml`؛ آزمون همین را با آن می‌سنجد). `tohid-backup-device`:
   *  پرچمِ «پشتیبانِ دستی و پوشه هم رمزدار» (OPS-02) تنظیمِ همین دستگاه است — کلیدِ عبارت روی گوشیِ تازه نیست، پس پرچم هم نرود.
   */
  val DEVICE_ONLY: List<String> = listOf(SUITE, "tohid-session", "tohid-session-secure", "tohid-staff-pins", "tohid-doc-series", "tohid-backup-device")

  /**
   *  کلیدهای `tohid-sync` که چرخش برنمی‌دارد: هویت (بازنویسی)، مالکِ شناسهٔ خام (با replicaها جابه‌جا می‌شود) و سایهٔ
   *  همگام‌سازی (`shadow`، `rev`، `last_sync` — جای سایه کارِ دیگری است و این‌جا دست نمی‌خورد). بقیه همان است که
   *  اندروید با کنار گذاشتنِ کلِ پرونده از دست می‌دهد: مجوز، کدِ آفلاین، نمایه، `clock_seen`…
   */
  val SYNC_KEEP: Set<String> = setOf(DEVICE, REPLICA_OWNER, MIRRORED, ROTATING_FROM, ROTATING_TO, "shadow", "rev", "last_sync")

  const val KEYCHAIN_ACCOUNT = "device-identity"
  const val MARKER_DIR = "tohid-device"
  const val MARKER_FILE = "device-id"
}

internal object DeviceIdentity {

  fun decide(s: Seen): Verdict {
    if (!s.protectedData) return Verdict.Undecided("داده‌های حفاظت‌شده هنوز بسته‌اند (پیش از نخستین بازگشایی)")
    (s.keychain as? Probe.Unavailable)?.let { return Verdict.Undecided("کلیددان: ${it.why}") }
    (s.marker as? Probe.Unavailable)?.let { return Verdict.Undecided("پرونده: ${it.why}") }
    val to = s.rotatingTo?.takeIf { it.isNotBlank() }
    if (to != null) return Verdict.Resume(s.rotatingFrom?.takeIf { it.isNotBlank() } ?: s.uid.orEmpty(), to)
    val uid = s.uid?.takeIf { it.isNotBlank() } ?: return Verdict.Fresh
    val keychainSame = s.keychain == Probe.Value(uid)
    val markerSame = s.marker == Probe.Value(uid)
    if (keychainSame) return Verdict.Same(repair = !s.mirrored || !markerSame)
    if (!s.mirrored) return Verdict.Upgrade
    //  همان ظرفِ برنامه (پرونده با به‌روزرسانی می‌ماند و با پشتیبان نمی‌رود): کلیددان گم شده، دستگاه همان است
    if (markerSame) return Verdict.Same(repair = true)
    return Verdict.Restored(uid)
  }

  /**
   *  replica ِ یک حساب پس از چرخشِ [from] ⇒ [to]: شناسهٔ خام ⇒ خامِ تازه، `<خام>-<برچسب>` ⇒ `<تازه>-<برچسب>` (جدایی
   *  حساب‌های یک دستگاه، `core/sync/SyncReplica.kt`، می‌ماند). replicaی که از [from] نیست ⇒ `null` (برداشته؛ از شناسهٔ تازه
   *  دوباره انتخاب می‌شود) — هرگز نگه داشتنِ شناسه‌ای که شاید مالِ گوشیِ دیگر است.
   */
  fun remap(replica: String, from: String, to: String): String? = when {
    replica.isBlank() -> null
    replica == to || replica.startsWith("$to-") -> replica
    from.isNotBlank() && replica == from -> to
    from.isNotBlank() && replica.startsWith("$from-") -> to + replica.substring(from.length)
    else -> null
  }

  /** همان قالبِ `SyncStore.newDeviceUid` ِ اندروید: ۱۶ بایت ⇒ ۳۲ نویسهٔ hex ِ کوچک */
  fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    for (b in bytes) {
      val v = b.toInt() and 0xff
      append(DIGITS[v shr 4]).append(DIGITS[v and 0xf])
    }
  }

  /** برای گزارش: فقط سرِ شناسه */
  fun short(uid: String?): String = uid.orEmpty().take(8).ifEmpty { "-" }

  private const val DIGITS = "0123456789abcdef"
}
