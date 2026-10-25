package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.perm.ApproverVault

/**
 *  PINِ شخصیِ هر کاربر روی همین دستگاه و کلیدِ تأییدِ صاحب/مدیر — STF-04 و
 *  STF-02 (بخشِ ۱۰.۱ و ۱۰.۲).
 *
 *  هر کاربری که روی این دستگاه وارد شده و PIN گذاشته یک ردیف دارد
 *  (`ApproverVault.Record`): نمک و اثرِ PBKDF2ِ PIN، شمارِ خطا، و برای صاحب و
 *  مدیر کلیدِ خصوصیِ بسته با همان PIN. **هیچ چیزِ این‌جا همگام نمی‌شود** و در
 *  پشتیبانِ ابری هم نیست (انبارِ جدای `tohid-staff-pins`).
 *
 *  جدا از `LockStore` است: آن قفلِ کلِ برنامه با یک رمزِ دستگاه است و همان‌طور
 *  می‌ماند؛ این PINِ «هر کس برای خودش» است.
 */
class StaffPins(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun all(): List<ApproverVault.Record> =
    prefs.all.keys.filter { it.startsWith(REC) }.mapNotNull { get(it.removePrefix(REC)) }.sortedBy { it.name }

  fun get(userId: String): ApproverVault.Record? = ApproverVault.decode(prefs.getString(REC + userId, null))

  fun put(r: ApproverVault.Record) {
    prefs.edit().putString(REC + r.userId, ApproverVault.encode(r)).apply()
  }

  fun remove(userId: String) {
    prefs.edit().remove(REC + userId).apply()
  }

  /**
   *  تأییدکننده‌هایی که روی همین دستگاه کلیدِ ثبت‌شده دارند — جز خودِ
   *  درخواست‌کننده (سرور خودتأیید را رد می‌کند: `approval_self`).
   */
  fun approvers(exceptUserId: String): List<ApproverVault.Record> =
    all().filter { it.hasKey && it.registered && it.role in ApproverVault.APPROVER_ROLES && it.userId != exceptUserId }

  private companion object {
    const val PREFS = "tohid-staff-pins"
    const val REC = "rec:"
  }
}
