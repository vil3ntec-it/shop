package ir.vil3ntec.tohid.core.i18n

/**
 *  هشدارِ «منقضی‌شده» جدا از «نزدیکِ انقضا» (`data/ExpiryAlerts`): ردیفِ «هشدارها» و یادآوریِ روزانه. «نزدیکِ انقضا» همان
 *  `StrHome.alertExpiring` است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrExpiryAlert {
  fun expired(n: Any): String = t("${asIs(n)} محموله منقضی شده", "$n ${pl(n, "batch", "batches")} expired", "$n لړۍ ختمې شوې")
  val expiredBody: String get() = t("جدا کنید و نفروشید", "Set them aside and don't sell them", "بېل یې کړئ او مه یې پلورئ")
  fun itemExpired(name: Any): String = t("«${asIs(name)}» منقضی شده", "«$name» has expired", "«$name» ختم شوی")
  fun itemNear(name: Any, days: Any): String = t(
    "«${asIs(name)}» ${asIs(days)} روز تا انقضا",
    "«$name» expires in $days ${pl(days, "day", "days")}",
    "«$name» تر ختمېدو $days ورځې",
  )
}
