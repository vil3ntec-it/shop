package ir.vil3ntec.tohid.ui.v3.account.subscription

import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3NavRow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.core.platform.StoreChannel
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.SUBSCRIPTION_WARN_DAYS
import ir.vil3ntec.tohid.ui.screens.SalesContact
import ir.vil3ntec.tohid.ui.screens.SubscriptionPulse
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.launch

/** آن‌چه «اشتراک و قیمت‌ها» نشان می‌دهد */
internal data class PlansUi(
  val state: PlansModel.StateCard,
  val options: List<PlansModel.Option>,
  val picked: PlansModel.Option,
  val currency: String,
  val signedIn: Boolean,
  val salesNumber: String,
  /** نسخهٔ کافه‌بازار: پرداختِ بیرون از بازار ممنوع ⇒ نه خرید با واتساپ */
  val storeBuild: Boolean = StoreChannel.storeManaged(BuildConfig.STORE),
)

internal class PlansActions(
  val back: (() -> Unit)?,
  val pick: (String) -> Unit,
  val buy: () -> Unit,
  val open: (String) -> Unit,
  val whatsapp: () -> Unit,
)

/**
 *  «اشتراک و قیمت‌ها»: کارتِ حالت، ستونِ کارت‌های بازشوندهٔ پلن (`PlanCard`: قیمتِ درشت از سرور، روزی چند، صرفه‌جویی
 *  از همان قیمت‌ها؛ زدن = انتخاب و باز شدنِ کشوی جزئیات، هر بار یکی)، «کد اشتراک دارم» ⇐ برگهٔ کد، نبودِ پرداختِ
 *  آنلاین، واتساپ، و نوارِ چسبانِ «گرفتن اشتراک …» که نوشته‌اش با انتخاب نرم عوض می‌شود (`PlansBuyBar`).
 *  فهرستِ «همیشه رایگان» و «با اشتراک باز می‌شود» درونِ کشوی هر پلن به تراشه رفت، نه دو فهرستِ بلندِ صفحه.
 */
