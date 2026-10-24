package ir.vil3ntec.tohid.ui.v3.account.profile

import ir.vil3ntec.tohid.core.sync.LogoutGuard

/**
 *  مراحلِ برگهٔ خروج (آزمون: `SignOutModelTest`) روی همان قفلِ SYN-10
 *  (`LogoutGuard`) — منطق دست نخورده، فقط چیدنِ مرحله‌ها در یک برگه:
 *   A `CONFIRM` «خروج از حساب؟» · B `BLOCKED` صفِ پُر (همگام کن / خروجِ اجباری… / ماندن)
 *   · C `WRITING` نوشتنِ پشتیبان · `FORCE` پرسشِ دوم پس از پشتیبان («باز هم خارج شو»).
 */
object SignOutModel {

  enum class Stage { CONFIRM, BLOCKED, WRITING, FORCE }

  fun pending(queued: Int, legacy: Int): Int =
    (LogoutGuard.decide(queued, legacy) as? LogoutGuard.Block)?.pending ?: 0

  /** برگه با کدام مرحله باز می‌شود */
  fun initial(queued: Int, legacy: Int): Stage = if (pending(queued, legacy) > 0) Stage.BLOCKED else Stage.CONFIRM

  /**
   *  «خروج» در مرحلهٔ A: اگر میانِ باز شدن و زدن چیزی ثبت شده باشد قفل دوباره
   *  سر می‌رسد (`BLOCKED`)؛ null یعنی همین حالا خارج شو.
   */
  fun onConfirm(queued: Int, legacy: Int): Stage? = if (pending(queued, legacy) > 0) Stage.BLOCKED else null

  /** خروجِ اجباری فقط با پشتیبانِ نوشته‌شده و دو تأیید («خروجِ اجباری…» + «باز هم خارج شو») */
  fun canForce(backupWritten: Boolean): Boolean = LogoutGuard.canForce(backupWritten, confirmations = 2)
}
