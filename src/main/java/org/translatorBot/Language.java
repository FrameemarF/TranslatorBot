package org.translatorBot;

/**
 * Поддерживаемые языки перевода: флаг, отображаемое название и ISO-код.
 */
public enum Language {
    /** Русский язык. */
    RU("🇷🇺", "🇷🇺 Русский", "ru"),
    /** Английский язык. */
    EN("🇬🇧", "🇬🇧 Английский", "en"),
    /** Немецкий язык. */
    DE("🇩🇪", "🇩🇪 Немецкий", "de"),
    /** Французский язык. */
    FR("🇫🇷", "🇫🇷 Французский", "fr"),
    /** Испанский язык. */
    ES("🇪🇸", "🇪🇸 Испанский", "es");

    /** Флаг языка (используется в истории переводов). */
    public final String flag;
    /** Отображаемое название языка (с флагом). */
    public final String label;
    /** ISO-код языка (LibreTranslate и Whisper используют одни и те же коды). */
    public final String code;

    /**
     * @param flag  флаг языка
     * @param label отображаемое название с флагом
     * @param code  ISO-код языка
     */
    Language(String flag, String label, String code) {
        this.flag = flag;
        this.label = label;
        this.code = code;
    }
}
