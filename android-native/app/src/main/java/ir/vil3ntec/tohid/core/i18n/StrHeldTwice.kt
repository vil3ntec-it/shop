package ir.vil3ntec.tohid.core.i18n

/**
 *  S09 — پیامِ «یک فاکتور در دو دستگاه» وقتی لغوِ خودکارِ فروشِ دوم روشن است (`data/HeldTwice`؛ پرچمِ خاموشِ
 *  `shop.held_twice_cancel` ⇒ همان پیامِ پیشینِ `StrPos.heldTwice`).
 */
@Suppress("unused")
object StrHeldTwice {
  fun cancelled(name: Any, device: Any): String = t(
    "فاکتورِ «${asIs(name)}» در دستگاهِ ${asIs(device)} هم فروخته شده بود؛ فروشِ دومِ همین دستگاه خودکار لغو شد و صاحبِ دکان آن را در «سندهای ردشده» می‌بیند.",
    "Invoice “$name” had also been sold on device $device; this device's second sale was cancelled automatically and the shop owner sees it under rejected documents.",
    "«$name» بل په $device وسیله کې هم پلورل شوی و؛ د همدې وسیلې دویم پلور پخپله لغوه شو او د دوکان خاوند یې په «رد شوي سندونه» کې ویني.",
  )
  fun reported(name: Any, device: Any): String = t(
    "فاکتورِ «${asIs(name)}» هم این‌جا و هم در دستگاهِ ${asIs(device)} فروخته شد. صاحبِ دکان خبر شد تا یکی را لغو کند.",
    "Invoice “$name” was sold both here and on device $device. The shop owner was told so one of them can be cancelled.",
    "«$name» بل هم دلته او هم په $device وسیله کې وپلورل شو. د دوکان خاوند خبر شو چې یو یې لغوه کړي.",
  )
}
