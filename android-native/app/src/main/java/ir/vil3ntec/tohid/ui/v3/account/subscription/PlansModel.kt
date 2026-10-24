package ir.vil3ntec.tohid.ui.v3.account.subscription

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat

/**
 *  «اشتراک و قیمت‌ها» (آزمون: `PlansModelTest`) — جای `VipScreen` بی طلا و بی نشانِ VIP.
 *  قیمت‌ها از سرور (`Backend.account.plans`)؛ تا نرسیده یا آفلاین همان سه پلنِ
 *  پیش‌فرضِ پیشین. کارتِ حالت یکی از سه است، نه دو کارتِ متناقض: وضعیتِ مجوزِ گوشی،
 *  دورهٔ باز به حرفِ سرور، یا دعوت به آزمایشی.
 */
object PlansModel {

  data class Option(
    val code: String,
    val title: String,
    val price: Long,
    val fullPrice: Long = price,
    val badge: String = "",
    val days: Int,
    val discountLabel: String = "",
  ) {
    val discounted: Boolean get() = fullPrice > price
    val offPercent: Int get() = if (!discounted || fullPrice <= 0) 0 else ((fullPrice - price) * 100 / fullPrice).toInt()
    /** قیمتِ هر روز — فقط برای مقایسه */
    val perDay: Double get() = if (days <= 0) 0.0 else price.toDouble() / days
  }

  val fallback: List<Option>
    get() = listOf(
      Option("m1", StrAccount.planMonthly, 500, days = 30),
      Option("m6", StrAccount.planSixMonths, 2500, badge = StrAccount.badgeRecommended, days = 180),
      Option("y1", StrAccount.planYearly, 4000, badge = StrAccount.badgeBestValue, days = 365),
    )

  /** پلن‌های فعالِ سرور، وگرنه پیش‌فرض‌ها */
  fun options(dto: PlansDto?): List<Option> {
    val fresh = dto?.plans.orEmpty().filter { it.active && it.days > 0 }.map { p ->
      Option(
        code = p.code,
        title = p.title.ifBlank { p.code },
        price = p.price,
        fullPrice = if (p.fullPrice > 0) p.fullPrice else p.price,
        badge = p.badge,
        days = p.days,
        discountLabel = p.discount?.label.orEmpty(),
      )
    }
    return fresh.ifEmpty { fallback }
  }

  /** انتخابِ کاربر؛ اگر دیگر در فهرست نیست ⇒ نشان‌دار («پیشنهاد ما»)، وگرنه اولی */
  fun pick(options: List<Option>, chosen: String?): Option =
    options.firstOrNull { it.code == chosen } ?: options.firstOrNull { it.badge.isNotBlank() } ?: options.first()

  fun price(amount: Long, currency: String): String = "${money(amount.toDouble())} $currency"

  fun discountText(o: Option): String? = if (!o.discounted) null else o.discountLabel.ifBlank { StrAccount.percentOff(o.offPercent) }

  fun cta(o: Option, currency: String): String = StrAccount.buyCta(o.title, price(o.price, currency))

  /** کارتِ حالتِ بالای صفحه */
  data class StateCard(val tone: V3Tone, val title: String, val body: String)

  /** مجوزِ روی گوشی (اگر هست) مقدم است؛ بعد دورهٔ باز به حرفِ سرور؛ وگرنه دعوت */
  fun state(
    license: License.State?,
    licenseDays: Int,
    endsAt: Long,
    planTitle: String?,
    serverActive: Boolean,
    serverDays: Int,
    serverTrial: Boolean,
    trialDays: Int,
    warnDays: Int,
  ): StateCard = when {
    license != null && license != License.State.NONE -> {
      val expired = license == License.State.EXPIRED || license == License.State.GRACE
      val urgent = expired || (license == License.State.ACTIVE && licenseDays <= warnDays)
      val title = when (license) {
        License.State.ACTIVE -> if (urgent) StrAccount.endingTitle else StrAccount.activeTitle
        License.State.GRACE -> StrAccount.graceTitle
        License.State.EXPIRED -> StrAccount.bannerExpiredTitle
        License.State.PENDING -> StrAccount.pendingPlanTitle
        else -> StrAccount.invalidTitle
      }
      val big = when {
        expired -> StrAccount.renewNow
        license == License.State.ACTIVE && licenseDays > 0 -> StrAccount.daysLeft(licenseDays)
        license == License.State.ACTIVE -> StrAccount.lastDay
        else -> "—"
      }
      val lines = buildList {
        add(big)
        if (endsAt > 0) add(if (expired) StrAccount.endedOn(AccountFormat.date(endsAt)) else StrAccount.untilDate(AccountFormat.date(endsAt)))
        planTitle?.takeIf { it.isNotBlank() }?.let { add(StrAccount.planName(it)) }
      }
      StateCard(
        tone = when {
          urgent -> V3Tone.ERROR
          license == License.State.ACTIVE -> V3Tone.SUCCESS
          else -> V3Tone.WARNING
        },
        title = title,
        body = lines.joinToString(" · "),
      )
    }
    serverActive && serverDays > 0 -> StateCard(
      tone = if (serverDays <= warnDays) V3Tone.ERROR else V3Tone.SUCCESS,
      title = (if (serverTrial) StrAccount.trialTitle else StrAccount.activeTitle) + " — " + StrAccount.daysLeft(serverDays),
      body = if (serverTrial) StrAccount.trialBody else StrAccount.activeBody,
    )
    else -> {
      val n = if (trialDays > 0) trialDays else 7
      StateCard(V3Tone.INFO, StrAccount.trialInviteTitle(n), StrAccount.trialInviteBody(n))
    }
  }

  /** همان فهرستِ واقعیِ قابلیت‌ها: همیشه باز، و آن‌چه اشتراک باز می‌کند */
  val freeFeatures: List<String>
    get() = listOf(StrAccount.featStock, StrAccount.featExpenses, StrAccount.featPurchasing, StrAccount.featReports, StrAccount.featAudit, StrAccount.featBackup, StrAccount.featExcel)
  val paidFeatures: List<String>
    get() = listOf(StrAccount.featSale, StrAccount.featDebtors, StrAccount.featScanner, StrAccount.featMultiUser)

  /** کدِ شش‌رقمی فقط با حساب و شش رقم فعال می‌شود */
  fun canRedeem(code: String, signedIn: Boolean): Boolean = signedIn && code.length == 6 && code.all { it in '0'..'9' }

  /** پیامِ پس از ثبتِ کدِ آفلاین: «— دائمی» از ده سال به بالا، وگرنه روزها */
  fun offlineOk(daysLeft: Int?): String = StrAccount.offlineOk(
    when {
      daysLeft == null -> ""
      daysLeft > 3650 -> StrAccount.offlinePermanent
      else -> " — " + Str.days(daysLeft)
    },
  )
}
