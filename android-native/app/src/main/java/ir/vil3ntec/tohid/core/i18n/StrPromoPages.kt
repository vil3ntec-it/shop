package ir.vil3ntec.tohid.core.i18n

/**
 *  تخفیف‌های خودکار و کدهای تخفیفِ صندوق (`ui/screens/Promos.kt`): فهرستِ قاعده‌ها، کلیدِ روشن/خاموش، کارِ گروهی، حذف و ویرایشگرِ
 *  قاعده (نوع، پله‌ها، دامنه، زمان، ترکیب و کد). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری،
 *  دورِ ۳). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. سنجیدنِ «همیشه» با
 *  خروجیِ `PromoText.windowText` منطق است و در صفحه «// داده:» دارد. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrPromoPages {
  // ── `Promos.kt` ──

  // فهرستِ قاعده‌ها
  val scope: String get() = t("دامنه", "Scope", "ساحه")
  val stacking: String get() = t("ترکیب", "Stacking", "یوځای کېدل")
  fun rulesNotSaved(n: Any?): String = t("${asIs(n)} قاعده ذخیره نشد", "$n ${pl(n ?: 0, "rule", "rules")} not saved", "$n قاعدې خوندي نه شوې")
  val subtitle: String get() = t(
    "قاعده‌های خودکار و کدهای تخفیفِ صندوق",
    "Automatic rules and till discount codes",
    "اتومات قاعدې او د دخل د تخفیف کوډونه",
  )
  val newRuleBtn: String get() = t("+ قاعدهٔ تازه", "+ New rule", "+ نوې قاعده")
  val signInNote: String get() = t(
    "تخفیف‌ها مالِ حسابِ دکان‌اند. برای دیدن و ساختنشان واردِ حسابِ دکان شوید.",
    "Discounts belong to the shop account. Sign in to the shop account to see and create them.",
    "تخفیفونه د دوکان د حساب دي. د لیدو او جوړولو لپاره یې د دوکان حساب ته ننوځئ.",
  )
  val autoAtTill: String get() = t("تخفیف‌های خودکار در صندوق", "Automatic discounts at the till", "په دخل کې اتومات تخفیفونه")
  val autoOnNote: String get() = t(
    "روشن: قاعده‌های فعال در سبدِ همهٔ دستگاه‌های این دکان حساب می‌شوند.",
    "On: active rules are applied to the cart on every device of this shop.",
    "روښانه: فعالې قاعدې د دې دوکان د ټولو وسیلو په ټوکرۍ کې حسابېږي.",
  )
  val autoOffNote: String get() = t(
    "خاموش: صندوق دقیقاً همان جمعِ همیشه را حساب می‌کند.",
    "Off: the till calculates exactly the usual total.",
    "بند: دخل هماغه تل والا ټولټال حسابوي.",
  )
  val autoTurnedOn: String get() = t("تخفیف‌های خودکار روشن شد", "Automatic discounts turned on", "اتومات تخفیفونه روښانه شول")
  val autoTurnedOff: String get() = t("تخفیف‌های خودکار خاموش شد", "Automatic discounts turned off", "اتومات تخفیفونه بند شول")
  val withCode: String get() = t("با کد", "With code", "له کوډ سره")
  val timed: String get() = t("زمان‌دار", "Timed", "وخت لرونکې")
  val hourOrDay: String get() = t("ساعت یا روز", "Hour or day", "ساعت یا ورځ")
  val ruleWord: String get() = t("قاعده", "Rules", "قاعده")
  val searchNameCode: String get() = t("جست‌وجو در نام یا کد", "Search name or code", "په نوم یا کوډ کې لټون")
  val noRulesYet: String get() = t(
    "هنوز قاعده‌ای نیست. با «قاعدهٔ تازه» اولی را بسازید.",
    "No rules yet. Create the first one with “New rule”.",
    "لا قاعده نشته. لومړۍ یې د «نوې قاعده» سره جوړه کړئ.",
  )
  val noSearchMatch: String get() = t("چیزی با این جست‌وجو نیست.", "Nothing matches this search.", "له دې لټون سره څه نشته.")
  val ruleSaved: String get() = t("قاعده ذخیره شد", "Rule saved", "قاعده خوندي شوه")
  val deleteRule: String get() = t("حذفِ قاعده", "Delete rule", "د قاعدې ړنګول")
  fun removedFromTill(names: Any?): String = t(
    "${asIs(names)} از صندوق برداشته می‌شود.",
    "$names will be removed from the till.",
    "$names له دخل څخه لرې کېږي.",
  )
  fun codesNotFreed(codes: Any?): String = t(
    " کدِ ${asIs(codes)} آزاد نمی‌شود و دوباره به کار نمی‌رود.",
    " Code $codes isn't freed and can't be used again.",
    " $codes کوډ نه خلاصېږي او بیا نه کارول کېږي.",
  )
  fun rulesNotDeleted(n: Any?): String = t("${asIs(n)} قاعده حذف نشد", "$n ${pl(n ?: 0, "rule", "rules")} not deleted", "$n قاعدې ونه ړنګېدې")
  val editRule: String get() = t("ویرایشِ قاعده", "Edit rule", "د قاعدې سمون")

  // ویرایشگرِ قاعده
  val newRule: String get() = t("قاعدهٔ تازه", "New rule", "نوې قاعده")
  val nameOnCart: String get() = t("نام (روی سبد دیده می‌شود)", "Name (shown on the cart)", "نوم (په ټوکرۍ کې ښکاري)")
  val discountPercent: String get() = t("درصدِ تخفیف", "Discount percent", "د تخفیف فیصده")
  val amountPerUnit: String get() = t("مبلغ از هر واحد (؋)", "Amount off each unit (؋)", "له هرې دانې مبلغ (؋)")
  val specialUnitPrice: String get() = t("قیمتِ ویژهٔ هر واحد (؋)", "Special price per unit (؋)", "د هرې دانې ځانګړې بیه (؋)")
  val fromAmount: String get() = t("از مبلغِ (؋)", "From amount (؋)", "له مبلغ (؋)")
  val fromCount: String get() = t("از تعدادِ", "From count", "له شمېر")
  val discountAfn: String get() = t("تخفیف (؋)", "Discount (؋)", "تخفیف (؋)")
  val addTier: String get() = t("+ پله", "+ Tier", "+ پوړ")
  val buyN: String get() = t("بخر", "Buy", "واخلئ")
  val getN: String get() = t("بگیر", "Get", "ترلاسه کړئ")
  val getDiscount: String get() = t("تخفیفِ «بگیر» (٪)", "“Get” discount (%)", "د «ترلاسه» تخفیف (٪)")
  val bxgyHint: String get() = t(
    "ارزان‌ترین واحدهای دامنه تخفیف می‌گیرند — «هر ۵ تا، دو ارزان‌تر رایگان» = بخر ۳ بگیر ۲.",
    "The cheapest units in scope get the discount — “every 5, the two cheapest free” = buy 3 get 2.",
    "د ساحې ارزانه دانې تخفیف اخلي — «هرو ۵ کې دوه ارزانه وړیا» = ۳ واخلئ ۲ ترلاسه کړئ.",
  )
  val bundlePrice: String get() = t("قیمتِ بسته (؋)", "Bundle price (؋)", "د بستې بیه (؋)")
  val daysBeforeExpiry: String get() = t("چند روز پیش از انقضا", "How many days before expiry", "د پای نېټې څو ورځې مخکې")
  val expiryHint: String get() = t(
    "فقط واحدهایی که در محموله‌های تا این چند روز مانده به انقضا نشسته‌اند تخفیف می‌گیرند (اول آنچه زودتر تمام می‌شود). کالای منقضی تخفیف نمی‌گیرد.",
    "Only units in shipments with this many days or fewer left to expiry get the discount (whatever runs out first goes first). Expired items get no discount.",
    "یوازې هغه دانې تخفیف اخلي چې په داسې بارونو کې دي چې د پای نېټې ته یې همدومره ورځې پاتې وي (لومړی هغه چې ژر پای ته رسېږي). تېر شوی توکی تخفیف نه اخلي.",
  )
  val onWhichItems: String get() = t("روی کدام کالاها", "On which items", "پر کومو توکو")
  val specificItems: String get() = t("کالاهای مشخص", "Specific items", "ټاکلي توکي")
  val exceptItems: String get() = t("جز این کالاها", "Except these items", "پرته له دې توکو")
  val exceptBrands: String get() = t("جز این برندها", "Except these brands", "پرته له دې برانډونو")
  val exceptCategories: String get() = t("جز این دسته‌ها", "Except these categories", "پرته له دې ډلو")
  val exceptSuppliers: String get() = t("جز این تأمین‌کنندگان", "Except these suppliers", "پرته له دې عرضه‌کوونکو")
  val scopeHint: String get() = t(
    "هیچ دسته یا کالایی نزنید ⇐ همهٔ کالاها. «جز» بر «شامل» مقدم است.",
    "Pick no category or item ⇐ all items. “Except” wins over “include”.",
    "هېڅ ډله یا توکی مه ټاکئ ⇐ ټول توکي. «پرته» پر «شامل» مخکې دی.",
  )
  val whenSection: String get() = t("چه وقت", "When", "کله")
  val fromHour: String get() = t("از ساعت (۱۷:۰۰)", "From hour (17:00)", "له ساعت (۱۷:۰۰)")
  val toHour: String get() = t("تا ساعت (۱۹:۰۰)", "To hour (19:00)", "تر ساعت (۱۹:۰۰)")
  val fromDate: String get() = t("از تاریخ (2026-10-01)", "From date (2026-10-01)", "له نېټې (2026-10-01)")
  val toDate: String get() = t("تا تاریخ", "To date", "تر نېټې")
  val whenHint: String get() = t(
    "هیچ‌کدام ⇐ همیشه. ساعتی که از نیمه‌شب رد شود (۲۲:۰۰ تا ۰۲:۰۰) به روزِ شروعش تعلق دارد.",
    "None ⇐ always. Hours that pass midnight (22:00 to 02:00) belong to their start day.",
    "هېڅ ⇐ تل. هغه ساعت چې له نیمې شپې تېرېږي (۲۲:۰۰ تر ۰۲:۰۰) د پیل ورځې پورې اړه لري.",
  )
  val stackCodeSection: String get() = t("ترکیب و کد", "Stacking & code", "یوځای کېدل او کوډ")
  val stackWithOthers: String get() = t("ترکیب با تخفیف‌های دیگر", "Stacking with other discounts", "له نورو تخفیفونو سره یوځای کېدل")
  val priority: String get() = t("اولویت", "Priority", "لومړیتوب")
  val codeOptional: String get() = t("کدِ تخفیف (اختیاری)", "Discount code (optional)", "د تخفیف کوډ (اختیاري)")
  val codeHint: String get() = t(
    "بی کد ⇐ خودکار. کد را صندوقدار در سبد می‌زند؛ کدِ هر قاعده در این دکان یکتاست.",
    "No code ⇐ automatic. The cashier enters the code in the cart; each rule's code is unique in this shop.",
    "بې کوډه ⇐ اتومات. کوډ صندوقدار په ټوکرۍ کې وهي؛ د هرې قاعدې کوډ په دې دوکان کې یوازینی دی.",
  )
  val codeCap: String get() = t("سقفِ مصرفِ کد (خالی = بی سقف)", "Code use limit (empty = no limit)", "د کوډ د کارونې سقف (تش = بې سقفه)")
  val codeCapNote: String get() = t(
    "سرور هر مصرف را می‌شمارد؛ کدِ سقف‌دار فقط با اینترنت در صندوق پذیرفته می‌شود.",
    "The server counts each use; a limited code is accepted at the till only with internet.",
    "سرور هره کارونه شمېري؛ سقف لرونکی کوډ یوازې د انټرنېټ سره په دخل کې منل کېږي.",
  )
  fun usedSoFar(n: Any?): String = t(
    " تا امروز ${asIs(n)} بار مصرف شده.",
    " Used $n ${pl(n ?: 0, "time", "times")} so far.",
    " تر نن $n ځله کارول شوی.",
  )
  val belowCost: String get() = t(
    "اجازهٔ فروش زیرِ بهای خرید (فقط صاحبِ دکان)",
    "Allow selling below cost (shop owner only)",
    "تر پېر بیې لاندې د پلور اجازه (یوازې د دوکان خاوند)",
  )
  val belowCostOwner: String get() = t(
    "صاحبِ دکان برای این قاعده «زیرِ بهای خرید» را روشن کرده؛ ذخیره با نقشِ شما آن را خاموش می‌کند.",
    "The shop owner turned on “below cost” for this rule; saving with your role turns it off.",
    "د دوکان خاوند د دې قاعدې لپاره «تر پېر بیې لاندې» روښانه کړی؛ ستاسو په رول خوندي کول یې بندوي.",
  )
  val belowCostOff: String get() = t(
    "بی آن، تخفیف هیچ کالایی را زیرِ بهای خریدش نمی‌برد.",
    "Without it, a discount never takes an item below its cost.",
    "بې له هغې، تخفیف هېڅ توکی تر پېر بیې لاندې نه راولي.",
  )

  // انتخابِ کالا
  fun searchFor(label: Any?): String = t("${asIs(label)} — جست‌وجوی نام یا بارکد", "$label — search name or barcode", "$label — د نوم یا بارکوډ لټون")
  val noItemFound: String get() = t("کالایی پیدا نشد.", "No item found.", "توکی ونه موندل شو.")
}
