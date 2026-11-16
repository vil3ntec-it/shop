package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.runtime.staticCompositionLocalOf
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrEvidence
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.staff.DeviceBuilds
import ir.vil3ntec.tohid.ui.theme.V3Status

/**
 *  نسخهٔ برنامهٔ هر دستگاهِ دکان در فهرستِ «دستگاه‌ها» (صفحهٔ کارمندان و صفحهٔ هر کارمند) و در هشدارِ کلیدِ «خرید به ارزِ دیگر».
 *  قاعده همه در `core/staff/DeviceBuilds` است؛ این‌جا فقط `ShopDeviceDto` ⇒ ردیفِ آن و نوشته‌ها. [self] = ساختِ همین برنامه
 *  (`AppConfig.appVersion`، گوشی `3.2.N`، کامپیوتر `1.0.N`). آزمون: `DeviceVersionModelTest`.
 */
object DeviceVersionModel {

  /** ساختِ همین برنامه برای صفحه‌ها (پیش‌فرض `AppConfig.appVersion`)؛ پیش‌نمایش و آزمون ساختِ گوشی را می‌گذارند */
  val LocalSelf = staticCompositionLocalOf { AppConfig.appVersion }

  /** فعال = نشسته و از کار نیفتاده (همان `MemberModel.DeviceState.ACTIVE`/`CURRENT`) */
  fun device(d: ShopDeviceDto): DeviceBuilds.Device =
    DeviceBuilds.Device(d.id, d.appVersion, current = d.current, active = d.status == "active" && d.disabledAt == null)

  fun states(all: List<ShopDeviceDto>?, self: String = AppConfig.appVersion): Map<String, DeviceBuilds.State> =
    DeviceBuilds.states(all.orEmpty().map(::device), self)

  /** فهرست نرسید ⇒ null ⇒ `Gate.NoList` */
  fun gate(all: List<ShopDeviceDto>?, self: String = AppConfig.appVersion): DeviceBuilds.Gate =
    DeviceBuilds.gate(all?.map(::device), self)

  /** نسخه‌ای که نوشته می‌شود: همین دستگاه ⇒ ساختِ همین برنامه؛ «نامعلوم» ⇒ هیچ (قرصِ «نسخهٔ نامعلوم» می‌گوید) */
  fun versionOf(d: ShopDeviceDto, state: DeviceBuilds.State?, self: String = AppConfig.appVersion): String? {
    if (state == DeviceBuilds.State.UNKNOWN) return null
    return (if (d.current) self else d.appVersion)?.trim()?.takeIf { it.isNotEmpty() }
  }

  /** خطِ زیرِ نام: «نسخهٔ ۳٫۲… · آخرین بار: …» (هر کدام که هست) */
  fun subtitle(d: ShopDeviceDto, state: DeviceBuilds.State?, lastSeen: String?, self: String = AppConfig.appVersion): String? =
    listOfNotNull(versionOf(d, state, self)?.let { Str.version(it) }, lastSeen).joinToString(" · ").ifEmpty { null }

  /** قرصِ نسخه فقط برای «کهنه» و «نامعلوم»؛ به‌روز و سنجیده‌نشده (خطِ دیگر) هیچ */
  fun pill(state: DeviceBuilds.State?): Pair<V3Status, String>? = when (state) {
    DeviceBuilds.State.OLD -> V3Status.WARNING to StrAccount.versionOld
    DeviceBuilds.State.UNKNOWN -> V3Status.INACTIVE to StrAccount.versionUnknown
    else -> null
  }

  /** یک ردیفِ هشدارِ کلید: «نامِ دستگاه · کارمند»، نسخه (اگر سنجیدنی است) و برچسبِ وضعیت */
  data class GateRow(val id: String, val title: String, val version: String?, val label: String, val state: DeviceBuilds.State)

  /** ردیف‌های [DeviceBuilds.Gate.Confirm] با نام و کارمندِ همان دستگاه از فهرستِ رسیده */
  fun gateRows(gate: DeviceBuilds.Gate.Confirm, all: List<ShopDeviceDto>, self: String = AppConfig.appVersion): List<GateRow> {
    val byId = all.associateBy { it.id }
    return gate.devices.mapNotNull { (dev, state) ->
      val d = byId[dev.id] ?: return@mapNotNull null
      GateRow(
        id = d.id,
        title = StrEvidence.step(MemberModel.deviceName(d), d.member.name.ifBlank { StrAccount.staffMember }),
        version = versionOf(d, state, self)?.let { Str.version(it) },
        label = label(state),
        state = state,
      )
    }
  }

  /** برچسبِ ردیفِ هشدارِ کلید: کهنه، نامعلوم یا «سنجیده نشد» (خطِ دیگر) */
  fun label(state: DeviceBuilds.State): String = when (state) {
    DeviceBuilds.State.OLD -> StrAccount.versionOld
    DeviceBuilds.State.UNKNOWN -> StrAccount.versionUnknown
    DeviceBuilds.State.UNCHECKED -> StrPurchaseFx.notChecked
    DeviceBuilds.State.CURRENT -> ""
  }
}
