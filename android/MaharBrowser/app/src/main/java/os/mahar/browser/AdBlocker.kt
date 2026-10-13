package os.mahar.browser

/**
 * Basic ad/tracker blocker. Mirrors the desktop Mahar Browser blocklist:
 * any request whose host or URL contains one of these entries is dropped.
 */
object AdBlocker {
    var enabled: Boolean = true

    private val BLOCKLIST = listOf(
        // classic ad networks
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "2mdn.net", "adservice.google.com", "ads.yahoo.com", "advertising.com",
        "adsrvr.org", "adnxs.com", "criteo.com", "criteo.net", "pubmatic.com",
        "rubiconproject.com", "openx.net", "openx.com", "casalemedia.com",
        "contextweb.com", "sovrn.com", "tribalfusion.com", "zedo.com",
        "adform.net", "adform.com", "smartadserver.com", "serving-sys.com",
        "adtech.de", "yieldmo.com", "sharethrough.com", "nativo.com",
        // native / content-recommendation widgets
        "taboola.com", "outbrain.com", "mgid.com", "revcontent.com",
        "adblade.com", "content.ad", "zergnet.com",
        // pop / aggressive networks
        "popads.net", "popcash.net", "adcash.com", "exoclick.com",
        "propellerads.com", "adsterra.com", "clickadu.com", "hilltopads.net",
        "juicyads.com", "trafficjunky.net", "bidvertiser.com",
        // misc ad / monetisation
        "infolinks.com", "media.net", "chitika.net", "amazon-adsystem.com",
        // analytics / tracking pixels
        "scorecardresearch.com", "quantserve.com", "moatads.com", "moat.com",
        "hotjar.com", "crazyegg.com", "fullstory.com", "mixpanel.com",
        "segment.io", "optimizely.com", "newrelic.com",
        "googletagmanager.com", "google-analytics.com",
        "connect.facebook.net", "pixel.facebook.com", "analytics.tiktok.com",
        // generic URL substrings
        "/ads/", "/adserver", "/popunder", "/banner-ads", "ad_banner",
        "sponsored-link", "/track/pixel"
    )

    fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        val lower = url.lowercase()
        return BLOCKLIST.any { lower.contains(it) }
    }
}
