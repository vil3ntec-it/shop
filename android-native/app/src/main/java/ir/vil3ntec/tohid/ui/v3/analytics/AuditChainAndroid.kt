package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.audit.AuditAnchor
import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.AuditSeal
import ir.vil3ntec.tohid.data.AuditSealAndroid
import ir.vil3ntec.tohid.sync.platform.AuditAnchorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  STF-09 — مرزِ سکوی صفحهٔ «سابقهٔ عملیات»: شناسهٔ همین دستگاه، نامِ کاربرانی که می‌شناسد، سنجشِ زنجیره با
 *  لنگرِ همین دستگاه (`AuditSeal.verify`، بیرون از رشتهٔ اصلی؛ تا پایانِ سنجش [AuditChainView.report] = `null`) و
 *  حکمِ سرور روی رونوشت و لنگرهای همهٔ دستگاه‌ها ([AuditChainView.server]، `AuditAnchorSync.verdict`؛ پرچمِ خاموش،
 *  بی‌اینترنت یا بی اجازه ⇐ `null` و همان سنجشِ محلی).
 */
class AuditChainView(val thisDevice: String, val names: Map<String, String>, val report: AuditChain.Report?, val server: AuditAnchor.Verdict? = null)

@Composable
fun rememberAuditChain(log: List<AuditEntry>): AuditChainView {
  val context = LocalContext.current
  val thisDevice = remember { AuditSealAndroid.deviceId(context) }
  val names = remember { AuditSealAndroid.names(context) }
  val report by produceState<AuditChain.Report?>(null, log) {
    value = withContext(Dispatchers.Default) { runCatching { AuditSeal.verify(log, AuditSealAndroid.anchors(context)) }.getOrNull() }
  }
  //  یک بار برای هر بار باز شدنِ صفحه (نه هر تغییرِ دفتر): سرور همهٔ رونوشت را می‌سنجد
  val server by produceState<AuditAnchor.Verdict?>(null) { value = AuditAnchorSync.verdict(context) }
  return AuditChainView(thisDevice, names, report, server)
}
