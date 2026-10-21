package org.translatorBot;

/**
 * Поддерживаемые языки перевода: отображаемое название и ISO-код.
 */
public enum Language {
    RU("🇷🇺 Русский", "ru"),
    EN("🇬🇧 English", "en"),
    DE("🇩🇪 Deutsch", "de"),
    FR("🇫🇷 Français", "fr"),
    ES("🇪🇸 Español", "es");

    /** Отображаемое название языка (с флагом). */
    public final String label;
    /** ISO-код языка (LibreTranslate и Whisper используют одни и те же коды). */
    public final String code;

    Language(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /**
     * Ищет язык по имени константы ({@code "RU"}, {@code "DE"}...).
     *
     * @return язык или {@code null}, если такого нет
     */
    public static Language find(String name) {
        for (Language l : values()) {
            if (l.name().equals(name)) return l;
        }
        return null;
    }
}
