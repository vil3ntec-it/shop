package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansActions
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansContent
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansModel
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansUi

/**
 *  «اشتراک و قیمت‌ها» برای گزارشِ کشیدن، با دادهٔ نمونهٔ عکس‌ها و دعوتِ ۱۴ روزه.
 */
internal object PlansDemo {
  @Composable
  fun Screen(chosen: String?) {
    val options = demoPlanOptions
    PlansContent(
      PlansUi(
        state = PlansModel.state(null, 0, 0, null, false, 0, false, 14, 7),
        options = options,
        picked = PlansModel.pick(options, chosen),
        currency = I18n.currencyWord(I18n.lang),
        signedIn = true,
        salesNumber = "+93700123456",
        storeBuild = false,
      ),
      PlansActions(back = {}, pick = {}, buy = {}, open = {}, whatsapp = {}),
    )
  }
}
