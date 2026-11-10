package com.dealmate.app.domain.util

import java.net.URI

object Listings {
    /**
     * Sample-data links use the reserved `.invalid` TLD (RFC 2606). They must never be opened in a
     * browser. Anything that is not a well-formed http(s) URL is also refused.
     */
    fun isSampleUrl(url: String): Boolean = hostOf(url)?.let { it == "invalid" || it.endsWith(".invalid") } ?: false

    fun isOpenable(url: String): Boolean {
        val scheme = runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull()
        return (scheme == "http" || scheme == "https") && hostOf(url) != null && !isSampleUrl(url)
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host?.lowercase()?.trimEnd('.') }.getOrNull()?.takeIf { it.isNotEmpty() }
}
