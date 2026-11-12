package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.shopper.AddressMedia
import ir.vil3ntec.tohid.core.shopper.AddressMediaGate
import ir.vil3ntec.tohid.core.shopper.PlaceText
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlinx.coroutines.delay

/**
 *  CRM-09 — «به پیک کمک کنید (اختیاری)» در برگهٔ نشانیِ برنامهٔ خرید: عکسِ دروازه و یادداشتِ صوتیِ همان نشانیِ حساب، همان
 *  «نشانی‌های من» ِ وب (`store/addr-media.js`). فقط با پرچمِ دکانِ همین لحظه؛ بی حساب ⇒ «وارد شوید»؛ نشانیِ فقط‌دستگاهی ⇒
 *  «اول ذخیره». عکس از گزینشگرِ سکو (JPEG ِ آمادهٔ بی EXIF)، صدا فقط با زدنِ «ضبط» تا ۶۰ ثانیه؛ «ذخیره شد» فقط پس از پاسخِ
 *  سرور؛ میکروفون با بستنِ برگه بسته می‌شود (`closeAddressMedia`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddressMediaCard(model: CustomerModel, id: String?) {
  val gate = model.mediaGate(id)
  if (gate == AddressMediaGate.HIDDEN) return
  val box = model.account.places
  val platform = LocalPlatform.current
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(PlaceText.amNote, style = MaterialTheme.typography.bodyMedium)
      when (gate) {
        AddressMediaGate.SIGN_IN -> Note(PlaceText.amSignIn)
        AddressMediaGate.SAVE_FIRST -> Note(PlaceText.amSaveFirst)
        else -> {
          val a = model.addressOf(id) ?: return@Column
          val hasPhoto = a.media?.gatePhoto == true
          val hasVoice = a.media?.voiceNote == true
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PlaceText.amPhoto, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (hasPhoto) PlaceText.amSaved else PlaceText.amNone, style = MaterialTheme.typography.bodySmall)
          }
          box.photo?.let { platform.picture(it, Modifier.size(160.dp)) }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (platform.canPickPhoto) {
              OutlinedButton(onClick = { platform.pickPhoto { p -> model.sendGatePhoto(a.id, p) } }, enabled = !box.busy) {
                Text(if (hasPhoto) PlaceText.amChange else PlaceText.amAdd)
              }
            }
            if (hasPhoto) TextButton(onClick = { model.deleteAddressMedia(a.id, AddressMedia.PHOTO) }, enabled = !box.busy) { Text(PlaceText.amDelete) }
          }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PlaceText.amVoice, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
              when {
                box.recording -> ShopperText.num(box.elapsed / 1000) + " / " + ShopperText.num(AddressMedia.VOICE_MAX_MS / 1000)
                hasVoice -> PlaceText.amSaved
                else -> PlaceText.amNone
              },
              style = MaterialTheme.typography.bodySmall,
            )
          }
          if (box.recording) {
            LaunchedEffect(Unit) {
              while (true) {
                delay(250)
                model.tickAddressVoice()
              }
            }
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
              box.recording -> OutlinedButton(onClick = { model.stopAddressVoice() }) { Text(PlaceText.amStop) }
              box.take != null -> {
                OutlinedButton(onClick = { model.sendAddressVoice(a.id) }, enabled = !box.busy) { Text(PlaceText.amSend) }
                TextButton(onClick = { model.cancelAddressVoice() }, enabled = !box.busy) { Text(PlaceText.amCancel) }
              }
              model.canRecordAddressVoice() -> OutlinedButton(onClick = { model.startAddressVoice(a.id) }, enabled = !box.busy) {
                Text(if (hasVoice) PlaceText.amRerecord else PlaceText.amRecord)
              }
            }
            if (hasVoice && !box.recording && box.take == null) {
              TextButton(onClick = { model.deleteAddressMedia(a.id, AddressMedia.VOICE) }, enabled = !box.busy) { Text(PlaceText.amDelete) }
            }
          }
          box.mediaNote?.let { n ->
            Text(n, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
          }
        }
      }
    }
  }
}
