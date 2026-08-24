package af.tohid.shop.util

import java.util.UUID

/** شناسه‌ی یکتا، هم‌شکل با نسخه وب تا داده‌ها با هم بخوانند. */
object Ids {
    fun new(): String = "id" + UUID.randomUUID().toString().replace("-", "").take(16)
}
