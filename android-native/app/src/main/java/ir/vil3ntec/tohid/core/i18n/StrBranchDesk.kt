package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.branch.BranchBook

/**
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md`): انتخاب و تعویضِ شعبه، صفحهٔ «شعبه‌ها» ِ صاحب، گزارشِ هر شعبه
 *  و جمع، و پیامِ ردهای شعبهٔ سرور (`branch_required`/`branch_main_only`/`branch_unsupported`/`branch_forbidden`).
 *  ⛔ پرچمِ `shop.branches` خاموش ⇒ هیچ‌کدام دیده نمی‌شود.
 */
@Suppress("unused")
object StrBranchDesk {

  val title: String get() = t("شعبه‌ها", "Branches", "څانګې")
  val mainName: String get() = t("شعبهٔ اصلی", "Main branch", "اصلي څانګه")
  fun nameOf(name: String, main: Boolean): String = name.ifBlank { if (main) mainName else "—" }
  fun current(name: String): String = t("شعبهٔ این دستگاه: ${asIs(name)}", "This device's branch: $name", "د دې وسیلې څانګه: $name")
  val currentLabel: String get() = t("شعبهٔ این دستگاه", "This device's branch", "د دې وسیلې څانګه")
  val switch: String get() = t("تعویضِ شعبه", "Change branch", "څانګه بدلول")
  val pickTitle: String get() = t("در کدام شعبه کار می‌کنید؟", "Which branch are you working in?", "په کومه څانګه کې کار کوئ؟")
  val pickNote: String get() = t(
    "دادهٔ هر شعبه جداست: فروش، مصرف، موجودی و دخلِ این دستگاه فقط در شعبهٔ انتخابی می‌نشیند.",
    "Each branch keeps its own data: this device's sales, expenses, stock and cash go only to the chosen branch.",
    "د هرې څانګې معلومات جلا دي: د دې وسیلې پلور، لګښت، موجودي او صندوق یوازې په ټاکل شوې څانګه کې ثبتېږي.",
  )
  val none: String get() = t(
    "هیچ شعبه‌ای برای شما باز نیست. از صاحبِ دکان بخواهید شما را به شعبه‌ای بدهد.",
    "No branch is open to you. Ask the shop owner to assign you to a branch.",
    "تاسو ته هېڅ څانګه خلاصه نه ده. د دوکان له خاوند وغواړئ چې تاسو یوې څانګې ته وټاکي.",
  )
  fun switched(name: String): String = t("حالا در «${asIs(name)}» کار می‌کنید.", "You are now working in “$name”.", "اوس په «$name» کې کار کوئ.")
  val pending: String get() = t(
    "اول همگام‌سازیِ این دستگاه تمام شود، بعد شعبه را عوض کنید.",
    "Let this device finish syncing first, then change the branch.",
    "لومړی د دې وسیلې همغږي بشپړه شي، بیا څانګه بدله کړئ.",
  )
  val v3Off: String get() = t(
    "سرورِ شما هنوز همگام‌سازیِ شعبه‌ها را ندارد؛ فقط شعبهٔ اصلی کار می‌کند.",
    "Your server does not sync branches yet; only the main branch works.",
    "ستاسو سرور لا د څانګو همغږي نه لري؛ یوازې اصلي څانګه کار کوي.",
  )
  val notArchived: String get() = t(
    "دفترِ این شعبه کنار گذاشته نشد (جای خالیِ دستگاه را ببینید)؛ هیچ چیز عوض نشد.",
    "This branch's ledger could not be set aside (check free space); nothing changed.",
    "د دې څانګې دفتر یوې خوا ته نه شو (د وسیلې تشه ځای وګورئ)؛ هېڅ بدلون نه دی شوی.",
  )
  val training: String get() = t("در حالتِ آموزشی شعبه عوض نمی‌شود.", "The branch cannot change in training mode.", "د زده کړې په حالت کې څانګه نه بدلېږي.")
  val noShop: String get() = t("اول وارد حسابِ دکان شوید.", "Sign in to the shop account first.", "لومړی د دوکان حساب ته ننوځئ.")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "It did not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val off: String get() = t("شعبه‌ها برای این دکان روشن نیست.", "Branches are not turned on for this shop.", "څانګې د دې دوکان لپاره فعالې نه دي.")

