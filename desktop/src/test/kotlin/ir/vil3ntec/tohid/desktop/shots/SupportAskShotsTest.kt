package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.account.subscription.LockedFeature
import ir.vil3ntec.tohid.ui.v3.account.support.SupportAskModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportContent
import ir.vil3ntec.tohid.ui.v3.account.support.SupportModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportUi
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderSetupModel
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «از پشتیبانی بخواه» برای صاحب‌کار: صفِ سفارش با «سفارشِ آنلاین خاموش است» و دکمه، قفلِ «جای دستگاه نیست» با دکمه، و
 *  گفت‌وگوی پشتیبانی پس از درخواست (نوارِ «رسید» + پیامِ درخواست + پاسخِ خودکارِ «روشن کرد») — گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲، روشن و تیره.
 *  فقط با `SUPPORT_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست؛ بی آن کنار می‌رود).
 */
@OptIn(ExperimentalTestApi::class)
class SupportAskShotsTest {

  private val out: File? = System.getenv("SUPPORT_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(2f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
            LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false,
          ) { Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) { content() } }
        }
      }
    }
    repeat(24) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}-fa.png"))
  }

  private fun both(name: String, content: @Composable () -> Unit) {
    shot(name, false, content)
    shot(name, true, content)
  }

  @Test fun shots() {
    Assume.assumeTrue("فقط با SUPPORT_SHOTS", out != null)
    val now = System.currentTimeMillis()
    both("phone-orders-off-ask") {
      OrdersScreen(
        state = QueueState(tab = "new", counts = mapOf("new" to 0), items = emptyList(), loading = false, perms = OrderPermsDto(manage = true, create = true), now = now),
        actions = QueueActions(onTab = {}, onOpen = {}, onStep = { _, _ -> }, onNew = {}, onRetry = {}, onMore = {}),
        onBack = {}, setup = OrderSetupModel.Status.Off("orders_off"), onSetup = {}, onSupport = {},
      )
    }
    both("phone-device-limit-ask") { LockedFeature("فروش", signedIn = true, serverDenied = "device_limit") }
    val msgs = listOf(
      SupportMessageDto(id = "1", sender = "user", body = "لطفاً سفارشِ آنلاین را برای دکانِ من روشن کنید.", createdAt = now - 120_000),
      SupportMessageDto(id = "2", sender = "admin", senderName = "پشتیبانی", body = "پشتیبانی سفارشِ آنلاین را برای دکانِ شما روشن کرد.", createdAt = now - 30_000),
    )
    both("phone-support-request-sent") {
      SupportContent(
        SupportUi(SupportModel.items(msgs.take(1), now), ask = SupportAskModel.ui("flag_off:shop.orders", SupportAskModel.State.SENT)),
        onBack = {}, onDraft = {}, onSend = {},
      )
    }
    both("phone-support-request-fixed") {
      SupportContent(SupportUi(SupportModel.items(msgs, now)), onBack = {}, onDraft = {}, onSend = {})
    }
  }
}
