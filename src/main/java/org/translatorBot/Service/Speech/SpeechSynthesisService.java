package org.translatorBot.Service.Speech;

import org.translatorBot.Language;

import java.util.List;

/**
 * Синтез речи: превращает текст перевода в аудио.
 */
public interface SpeechSynthesisService {

    /**
     * Озвучивает текст. Длинный текст разбивается на несколько частей.
     *
     * @param text     текст для озвучки
     * @param language язык текста (под него подбирается голос)
     * @return части аудио или пустой список, если сервис недоступен
     */
    List<byte[]> synthesize(String text, Language language);
}
