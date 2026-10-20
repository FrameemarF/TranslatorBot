package org.translatorBot.Service.Translation;

import org.translatorBot.Language;

/** Сервис перевода текста. */
public interface TranslationService {
    /** Максимальная длина текста, принимаемая сервисом перевода. */
    int MAX_TEXT_LENGTH = 4000;

    String translate(String text, Language from, Language to) throws Exception;
}
