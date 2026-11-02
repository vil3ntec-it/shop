package ir.vil3ntec.tohid.hw

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.hw.DeviceSignal
import ir.vil3ntec.tohid.core.hw.DeviceTransport
import ir.vil3ntec.tohid.print.ThermalPrinter
import kotlinx.coroutines.delay

/**
 *  «دستگاه‌ها» — مرزِ اندروید: نشانه‌های هر دستگاهِ بلوتوث (نام، ردهٔ دستگاه، نوعِ وصل، سرویس‌های شناخته) برای
 *  `DeviceClassifier`. جفت‌شده‌ها از `bondedDevices`؛ «جست‌وجو» همان جست‌وجوی دوازده‌ثانیه‌ایِ اندروید (کلاسیک و BLE
 *  در یک‌جا). اجازه‌ها همان «دستگاه‌های نزدیک» ِ HW-12 (`PrinterFinderPlatform`). کامپیوتر جایگزینِ خودش را دارد:
 *  ‎`desktop/…/hw/DeviceSignalsDesk.kt‎` (درگاه‌های COM).
 */
object DeviceSignals {
  const val SEARCH_MS = 12_000L

  /** این دستگاه جست‌وجوی بلوتوث دارد (کامپیوتر: نه، فقط فهرستِ درگاه‌ها) */
  fun search(): Boolean = true

  fun permissions(): List<String> = PrinterFinderPlatform.bluetoothPermissions()

  fun granted(context: Context): Boolean = PrinterFinderPlatform.bluetoothGranted(context)

  @SuppressLint("MissingPermission")
  private fun signal(d: BluetoothDevice, paired: Boolean): DeviceSignal = DeviceSignal(
    link = "bt:" + d.address,
    name = runCatching { d.name }.getOrNull().orEmpty().ifBlank { d.address },
    btClass = runCatching { d.bluetoothClass?.deviceClass }.getOrNull(),
    services = runCatching { d.uuids?.map { it.uuid.toString() }?.toSet() }.getOrNull().orEmpty(),
    transport = when (runCatching { d.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)) {
      BluetoothDevice.DEVICE_TYPE_LE -> DeviceTransport.BLE
      BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceTransport.DUAL
      else -> DeviceTransport.CLASSIC
    },
    paired = paired,
  )

  /** دستگاه‌های جفت‌شده در تنظیماتِ بلوتوثِ گوشی */
  @SuppressLint("MissingPermission")
  fun paired(context: Context): List<DeviceSignal> {
    val adapter = ThermalPrinter.adapter(context) ?: return emptyList()
    return try {
      adapter.bondedDevices.orEmpty().map { signal(it, true) }
    } catch (e: SecurityException) {
      emptyList()
    }
  }

  /** جست‌وجوی نزدیک تا [ms]؛ هر یافته با [onFound]. نتیجه: پیامِ خطا یا `null` */
  @SuppressLint("MissingPermission")
  suspend fun nearby(context: Context, ms: Long, onFound: (DeviceSignal) -> Unit): String? {
    val adapter = ThermalPrinter.adapter(context) ?: return "این دستگاه بلوتوث ندارد"
    if (!granted(context)) return DeviceLinks.NO_PERMISSION
    if (!adapter.isEnabled) return "بلوتوث خاموش است — روشنش کنید و دوباره بزنید"
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_FOUND) return
        val device = deviceOf(intent) ?: return
        runCatching { onFound(signal(device, device.bondState == BluetoothDevice.BOND_BONDED)) }
      }
    }
    return try {
      ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_NOT_EXPORTED)
      if (!adapter.startDiscovery()) {
        if (Build.VERSION.SDK_INT < 31) "جست‌وجوی بلوتوث آغاز نشد — مکان‌یابِ (GPS) دستگاه را روشن کنید" else "جست‌وجوی بلوتوث آغاز نشد"
      } else {
        delay(ms)
        null
      }
    } catch (e: SecurityException) {
      DeviceLinks.NO_PERMISSION
    } finally {
      runCatching { adapter.cancelDiscovery() }
      runCatching { context.unregisterReceiver(receiver) }
    }
  }

  @Suppress("DEPRECATION")
  private fun deviceOf(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

  /** جفت کردن (پنجرهٔ خودِ اندروید) */
  fun bond(context: Context, address: String): Boolean = PrinterFinderPlatform.bond(context, address)
}
