package android.hardware.usb

/** shimِ iOS ِ USB ِ میزبانِ اندروید — فقط نوع؛ آیفون چاپگرِ USB نمی‌گیرد، پس `UsbManager` هرگز ساخته نمی‌شود */
object UsbConstants {
  const val USB_ENDPOINT_XFER_BULK = 2
  const val USB_DIR_OUT = 0
  const val USB_DIR_IN = 0x80
  const val USB_CLASS_PRINTER = 7
}

class UsbEndpoint internal constructor() {
  val type: Int get() = 0
  val direction: Int get() = 0
  val address: Int get() = 0
}

class UsbInterface internal constructor() {
  val interfaceClass: Int get() = 0
  val endpointCount: Int get() = 0
  fun getEndpoint(index: Int): UsbEndpoint = UsbEndpoint()
}

class UsbDevice internal constructor() {
  val deviceName: String get() = ""
  val productName: String? get() = null
  val manufacturerName: String? get() = null
  val vendorId: Int get() = 0
  val productId: Int get() = 0
  val deviceClass: Int get() = 0
  val interfaceCount: Int get() = 0
  fun getInterface(index: Int): UsbInterface = UsbInterface()
}

class UsbDeviceConnection internal constructor() {
  fun claimInterface(intf: UsbInterface, force: Boolean): Boolean = false
  fun releaseInterface(intf: UsbInterface): Boolean = false
  fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int = -1
  fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, offset: Int, length: Int, timeout: Int): Int = -1
  fun close() {}
}

class UsbManager private constructor() {
  val deviceList: HashMap<String, UsbDevice> get() = HashMap()
  fun hasPermission(device: UsbDevice): Boolean = false
  fun requestPermission(device: UsbDevice, pi: android.app.PendingIntent) {}
  fun openDevice(device: UsbDevice): UsbDeviceConnection? = null

  companion object {
    const val ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
    const val EXTRA_PERMISSION_GRANTED = "permission"
  }
}
