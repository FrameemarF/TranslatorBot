package org.translatorBot.Service.Translation;

import org.translatorBot.Language;

public interface TranslationService {
    String translate(String text, Language from, Language to) throws Exception;
}