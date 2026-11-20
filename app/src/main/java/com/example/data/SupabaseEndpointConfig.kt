package com.example.data

import com.example.BuildConfig

/**
 * Centralizes Supabase public project base URL normalization and configuration checks.
 *
 * - Strips accidental REST/Auth/Functions/Storage path suffixes (e.g. `/rest/v1/`) so users
 *   who paste the REST API URL from Supabase Dashboard still connect cleanly to `/auth/v1`,
 *   `/rest/v1`, `/functions/v1`, and `/storage/v1`.
 * - Remaps the legacy unreachable project ref (`dtavbwdnopbizlvokphd.supabase.co`) to the
 *   active Azril Clas Supabase project base URL (`https://edalyugepcslowffevgi.supabase.co`)
 *   while preserving placeholder detection (`YOUR_PROJECT_REF`) for offline/local testing and
 *   honoring any new `SUPABASE_URL` configured via AI Studio Secrets.
 */
object SupabaseEndpointConfig {
    const val ACTIVE_PROJECT_BASE_URL = "https://edalyugepcslowffevgi.supabase.co"
    private const val LEGACY_UNREACHABLE_HOST = "dtavbwdnopbizlvokphd.supabase.co"
    private val API_SUFFIX_REGEX =
        Regex("/(rest|auth|functions|storage|realtime)/v1(/.*)?$", RegexOption.IGNORE_CASE)

    fun normalizeBaseUrl(rawUrl: String = BuildConfig.SUPABASE_URL): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val withoutApiSuffix = trimmed.replace(API_SUFFIX_REGEX, "").trimEnd('/')
        if (withoutApiSuffix.contains(LEGACY_UNREACHABLE_HOST, ignoreCase = true)) {
            return ACTIVE_PROJECT_BASE_URL
        }
        return withoutApiSuffix
    }

    fun isConfigured(rawUrl: String, rawAnonKey: String): Boolean {
        val cleanUrl = normalizeBaseUrl(rawUrl)
        val cleanKey = rawAnonKey.trim()
        return cleanUrl.startsWith("https://") &&
            !cleanUrl.contains("YOUR_PROJECT_REF", ignoreCase = true) &&
            cleanKey.length > 20 &&
            !cleanKey.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)
    }
}