  /** پیامِ ردِ شعبهٔ سرور — به‌جای متنِ فنیِ پاسخ (`ApiFailure.fromHttp`) */
  fun refusal(r: BranchBook.Refusal): String = when (r) {
    BranchBook.Refusal.REQUIRED -> t(
      "این دستگاه هنوز شعبه‌ای انتخاب نکرده است. از «حسابِ من ⇐ شعبه‌ها» شعبهٔ خود را انتخاب کنید.",
      "This device has not chosen a branch yet. Choose your branch in “My account ⇐ Branches”.",
      "دې وسیلې لا څانګه نه ده ټاکلې. په «زما حساب ⇐ څانګې» کې خپله څانګه وټاکئ.",
    )
    BranchBook.Refusal.MAIN_ONLY -> t(
      "این بخش فقط دادهٔ شعبهٔ اصلی را دارد و شما به شعبهٔ اصلی دسترسی ندارید.",
      "This part only holds the main branch's data, and you do not have access to the main branch.",
      "دا برخه یوازې د اصلي څانګې معلومات لري او تاسو اصلي څانګې ته لاسرسی نه لرئ.",
    )
    BranchBook.Refusal.UNSUPPORTED -> t(
      "این بخش هنوز فقط در شعبهٔ اصلی کار می‌کند. برای آن به شعبهٔ اصلی بروید.",
      "This part still works only in the main branch. Switch to the main branch to use it.",
      "دا برخه لا یوازې په اصلي څانګه کې کار کوي. د هغې لپاره اصلي څانګې ته لاړ شئ.",
    )
    BranchBook.Refusal.FORBIDDEN -> t(
      "این شعبه در دسترسیِ شما نیست یا بایگانی شده است. شعبهٔ دیگری انتخاب کنید.",
      "This branch is not open to you or has been archived. Choose another branch.",
      "دا څانګه تاسو ته خلاصه نه ده یا آرشیف شوې ده. بله څانګه وټاکئ.",
    )
  }

  //  صفحهٔ صاحب
  val ownerOnly: String get() = t("شعبه‌ها را فقط صاحبِ دکان می‌سازد و می‌دهد.", "Only the shop owner creates and assigns branches.", "څانګې یوازې د دوکان خاوند جوړوي او ټاکي.")
  val newBranch: String get() = t("شعبهٔ تازه", "New branch", "نوې څانګه")
  val nameLabel: String get() = t("نامِ شعبه", "Branch name", "د څانګې نوم")
  val create: String get() = t("ساختن", "Create", "جوړول")
  val rename: String get() = t("تغییرِ نام", "Rename", "نوم بدلول")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val archive: String get() = t("بایگانی", "Archive", "آرشیف")
  val restore: String get() = t("برگرداندن", "Restore", "بېرته راوړل")
  val archived: String get() = t("بایگانی‌شده", "Archived", "آرشیف شوې")
  val archiveNote: String get() = t(
    "دادهٔ شعبهٔ بایگانی‌شده پاک نمی‌شود و در گزارشِ صاحب می‌ماند؛ کسی دیگر در آن کار نمی‌کند. شعبهٔ اصلی بایگانی نمی‌شود.",
    "An archived branch's data is not deleted and stays in the owner's report; nobody works in it anymore. The main branch cannot be archived.",
    "د آرشیف شوې څانګې معلومات نه پاکېږي او د خاوند په راپور کې پاتې کېږي؛ نور څوک پکې کار نه کوي. اصلي څانګه آرشیف کېدای نه شي.",
  )
  val members: String get() = t("کارمندان و شعبه‌ها", "Staff and branches", "کارکوونکي او څانګې")
  val memberNote: String get() = t(
    "صاحب همهٔ شعبه‌ها را دارد. کارمندِ بی هیچ شعبه فقط شعبهٔ اصلی را دارد.",
    "The owner has every branch. A staff member with no branch has only the main branch.",
    "خاوند ټولې څانګې لري. هغه کارکوونکی چې څانګه نه لري یوازې اصلي څانګه لري.",
  )
  val ownerHasAll: String get() = t("همهٔ شعبه‌ها", "All branches", "ټولې څانګې")
  val saved: String get() = t("ذخیره شد.", "Saved.", "خوندي شو.")
  fun notSaved(why: String): String = t("ذخیره نشد: ${asIs(why)}", "Not saved: $why", "خوندي نه شو: $why")
  val report: String get() = t("گزارشِ شعبه‌ها", "Branch report", "د څانګو راپور")
  val reportNote: String get() = t(
    "هر شعبه فقط از دفترِ خودش؛ «جمعِ همه» فقط برای صاحب.",
    "Each branch only from its own ledger; “All together” only for the owner.",
    "هره څانګه یوازې له خپل دفتر؛ «ټولګه» یوازې د خاوند لپاره.",
  )
  val allTogether: String get() = t("جمعِ همهٔ شعبه‌ها", "All branches together", "د ټولو څانګو ټولګه")
}