@Composable
internal fun PlansContent(ui: PlansUi, actions: PlansActions) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger()
  //  کارتِ باز (UI، نه داده): دست‌نخورده ⇒ همان پلنِ انتخاب‌شده؛ null ⇒ همه بسته
  var openState by rememberSaveable { mutableStateOf<String?>(PlansModel.UNTOUCHED) }
  val open = PlansModel.openCard(ui.options, ui.picked, openState)
  val savings = remember(ui.options) { ui.options.associate { it.code to PlansModel.saving(it, ui.options) } }
  V3Scaffold(
    title = Str.plans,
    onBack = actions.back,
    notice = null,
    bottomBar = if (ui.storeBuild) null else ({
      PlansBuyBar(PlansModel.cta(ui.picked, ui.currency), ui.picked.price, onBuy = actions.buy, summary = StrAccount.buyFootnote)
    }),
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
        Spacer(Modifier.height(8.dp))
        V3Message(ui.state.tone, ui.state.title, body = ui.state.body)

        Text(
          StrAccount.chooseDuration,
          style = V3Type.headline,
          color = c.ink,
          modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp).semantics { heading() },
        )
        ui.options.forEachIndexed { i, o ->
          key(o.code) {
            PlanCard(
              option = o,
              saving = savings[o.code],
              currency = ui.currency,
              selected = o.code == ui.picked.code,
              open = o.code == open,
              onTap = {
                actions.pick(o.code)
                openState = PlansModel.toggle(o.code, open)
              },
              modifier = Modifier.padding(bottom = 12.dp).v3Stagger(stagger, o.code, i),
            )
          }
        }

        if (!ui.signedIn) {
          Spacer(Modifier.height(2.dp))
          V3Message(V3Tone.WARNING, StrAccount.buySignInTitle, body = StrAccount.buySignInBody, action = Str.signIn, onAction = { actions.open(AccountLinks.SIGN_IN) })
        }
        if (ui.storeBuild) {
          Spacer(Modifier.height(2.dp))
          Text(StrAccount.bazaarNote, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        V3Section(null, Modifier.v3Stagger(stagger, "redeem", ui.options.size)) {
          //  ۶۵r: همان ردیفِ «بیشتر» (`V3NavRow`)؛ رنگ‌ها همان رنگِ «اشتراکِ من» در «بیشتر»
          V3NavRow(StrAccount.haveCode, icon = Icons.Outlined.Redeem, hue = V3Hue.BRAND, help = StrAccount.sixDigitHint, onClick = { actions.open(AccountPaths.REDEEM) })
          V3Divider(start = V3NavRowSpec.DIVIDER_START)
          V3NavRow(StrAccount.noOnlinePay, icon = Icons.Outlined.CreditCardOff, hue = ir.vil3ntec.tohid.ui.v3.nav.V3Modules.hueOf("myplan"), chevron = false)
        }
        if (!ui.storeBuild) {
          V3Section(StrAccount.contactWays, Modifier.v3Stagger(stagger, "contact", ui.options.size + 1)) {
            V3NavRow(
              StrAccount.whatsapp,
              icon = Icons.AutoMirrored.Outlined.Chat,
              hue = V3Hue.TEAL,
              trailing = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                  Text(SalesContact.display(ui.salesNumber), style = V3Type.subhead, color = c.inkMuted)
                }
              },
              onClick = actions.whatsapp,
            )
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

//  پیام به فروشنده (دادهٔ گفت‌وگو، نه نوشتهٔ صفحه) — همان متنِ پیشین
private const val BUY_MESSAGE = "سلام، می‌خواهم اشتراک برنامهٔ فروشگاه من را بخرم."

/**
 *  مسیرِ `subscription/plans`: قیمت‌ها از سرور، خرید = ثبتِ درخواست (اگر وارد شده،
 *  بی‌صدا با **کدِ** پلن) **و** باز شدنِ واتساپ با شمارهٔ فروشِ سرور (F0-24).
 */
@Composable
internal fun RouteScope.PlansRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val signedIn = remember(nav.current) { !SyncStore(context).accessToken.isNullOrBlank() }
  var dto by remember { mutableStateOf<PlansDto?>(null) }
  var chosen by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) { Backend.account(context).plans().onSuccess { dto = it } }
  LaunchedEffect(Unit) { SubscriptionPulse.refresh(context) }
  val status = remember { runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull() }
  val options = PlansModel.options(dto)
  val picked = PlansModel.pick(options, chosen)
  val currency = dto?.currency?.takeIf { it.isNotBlank() && I18n.lang.persian } ?: I18n.currencyWord(I18n.lang)
  val salesNumber = dto?.whatsapp?.number.orEmpty()

  fun whatsapp(text: String?) {
    val tail = text?.let { "?text=" + Uri.encode(it) }.orEmpty()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${SalesContact.waDigits(salesNumber.ifBlank { null })}$tail"))) }
  }

  PlansContent(
    PlansUi(
      state = PlansModel.state(
        license = status?.state, licenseDays = status?.daysLeft() ?: 0,
        endsAt = status?.payload?.subscriptionEndsAt ?: 0L, planTitle = status?.payload?.planTitle,
        serverActive = SubscriptionPulse.active, serverDays = SubscriptionPulse.days, serverTrial = SubscriptionPulse.trial,
        trialDays = dto?.trialDays ?: 0, warnDays = SUBSCRIPTION_WARN_DAYS,
      ),
      options = options, picked = picked, currency = currency, signedIn = signedIn, salesNumber = salesNumber,
    ),
    PlansActions(
      back = backOrNull,
      pick = { chosen = it },
      buy = {
        //  ثبتِ درخواست بی‌صداست: اگر نگرفت، واتساپ همچنان باز می‌شود
        if (signedIn) scope.launch { Backend.account(context).requestPurchase(picked.code, "از برنامهٔ اندروید — ${picked.title}") }
        whatsapp("$BUY_MESSAGE (${picked.title})")
      },
      open = { nav.open(it) },
      whatsapp = { whatsapp(null) },
    ),
  )
}
