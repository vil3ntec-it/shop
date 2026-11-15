package ir.vil3ntec.tohid.ui.courier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.legal.LegalKind
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.onboarding.LegalPage

/**
 *  «حریمِ خصوصی» و «شرایط و ضوابط» در حالتِ پیک — همان صفحهٔ درونِ برنامهٔ دکان ([LegalPage]، منبعِ یکتای `core/legal`)،
 *  به‌عنوانِ زیرصفحهٔ برگهٔ «حساب» (مثلِ «نقدِ من»: «برگشت» و دکمهٔ برگشتِ گوشی به همان برگه). هیچ صفحهٔ بیرونی باز نمی‌شود.
 */
internal object CourierLegal {
  const val PRIVACY = "legal:privacy"
  const val TERMS = "legal:terms"

  fun isKey(key: String?): Boolean = key == PRIVACY || key == TERMS

  fun kindOf(key: String): LegalKind = if (key == TERMS) LegalKind.TERMS else LegalKind.PRIVACY
}

/** کارتِ دو ردیفِ برگهٔ «حساب» */
@Composable
internal fun CourierLegalCard(onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth().courierCard(p).padding(horizontal = 16.dp, vertical = 4.dp)) {
    AccountRow(Icons.Outlined.PrivacyTip, StrAccount.privacyPolicy, "", onClick = { onOpen(CourierLegal.PRIVACY) })
    AccountRow(Icons.Outlined.Description, StrAuth.termsShort, "", divider = false, onClick = { onOpen(CourierLegal.TERMS) })
  }
}

/** زیرصفحهٔ پیک: یکی از دو متنِ حقوقی، وگرنه «نقدِ من» */
@Composable
internal fun CourierSubPage(key: String, summary: CourierSummary?, staleError: String?, syncing: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
  if (CourierLegal.isKey(key)) LegalPage(CourierLegal.kindOf(key), onClose = onBack)
  else CourierCashPage(summary, staleError, syncing, onBack = onBack, onRefresh = onRefresh)
}
