package ir.vil3ntec.tohid.ui.v3.account.support

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.i18n.StrSupportAsk
import ir.vil3ntec.tohid.core.i18n.t
import ir.vil3ntec.tohid.core.model.SupportAskDto
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.core.support.SupportProblem
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav

/**
 *  «از پشتیبانی بخواه» (آزمون: `SupportAskModelTest`، `SupportAskUiTest`): هر حالتِ بسته‌ای که کارش با پشتیبانی است (پرچمِ خاموش،
 *  قابلیتِ بیرونِ اشتراک، پایانِ اشتراک، سقفِ دستگاه، همگام‌سازی، کدِ ایمیل) یک دکمه دارد که گفت‌وگوی **همان** پشتیبانی را باز می‌کند و
 *  درخواستِ ساخت‌یافته (کد + واقعیت‌های مجاز) را با همان یک لمس می‌فرستد — بی چتِ دوم، بی نوشتن. لمسِ دوباره درخواستِ دوم نمی‌سازد
 *  (سرور: یکتا تا باز است؛ برنامه: `asked` ِ ماندگارِ صفحه).
 *
 *  نشانی: `support?ask=<کد>&f.<واقعیت>=<مقدار>[&contact=<ایمیلِ مهمان>]` — کد فقط از `SupportProblem`، واقعیت‌ها فقط مجازهای همان
 *  مشکل (بقیه همین‌جا می‌افتند). ⛔ کد و نامِ پرچم روی صفحه نمی‌آیند؛ نسخه و سکو هنگامِ فرستادن افزوده می‌شوند.
 */
object SupportAskModel {

  enum class State { SENDING, SENT, ALREADY, COLLEAGUE, FAILED }

  /** درخواستِ آمده از نشانی */
  data class Pending(val code: String, val facts: Map<String, Any>, val contact: String)

  /** نوارِ بالای گفت‌وگو */
  data class Ui(val code: String, val title: String, val state: State) {
    val body: String get() = when (state) {
      State.SENDING -> StrSupportAsk.sending
      State.SENT -> StrSupportAsk.sent
      State.ALREADY -> StrSupportAsk.already
      State.COLLEAGUE -> StrSupportAsk.colleague
      State.FAILED -> StrSupportAsk.failed
    }
  }

  const val ASK = "ask"
  const val CONTACT = "contact"
  private const val FACT = "f."

  /** نشانیِ گفت‌وگو با درخواستِ آماده؛ کدِ ناشناخته ⇒ IllegalArgumentException (خطای برنامه‌نویس) */
  fun path(code: String, facts: Map<String, Any> = emptyMap(), contact: String? = null): String {
    val (def, _) = requireNotNull(SupportProblem.parse(code)) { "مشکلِ ناشناخته: $code" }
    val clean = SupportProblem.facts(def, facts)
    val args = buildList {
      add(ASK to code)
      clean.forEach { (k, v) -> add("$FACT$k" to v.toString()) }
      contact?.trim()?.takeIf { it.isNotEmpty() && def.code == SupportProblem.EMAIL_CODE }?.let { add(CONTACT to it) }
    }
    return RoutePaths.withQuery(AccountPaths.SUPPORT, *args.toTypedArray())
  }

  /** پرسشِ نشانی ⇒ درخواست (یا null)؛ شمارها دوباره عدد می‌شوند، هر چیزِ نامجاز می‌افتد */
  fun pendingOf(query: (String) -> String?): Pending? {
    val code = query(ASK)?.trim().orEmpty()
    val (def, _) = SupportProblem.parse(code) ?: return null
    val raw = LinkedHashMap<String, Any?>()
    for (k in def.facts) {
      val v = query("$FACT$k") ?: continue
      raw[k] = if (SupportProblem.FACTS[k] == "count") v.toLongOrNull() else v
    }
    val contact = if (def.code == SupportProblem.EMAIL_CODE) query(CONTACT)?.trim().orEmpty() else ""
    return Pending(code, SupportProblem.facts(def, raw), contact)
  }

  /** واقعیت‌های فرستادنی: آمده از نشانی + نسخه و سکوی همین برنامه (هر دو از فهرستِ مجاز می‌گذرند) */
  fun factsToSend(p: Pending, appVersion: String, platform: String): Map<String, Any> {
    val def = SupportProblem.parse(p.code)?.first ?: return emptyMap()
    return SupportProblem.facts(def, p.facts + mapOf("appVersion" to appVersion, "platform" to platform))
  }

  fun stateOf(dto: SupportAskDto): State = when {
    dto.created -> State.SENT
    dto.sameShop -> State.COLLEAGUE
    else -> State.ALREADY
  }

  /** سرخطِ خواندنیِ همان مشکل به زبانِ برنامه (کدِ ناشناخته ⇒ «کمک از پشتیبانی»)؛ کد هرگز */
  fun title(code: String): String = SupportProblem.title(code) { t(it.fa, it.en, it.ps) }

  fun ui(code: String, state: State) = Ui(code, title(code), state)

  /** سکوی همین ساخت — `ui/Platform` ِ هر سکو (کامپیوتر/گوشی) */
  fun platform(): String = if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) "desktop" else "android"
}

/** «از پشتیبانی بخواه» — یک لمس، همان گفت‌وگوی پشتیبانی با درخواستِ ساخت‌یافته (`onOpen` برای آزمون و برگه‌هایی که خودشان می‌برند) */
@Composable
internal fun AskSupportButton(
  code: String,
  modifier: Modifier = Modifier,
  facts: Map<String, Any> = emptyMap(),
  contact: String? = null,
  kind: V3ButtonKind = V3ButtonKind.SECONDARY,
  onOpen: ((String) -> Unit)? = null,
) {
  val nav = LocalV3Nav.current
  val path = SupportAskModel.path(code, facts, contact)
  V3Button(
    StrSupportAsk.button,
    onClick = { onOpen?.invoke(path) ?: nav?.open(path) ?: AppNav.go(path) },
    modifier = modifier.fillMaxWidth(),
    kind = kind,
    icon = Icons.Outlined.SupportAgent,
  )
}

/** نشانیِ گفت‌وگو با درخواستِ آماده — برای جاهایی که دکمهٔ خودشان را دارند (برگهٔ بخشِ خاموش، کارتِ «چرا سفارش نمی‌رسد») */
fun AccountPaths.supportAsk(code: String, facts: Map<String, Any> = emptyMap(), contact: String? = null): String =
  SupportAskModel.path(code, facts, contact)
