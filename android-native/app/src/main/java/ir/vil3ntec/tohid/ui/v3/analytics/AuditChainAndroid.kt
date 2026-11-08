package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.AuditSeal
import ir.vil3ntec.tohid.data.AuditSealAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  STF-09 — مرزِ سکوی صفحهٔ «سابقهٔ عملیات»: شناسهٔ همین دستگاه، نامِ کاربرانی که می‌شناسد و سنجشِ زنجیره با
 *  لنگرِ همین دستگاه (`AuditSeal.verify`، بیرون از رشتهٔ اصلی؛ تا پایانِ سنجش [AuditChainView.report] = `null`).
 */
class AuditChainView(val thisDevice: String, val names: Map<String, String>, val report: AuditChain.Report?)

@Composable
fun rememberAuditChain(log: List<AuditEntry>): AuditChainView {
  val context = LocalContext.current
  val thisDevice = remember { AuditSealAndroid.deviceId(context) }
  val names = remember { AuditSealAndroid.names(context) }
  val report by produceState<AuditChain.Report?>(null, log) {
    value = withContext(Dispatchers.Default) { runCatching { AuditSeal.verify(log, AuditSealAndroid.anchors(context)) }.getOrNull() }
  }
  return AuditChainView(thisDevice, names, report)
}
