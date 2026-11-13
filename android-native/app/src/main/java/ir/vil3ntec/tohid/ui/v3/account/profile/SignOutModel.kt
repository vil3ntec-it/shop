package ir.vil3ntec.tohid.ui.v3.account.profile

import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.sync.LogoutGuard
import ir.vil3ntec.tohid.core.sync.SignOutSafety

/**
 *  مراحلِ برگهٔ خروج (آزمون: `SignOutModelTest`) روی همان قفلِ SYN-10
 *  (`LogoutGuard`) و «خروجِ بی گم شدنِ داده» (`SignOutSafety`، `data/SafeSignOut`):
 *   A `CONFIRM` «خروج از حساب؟» · `CHECKING` فرستادنِ همه و سنجیدنِ پاسخِ سرور
 *   · B `BLOCKED` «X تغییرِ ثبت‌نشده هنوز به سرور نرسیده» (دوباره بکوش / خروجِ اجباری… / بیرون نشو)
 *   · C `WRITING` پشتیبان + گاوصندوقِ رمزدارِ همین حساب · `FORCE` پرسشِ دوم («باز هم خارج شو»).
 */
object SignOutModel {

  enum class Stage { CONFIRM, CHECKING, BLOCKED, WRITING, FORCE }

  fun pending(queued: Int, legacy: Int): Int =
    (LogoutGuard.decide(queued, legacy) as? LogoutGuard.Block)?.pending ?: 0

  /** برگه با کدام مرحله باز می‌شود */
  fun initial(queued: Int, legacy: Int): Stage = if (pending(queued, legacy) > 0) Stage.BLOCKED else Stage.CONFIRM

  /**
   *  «خروج» در مرحلهٔ A: اگر میانِ باز شدن و زدن چیزی ثبت شده باشد قفل دوباره
   *  سر می‌رسد (`BLOCKED`)؛ null یعنی «همه را بفرست و بسنج» (`CHECKING`).
   */
  fun onConfirm(queued: Int, legacy: Int): Stage? = if (pending(queued, legacy) > 0) Stage.BLOCKED else null

  /** پس از «همه را بفرست»: آزاد ⇒ null (همین حالا خارج شو)، وگرنه `BLOCKED` با دلیل */
  fun afterCheck(verdict: SignOutSafety.Verdict): Stage? = if (verdict is SignOutSafety.Allow) null else Stage.BLOCKED

  /** جملهٔ دلیلِ نگه داشتن — هر کدام کارِ خودش را به کاربر می‌گوید */
  fun reasonText(reason: SignOutSafety.Reason): String = when (reason) {
    SignOutSafety.Reason.OFFLINE -> StrAccount.unsyncedOffline
    SignOutSafety.Reason.REJECTED -> StrAccount.unsyncedRejected
    SignOutSafety.Reason.NOT_RECONCILED -> StrAccount.unsyncedNeverSent
    SignOutSafety.Reason.NOT_SIGNED_IN -> StrAccount.unsyncedSessionEnded
    SignOutSafety.Reason.FAILED, SignOutSafety.Reason.NONE -> StrAccount.unsyncedFailed
  }

  /**
   *  AUTH-06 «خروج از همهٔ دستگاه‌ها» (`profile/signout-all`): همان قفلِ SYN-10 و همان پشتیبان پیش از هر چیز، سپس
   *  `POST /auth/logout-all`. فقط پذیرشِ سرور ⇒ null (خروجِ همین دستگاه با `SafeSignOut.finish`)؛ رد ⇒ همان تأیید با
   *  خطای سرور — نشست‌های دستگاه‌های دیگر باز ماندند و برگه نباید «خارج شدید» بگوید.
   */
  fun afterEverywhere(serverOk: Boolean): Stage? = if (serverOk) null else Stage.CONFIRM

  /** خروجِ اجباری فقط با پشتیبانِ نوشته‌شده و دو تأیید («خروجِ اجباری…» + «باز هم خارج شو») */
  fun canForce(backupWritten: Boolean): Boolean = LogoutGuard.canForce(backupWritten, confirmations = 2)

  /** همان، با گاوصندوقِ همین حساب (`SafeSignOut.keepForAccount`) */
  fun canForce(backupWritten: Boolean, kept: Boolean): Boolean =
    SignOutSafety.canForce(confirmations = 2, backupWritten = backupWritten, keptForAccount = kept)
}
