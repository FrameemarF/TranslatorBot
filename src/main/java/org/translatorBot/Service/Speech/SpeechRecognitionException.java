package org.translatorBot.Service.Speech;

/**
 * Общая ошибка распознавания речи. Наследуется от {@link RuntimeException},
 * чтобы интерфейс {@link SpeechRecognitionService} не объявлял
 * {@code throws Exception}.
 */
public class SpeechRecognitionException extends RuntimeException {

    /**
     * @param message описание ошибки
     */
    public SpeechRecognitionException(String message) {
        super(message);
    }

    /**
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public SpeechRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
