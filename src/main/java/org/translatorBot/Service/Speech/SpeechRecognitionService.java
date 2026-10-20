package org.translatorBot.Service.Speech;

import org.translatorBot.Language;

/** Сервис распознавания речи (speech-to-text). */
public interface SpeechRecognitionService {

    /**
     * Распознаёт речь в аудио.
     *
     * @param audio    байты аудиофайла
     * @param fileName имя файла (расширение подсказывает формат)
     * @param language язык, на котором говорит пользователь
     * @return распознанный непустой текст
     * @throws SpeechUnrecognizedException если речи в записи не найдено
     * @throws SpeechUnavailableException  если сервис недоступен или не настроен
     * @throws Exception                   при прочих сетевых ошибках
     */
    String recognize(byte[] audio, String fileName, Language language) throws Exception;
}
