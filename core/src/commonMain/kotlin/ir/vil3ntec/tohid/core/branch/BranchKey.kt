package ir.vil3ntec.tohid.core.branch

import ir.vil3ntec.tohid.core.platform.sha256

/**
 *  شعبه درونِ یک دکان — فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md`، تصمیمِ ۱/۱b/۳۴ ِ صاحب‌کار): کلیدِ دفتر و همگام‌سازی.
 *
 *  دفترِ هر شعبه روی دستگاه جداست: **حساب@دکان#شعبه** ([ledgerName]) — همان «بخشِ» سرور (`<دکان>#<شعبه>`،
 *  `server/src/lib/branch-key.js`). Sync v3 ِ هر شعبه حالِ خودش را دارد ([syncKey]/[syncFileKey]) و سرآیندِ
 *  `X-Branch-Id` ([headers]) را می‌فرستد؛ سرور با دسترسیِ همان عضو می‌سنجد (`branches.resolve`).
 *
 *  ⛔ **شعبهٔ اصلی = شناسهٔ خالی** و همه‌چیزش بایت‌به‌بایت همان امروز: همان نامِ بایگانی، همان کلیدِ همگام‌سازی، بی سرآیند.
 *  ⛔ شناسهٔ غیرِ خالی هرگز بی سرآیند (یعنی «اصلی») نمی‌رود؛ بدشکل را سرور با ۴۰۳ `branch_forbidden` رد می‌کند.
 */
object BranchKey {

  /** شعبهٔ اصلی (همهٔ دادهٔ امروزِ دکان) */
  const val MAIN = ""

  /** سرآیندِ Sync v3 (`branches.HEADER` ِ سرور) */
  const val HEADER = "X-Branch-Id"

  private val ID = Regex("^brn_[0-9a-f]{24}$")

  fun isMain(id: String): Boolean = id == MAIN

  /** همان `branches.idOf` ِ سرور: تهی یا `main` ⇐ اصلی؛ `brn_<۲۴ هگز>` ⇐ همان؛ بقیه ⇐ null */
  fun idOf(raw: String?): String? {
    val s = raw?.trim() ?: return null
    if (s.isEmpty() || s == "main") return MAIN
    return s.takeIf { ID.matches(it) }
  }

  /** نامِ دفتر: `<حساب>@<دکان>` (همان امروز) و برای شعبهٔ دیگر `<حساب>@<دکان>#<شعبه>` */
  fun ledgerName(account: String, shop: String, branch: String): String {
    val base = if (shop.isBlank()) account else "$account@$shop"
    return if (isMain(branch)) base else "$base#$branch"
  }

  /**
   *  نامِ بایگانیِ دفترِ شعبه روی دیسک از نامِ بایگانیِ اصلیِ همان حساب و دکان ([base] = `DataPartition.keyIn`).
   *  اصلی ⇐ همان [base]. شعبهٔ دیگر ⇐ `b` + ۴۰ هگز از هشِ «[base]#شعبه»: نامِ فایل (`ShopStore.safeKey`) ۴۸ نویسه
   *  بیشتر نمی‌گیرد و «حساب@دکان» خودش ۵۷ نویسه است — «#شعبه» ِ ساده همان نامِ اصلی می‌شد.
   */
  fun vaultKey(base: String, branch: String): String {
    if (isMain(branch)) return base
    return "b" + hex(sha256("$base#$branch".encodeToByteArray())).take(40)
  }

  /** کلیدِ حالِ Sync v3 در حافظه: اصلی ⇐ همان کلیدِ حساب؛ شعبه ⇐ `<حساب>#<شعبه>` */
  fun syncKey(account: String, branch: String): String = if (isMain(branch)) account else "$account#$branch"

  /**
   *  بخشِ نامِ پوشه‌های v3 (`V3StateDir`): اصلی ⇐ همان [fileKey] (`OpQueue.fileKey`)؛ شعبه ⇐ `<fileKey>-<شعبه>`.
   *  `fileKey` جز حرف، رقم و `_` ندارد ⇒ «-» هیچ دو حساب/شعبه‌ای را یکی نمی‌کند.
   */
  fun syncFileKey(fileKey: String, branch: String): String = if (isMain(branch)) fileKey else "$fileKey-$branch"

  /** سرآیندهای درخواستِ Sync v3: اصلی ⇐ هیچ (همان امروز)؛ هر شناسهٔ دیگر ⇐ `X-Branch-Id` */
  fun headers(branch: String): Map<String, String> = if (isMain(branch)) emptyMap() else mapOf(HEADER to branch)

  private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
