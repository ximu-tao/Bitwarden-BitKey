package com.x8bit.bitwarden.ui.platform.feature.settings.appearance.model

import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.util.Text
import com.bitwarden.ui.util.asText

/**
 * Represents the languages supported by the app.
 *
 * Backed by the shared data layer model in `:appdata`; display labels are provided via the
 * [text] extension below.
 */
typealias AppLanguage = com.x8bit.bitwarden.data.platform.model.AppLanguage

/**
 * Returns a human-readable display label for the given [AppLanguage].
 */
val AppLanguage.text: Text
    get() = when (this) {
        AppLanguage.DEFAULT -> BitwardenString.default_system.asText()
        AppLanguage.AFRIKAANS -> "Afrikaans".asText()
        AppLanguage.BELARUSIAN -> "Беларуская".asText()
        AppLanguage.BULGARIAN -> "български".asText()
        AppLanguage.CATALAN -> "català".asText()
        AppLanguage.CZECH -> "čeština".asText()
        AppLanguage.DANISH -> "Dansk".asText()
        AppLanguage.GERMAN -> "Deutsch".asText()
        AppLanguage.GREEK -> "Ελληνικά".asText()
        AppLanguage.ENGLISH -> "English".asText()
        AppLanguage.ENGLISH_BRITISH -> "English (United Kingdom)".asText()
        AppLanguage.SPANISH -> "Español".asText()
        AppLanguage.ESTONIAN -> "eesti".asText()
        AppLanguage.PERSIAN -> "فارسی".asText()
        AppLanguage.FINNISH -> "suomi".asText()
        AppLanguage.FRENCH -> "Français".asText()
        AppLanguage.HINDI -> "हिन्दी".asText()
        AppLanguage.CROATIAN -> "hrvatski".asText()
        AppLanguage.HUNGARIAN -> "magyar".asText()
        AppLanguage.INDONESIAN -> "Bahasa Indonesia".asText()
        AppLanguage.ITALIAN -> "Italiano".asText()
        AppLanguage.HEBREW -> "עברית".asText()
        AppLanguage.JAPANESE -> "日本語".asText()
        AppLanguage.KOREAN -> "한국어".asText()
        AppLanguage.LATVIAN -> "Latvietis".asText()
        AppLanguage.MALAYALAM -> "മലയാളം".asText()
        AppLanguage.NORWEGIAN -> "norsk (bokmål)".asText()
        AppLanguage.DUTCH -> "Nederlands".asText()
        AppLanguage.POLISH -> "Polski".asText()
        AppLanguage.PORTUGUESE_BRAZILIAN -> "Português do Brasil".asText()
        AppLanguage.PORTUGUESE -> "Português".asText()
        AppLanguage.ROMANIAN -> "română".asText()
        AppLanguage.RUSSIAN -> "русский".asText()
        AppLanguage.SLOVAK -> "slovenčina".asText()
        AppLanguage.SWEDISH -> "svenska".asText()
        AppLanguage.THAI -> "ไทย".asText()
        AppLanguage.TURKISH -> "Türkçe".asText()
        AppLanguage.UKRAINIAN -> "українська".asText()
        AppLanguage.VIETNAMESE -> "Tiếng Việt".asText()
        AppLanguage.CHINESE_SIMPLIFIED -> "中文（中国大陆）".asText()
        AppLanguage.CHINESE_TRADITIONAL -> "中文（台灣）".asText()
    }