package ir.vil3ntec.tohid.ui

/**
 *  Ctrl+L (STF-04، S46) پس از `TillUser.lock` — کدام قفل می‌افتد. یک قاعده برای پوستهٔ برنامه (`AppRoot`، کامپیوتر
 *  و گوشیِ نسخهٔ ۳): «تعویضِ کاربر روی صندوق» روشن و با راهِ برگشت ⇒ صفحهٔ PIN ِ شخصی (`TillLockScreen`)؛ وگرنه با
 *  رمزِ برنامه ⇒ قفلِ برنامه همان دم (`AppLockScreen`)؛ بی رمزِ برنامه قفلی نیست که بیفتد ⇒ فقط راهش گفته می‌شود.
 */
object AppLockKey {
  enum class Then { TILL, APP, HINT }

  /** @param tillLocked پاسخِ `TillUser.lock`؛ @param appLock رمزِ برنامه گذاشته شده (`LockStore.enabled`) */
  fun after(tillLocked: Boolean, appLock: Boolean): Then = when {
    tillLocked -> Then.TILL
    appLock -> Then.APP
    else -> Then.HINT
  }
}
