package com.x8bit.bitwarden.data.platform.model

/**
 * Represents the languages supported by the app.
 *
 * The display labels are provided by the UI layer (see the `text` extension in the app's
 * settings appearance model package).
 */
enum class AppLanguage(val localeName: String?) {
    DEFAULT(localeName = null),
    AFRIKAANS(localeName = "af"),
    BELARUSIAN(localeName = "be"),
    BULGARIAN(localeName = "bg"),
    CATALAN(localeName = "ca"),
    CZECH(localeName = "cs"),
    DANISH(localeName = "da"),
    GERMAN(localeName = "de"),
    GREEK(localeName = "el"),
    ENGLISH(localeName = "en"),
    ENGLISH_BRITISH(localeName = "en-GB"),
    SPANISH(localeName = "es"),
    ESTONIAN(localeName = "et"),
    PERSIAN(localeName = "fa"),
    FINNISH(localeName = "fi"),
    FRENCH(localeName = "fr"),
    HINDI(localeName = "hi"),
    CROATIAN(localeName = "hr"),
    HUNGARIAN(localeName = "hu"),
    INDONESIAN(localeName = "in"),
    ITALIAN(localeName = "it"),
    HEBREW(localeName = "iw"),
    JAPANESE(localeName = "ja"),
    KOREAN(localeName = "ko"),
    LATVIAN(localeName = "lv"),
    MALAYALAM(localeName = "ml"),
    NORWEGIAN(localeName = "nb"),
    DUTCH(localeName = "nl"),
    POLISH(localeName = "pl"),
    PORTUGUESE_BRAZILIAN(localeName = "pt-BR"),
    PORTUGUESE(localeName = "pt-PT"),
    ROMANIAN(localeName = "ro"),
    RUSSIAN(localeName = "ru"),
    SLOVAK(localeName = "sk"),
    SWEDISH(localeName = "sv"),
    THAI(localeName = "th"),
    TURKISH(localeName = "tr"),
    UKRAINIAN(localeName = "uk"),
    VIETNAMESE(localeName = "vi"),
    CHINESE_SIMPLIFIED(localeName = "zh-CN"),
    CHINESE_TRADITIONAL(localeName = "zh-TW"),
}