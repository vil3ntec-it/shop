// ساخته‌شده به دستِ tools/legal/generate.mjs از core/legal/legal-texts.json — دستی ویرایش نکنید (منبع را ویرایش کنید و ابزار را بزنید).
package ir.vil3ntec.tohid.core.legal

internal object LegalTextsEn : LegalTextSet {
  override val labels = LegalLabels(version = "Version {v}", language = "Language", heading = "Privacy and terms")

  override val privacy = LegalDoc(
    LegalKind.PRIVACY, "1405-08-13",
    short = "Privacy policy",
    title = "Privacy policy of “My Shop”",
    lead = "Last reviewed: 4 November 2026 (1405/08/13) — for the Android app, the computer app and the web version.",
    sections = listOf(
      LegalSection(
        "summary", "Summary",
        paras = emptyList(),
        items = listOf(
          "Your shop’s data (goods, sales, customers, debts, purchases, expenses) **belongs to you** and is never sold or given to anyone.",
          "Without an account, the shop’s data stays only on your own device.",
          "With an account, data is kept on the “My Shop” server to sync between devices and as a backup.",
          "We have no ads, no trackers and no data selling.",
        ),
      ),
      LegalSection(
        "collected", "What is collected",
        paras = emptyList(),
        items = listOf(
          "**Account:** name, email and (optionally) phone number — for signing in and recovering your password.",
          "**Shop ledger:** whatever you record in the app yourself, including your customers’ names and phone numbers.",
          "**Device:** the device’s name and type and the app’s identifier — for the device list and the subscription’s device limit. In the web version this identifier is a random number created once in that browser and also sent when signing in; it is not derived from your name, email or any other data.",
          "**Visits:** when it opens, the installed app sends the device identifier, platform and app version at most once every six hours, even without an account — to count users and for support messages. It contains no data from the shop ledger.",
          "**Location (optional):** only if you allow it during sign-up, the device’s coordinates (latitude, longitude and accuracy) are recorded once with your account, together with the IP address. Sending the location again or periodically requires separate explicit consent and is off by default.",
          "**Marketplace (optional):** if the shop owner gives the shop’s location for “Near me”, it is stored only coarsely (about one kilometre) and buyers are never shown the coordinates, only a rounded distance. When a buyer taps “Near me”, their location is rounded to about one kilometre on their own device, sent only for that search, and is neither stored nor written to logs.",
          "**Crash reports:** if the app closes unexpectedly, a technical error report (without ledger data and without personal data) is sent to the server with a tracking code so the problem can be fixed.",
          "**Customer file access history:** when the shop owner or a staff member opens a customer’s file, sees or uses their full number or address (call, SMS, WhatsApp), or exports a statement or the customer list, that staff member’s ID, name and role, the device ID, the kind of action and the time are recorded in the shop ledger (and, with sync, on that shop’s server) — one row per person per customer every ten minutes, without the customer’s name, number, address or notes. Only the shop owner and whoever can see the “activity history” see it in the file; it is kept like the activity history until its retention period is set.",
          "**Chat with customers (if the shop turns it on):** chat messages, voice messages and photos are kept on the server so they reach the other side.",
          "**Product photos:** photos you upload for a product or the storefront stay on the server; your account photo is only on that device.",
          "**Google libraries:** the ML Kit barcode scanner reads the image on the phone itself and the image never leaves it; the library itself sends the device model and version and the app’s name to Google to measure its performance. “Sign in with Google” (optional) only provides that account’s name and email.",
          "**Invoice QR (if the shop turns it on):** each sale gets a random, unguessable code. Anyone holding the paper invoice can scan it to see a read-only page for that one sale: the shop’s name and logo, the items, the amounts and the payment method. The customer’s and debtor’s name and phone are not on that page, and the shopkeeper can turn the QR off in settings.",
        ),
      ),
      LegalSection(
        "permissions", "Android app permissions and why",
        paras = emptyList(),
        items = listOf(
          "**Camera:** reading barcodes and taking product photos. No photo is taken without you asking.",
          "**Bluetooth:** connecting to the receipt printer and the barcode scanner.",
          "**Notifications:** low-stock and expiry alerts and debt reminders.",
          "**Location (optional):** once, during sign-up, to record the location with the account. Without separate explicit consent the app neither sends the location again nor asks for permission when it opens. The app works fully without it.",
          "**Internet:** sync, backup and sign-in.",
          "**Microphone (optional):** only when you press the button to record a voice message in chat.",
          "**Nearby devices and foreground service:** keeping a Bluetooth barcode scanner connected while the app is closed, with an ongoing notification; it reconnects after the phone is switched on.",
          "**NFC:** while the HesabPay payment sheet is open, the shop’s phone presents itself as a payment tag; no bank card is read.",
          "**Notification access (optional, off by default):** only HesabPay app notifications, only on that phone and only to suggest “money received” at the till; the notification text is not sent anywhere.",
        ),
      ),
      LegalSection(
        "storage", "Storage and security",
        paras = emptyList(),
        items = listOf(
          "Communication with the server is HTTPS only.",
          "The sign-in token is stored encrypted on the phone.",
          "The ledger backup stays on the server so your data comes back if the phone is lost. This backup is not encrypted on the server itself, and the system administrator has access to it.",
          "Full-server backup copies are encrypted only if the system administrator has set a backup passphrase.",
        ),
      ),
      LegalSection(
        "delete-account", "Deleting your account and data",
        paras = emptyList(),
        items = listOf(
          "**In the app:** “My account → Edit account & shop → Delete account”. After confirming your password again (or an email code) and typing that account’s email or number, the account and all of its data on the server are deleted.",
          "**Without the app, on the web:** sign in to the [“My Shop” web portal](/portal/#delete-account) with your email and a six-digit code and open the “Delete account” tab. You do not need to reinstall the app.",
          "**Through support:** if deletion from the app itself is not turned on yet, or the account also has a pump, the same sheet sends a deletion request to “Support” and the system administrator deletes the account.",
          "**What is deleted:** the account itself, its sessions and devices, and the shops you own with their goods, sales, customers, debts, sync ledger and backups on the server. Staff of those shops lose their access. Deletion cannot be undone.",
          "**What remains:** records of subscription payments (the system’s financial ledger), a record of the deletion itself without email or number, and full-server backups until the end of their retention period. Someone else’s shop that you were only a member of is not deleted; only your membership is.",
          "Data that is only on the device is deleted by uninstalling the app (or clearing the app’s data in Android settings).",
        ),
      ),
      LegalSection(
        "customer-erasure", "Erasing one customer’s data from a shop’s server",
        paras = listOf(
          "Only when that shop has turned this option on. The customer asks from their storefront account (after freshly confirming that number with a code), or the shop owner asks from the customer’s file in the app (after confirming their identity again); staff cannot. The scope is always that one shop, and it is not carried out while an order is still open.",
        ),
        items = listOf(
          "**Deleted or anonymised:** name, number, address (except the area), notes, photos and delivery location, chat text and its media, cart, favourites and shopping list, “Notify me” requests, and review and problem-report text.",
          "**Kept without a name:** financial and settlement records (amount, status and time of orders, refunds and courier settlements) and star ratings — without the customer’s name and number, because the shop’s accounts would not stay correct without them.",
          "**Kept:** anti-abuse lists — a number the shop has blocked, or whose chat it has blocked (the number itself), and the one-way code of the number used for the discount-code limit —, the system administrator’s case file, and the customer’s global account, which only the customer can delete from their storefront account.",
          "**The shop’s device ledger:** debtors and sales the shop recorded in its own app stay on the device and in sync until the shop itself deletes them in the app; this request sends no command to the device.",
          "**Record:** of the request itself only the time, the method and the number of rows are kept — not the number, not the name.",
          "**File access history:** the rows the server kept about viewing this customer’s file are unlinked — who and when remain, but not the link to this customer; the same rows in the shop’s device ledger stay like the rest of that ledger.",
          "**Backups:** backup copies made before the erasure stay until the end of their own retention period and contain the earlier data; if the server is restored from such a copy, the erasure must be requested again.",
          "Erasure cannot be undone, and no copy of the erased data is written anywhere.",
        ),
      ),
      LegalSection(
        "contact", "Contact",
        paras = listOf(
          "Questions about privacy: send a message from the “Support” section inside the app.",
        ),
        items = emptyList(),
      ),
    ),
  )

