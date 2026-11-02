package app.fluid.launcher

import android.content.pm.ApplicationInfo

/** Section order in the apps grid. */
val CATEGORIES = listOf("Social", "Chat", "Money", "Shopping", "Food", "Travel", "Entertainment", "Photos", "Games", "Work", "Tools", "Other")

// ponytail: keyword guess on package + name, since most apps leave Android's own category unset; add words as apps get misfiled
// Checked in this order, so the specific beats the general: Progressive's package says "phone", Fire TV's says "amazon".
private val WORDS = listOf(
    "Money" to "bank pay wallet venmo cash chase amex americanexpress bofa bankofamerica capitalone robinhood kalshi coinbase " +
        "zelle remitly adp wellsfargo discover citi fidelity schwab statefarm progressive geico allstate insurance credit finance experian creditkarma",
    "Entertainment" to "youtube netflix spotify music hulu disney primevideo firetv twitch podcast audible player radio aria",
    "Tools" to "browser chrome firefox calculator clock files settings",
    "Social" to "instagram facebook snapchat tiktok twitter reddit linkedin pinterest threads tumblr discord bereal imgur",
    "Chat" to "whatsapp messag mail gmail outlook telegram signal slack teams zoom dialer phone contacts meet skype",
    "Shopping" to "amazon shop ebay walmart target etsy costco instacart temu shein bestbuy",
    "Food" to "doordash ubereats grubhub starbucks mcdonald domino chipotle food restaurant beli buffalo wings pizza",
    "Travel" to "airbnb uber lyft booking expedia airline united delta maps waze transit hotel hopper",
    "Photos" to "gallery photo camera picsart inshot lightroom snapseed vsco editor video",
    "Work" to "docs sheets slides drive notion office word excel calendar keep note jira github",
)

/** The grid section for an app: a keyword match first, then Android's own category, then system apps go to Tools. */
fun categorize(pkg: String, label: String, androidCategory: Int): String {
    val hay = (pkg + " " + label).lowercase().replace(" ", "")
    WORDS.firstOrNull { (_, words) -> words.split(' ').any { it.isNotEmpty() && it in hay } }?.let { return it.first }
    return when (androidCategory) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        ApplicationInfo.CATEGORY_MAPS -> "Travel"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Work"
        ApplicationInfo.CATEGORY_NEWS -> "Entertainment"
        else -> if (listOf("com.samsung", "com.sec.", "com.android", "com.google.android").any { pkg.startsWith(it) }) "Tools" else "Other"
    }
}
