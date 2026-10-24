package ir.vil3ntec.tohid.ui.v3.account.subscription

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.PlansDto
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.SUBSCRIPTION_WARN_DAYS
import ir.vil3ntec.tohid.ui.screens.SalesContact
import ir.vil3ntec.tohid.ui.screens.SubscriptionPulse
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.DraftSelectCard
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
  val storeBuild: Boolean = BuildConfig.STORE == "bazaar",
)

internal class PlansActions(
  val back: () -> Unit,
  val pick: (String) -> Unit,
  val buy: () -> Unit,
  val open: (String) -> Unit,
  val whatsapp: () -> Unit,
)

/**
 *  «اشتراک و قیمت‌ها» — کارت‌های ساده، بی طلا و بی نشانِ VIP: کارتِ حالت، سه کارتِ
 *  انتخابیِ مدت (قیمت از سرور، تخفیف خط‌خورده، قیمتِ روزانه)، فهرستِ واقعیِ
 *  قابلیت‌ها، «کد اشتراک دارم» ⇐ برگهٔ کد، و دکمهٔ چسبانِ «گرفتن اشتراک …».
 */
@Composable
internal fun PlansContent(ui: PlansUi, actions: PlansActions) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger()
  V3Scaffold(
    title = Str.plans,
    onBack = actions.back,
    notice = null,
    bottomBar = if (ui.storeBuild) null else ({
      V3FormBar(primary = PlansModel.cta(ui.picked, ui.currency), onPrimary = actions.buy, summary = StrAccount.buyFootnote)
    }),
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
        Spacer(Modifier.height(8.dp))
        V3Message(ui.state.tone, ui.state.title, body = ui.state.body)

        Text(StrAccount.chooseDuration, style = V3Type.headline, color = c.ink, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ui.options.forEachIndexed { i, o ->
            DraftSelectCard(
              selected = o.code == ui.picked.code,
              label = o.title,
              onClick = { actions.pick(o.code) },
              badge = o.badge.ifBlank { null },
              modifier = Modifier.weight(1f).fillMaxHeight().v3Stagger(stagger, o.code, i),
            ) { OptionBody(o, ui.currency) }
          }
        }

        if (!ui.signedIn) {
          Spacer(Modifier.height(14.dp))
          V3Message(V3Tone.WARNING, StrAccount.buySignInTitle, body = StrAccount.buySignInBody, action = Str.signIn, onAction = { actions.open(AccountLinks.SIGN_IN) })
        }
        if (ui.storeBuild) {
          Spacer(Modifier.height(14.dp))
          Text(StrAccount.bazaarNote, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Features(StrAccount.freeAlways, PlansModel.freeFeatures, Icons.Outlined.Check)
        Features(StrAccount.withSubscription, PlansModel.paidFeatures, Icons.Outlined.LockOpen)

        V3Section(null) {
          V3ListRow(StrAccount.haveCode, subtitle = StrAccount.sixDigitHint, icon = Icons.Outlined.Redeem, hue = V3Hue.BRAND, onClick = { actions.open(AccountPaths.REDEEM) })
          V3Divider(start = 64.dp)
          V3ListRow(StrAccount.noOnlinePay, icon = Icons.Outlined.CreditCardOff, chevron = false)
        }
        if (!ui.storeBuild) {
          V3Section(StrAccount.contactWays) {
            V3ListRow(
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

@Composable
private fun OptionBody(o: PlansModel.Option, currency: String) {
  val c = PhoneV3.colors
  Text(o.title, style = V3Type.headline, color = c.ink)
  Spacer(Modifier.height(6.dp))
  if (o.discounted) {
    Text(money(o.fullPrice.toDouble()), style = V3Type.footnote.copy(textDecoration = TextDecoration.LineThrough), color = c.inkMuted)
  }
  Text(money(o.price.toDouble()), style = V3Type.figure, color = c.ink)
  Text(currency, style = V3Type.caption, color = c.inkMuted)
  PlansModel.discountText(o)?.let { Text(it, style = V3Type.caption, color = PhoneV3Roles.brandText(c)) }
  Spacer(Modifier.height(4.dp))
  Text(StrAccount.perDay(money(o.perDay)), style = V3Type.caption, color = c.inkMuted)
}

@Composable
private fun Features(title: String, items: List<String>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
  val c = PhoneV3.colors
  V3Section(title) {
    items.forEachIndexed { i, f ->
      if (i > 0) V3Divider(start = 52.dp)
      V3ListRow(
        f,
        minHeight = 46.dp,
        chevron = false,
        leading = { Icon(icon, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) },
      )
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
  val currency = dto?.currency?.takeIf { it.isNotBlank() && I18n.lang == Lang.FA } ?: I18n.currencyWord(I18n.lang)
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
      back = { back() },
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