  override val terms = LegalDoc(
    LegalKind.TERMS, "1",
    short = "Terms and conditions",
    title = "Terms and conditions of use",
    lead = "",
    sections = listOf(
      LegalSection(
        "confidentiality", "Data protection and confidentiality",
        paras = listOf(
          "Shop, sales, customer, account, stock and all other information recorded by the user is confidential and is not made available to unauthorised persons without the user’s permission, except where providing it is required by law.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "backup", "Data backup",
        paras = listOf(
          "User data is backed up regularly so that it can be recovered if a problem occurs. However, the user is also responsible for keeping a backup copy of their important information.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "responsibility", "User responsibility",
        paras = listOf(
          "The user is responsible for the information they enter in the app and must protect their account and password. Sign-in details must not be shared with others without permission.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "misuse", "Illegal and unauthorised use",
        paras = listOf(
          "Any illegal or unauthorised use of the app is prohibited, including intrusion, accessing other people’s accounts or data, tampering with the system or data, stealing data, abusing the services and attempting to bypass the app’s limits. If such activity is detected, the user’s account may be blocked and their access cut off.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "support", "Support",
        paras = listOf(
          "The app’s support service is available 24 hours a day, 7 days a week (24/7). Users can contact support through the “Support” section inside the app or the official contact channels announced in the app. To keep accounts secure, sending your password or sign-in code to support is prohibited.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "subscription", "Subscription renewal and expiry",
        paras = listOf(
          "In the final week of the subscription period, the user will be told when the subscription expires so there is enough time to renew. If it is not renewed by the end of the set period, access to sections and features that need an active subscription may be automatically limited, locked or disabled. Renewing the subscription on time and keeping access to the app’s features is the user’s responsibility.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "disruption", "Service disruption",
        paras = listOf(
          "Because of maintenance, updates, technical problems, internet disruption or factors beyond our control, access to the app or some of its features may be disrupted for a limited time.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "updates", "Updates",
        paras = listOf(
          "To improve the app’s security, performance and features, the software may be updated over time or some of its features may be changed.",
        ),
        items = emptyList(),
      ),
      LegalSection(
        "acceptance", "Acceptance of the terms",
        paras = listOf(
          "Using the app, signing up or activating a subscription constitutes acceptance of these terms and conditions.",
        ),
        items = emptyList(),
      ),
    ),
  )
}
