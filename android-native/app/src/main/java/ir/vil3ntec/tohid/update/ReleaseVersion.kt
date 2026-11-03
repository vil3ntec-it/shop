package ir.vil3ntec.tohid.update

/**
 *  قاعده‌های شمارهٔ نسخه — خالص و یک نسخه برای هر سه سکو (آیفون گامِ ۷).
 *
 *  پیش‌تر در `Updater` ِ اندروید، `UpdaterDesk` ِ کامپیوتر و `UpdaterIos` ِ آیفون سه بار نوشته شده بود؛
 *  حالا هر سه `Updater.versionOf`/`Updater.isNewer` (همان API) همین را صدا می‌زنند.
 */
object ReleaseVersion {

  /**
   *  شمارهٔ نسخه را از نامِ فایل بیرون می‌کشد: `Tohid-Native-3.2.45.apk` →
   *  `3.2.45`. اگر نام شماره نداشت، سراغِ برچسب می‌رود.
   */
  fun of(assetName: String, tag: String): String {
    val fromName = Regex("(\\d+(?:\\.\\d+)+)").find(assetName)?.value
    if (fromName != null) return fromName
    val fromTag = Regex("(\\d+(?:\\.\\d+)+)").find(tag)?.value
    return fromTag ?: tag.removePrefix("v")
  }

  /** ۱.۳.۰ تازه‌تر از ۱.۲.۹ است — مقایسهٔ عددی، نه الفبایی */
  fun isNewer(remote: String, local: String): Boolean {
    val a = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
    val b = local.split(".", "-").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(a.size, b.size)) {
      val x = a.getOrElse(i) { 0 }
      val y = b.getOrElse(i) { 0 }
      if (x != y) return x > y
    }
    // شماره یکی است ولی برچسب فرق دارد (مثلاً پیش‌نمایش) — تازه حساب نمی‌شود
    return false
  }
}
