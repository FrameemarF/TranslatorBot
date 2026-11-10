package org.translatorBot.Service.Speech;

/** Сервис распознавания речи недоступен (ошибка сети, ключ, лимиты). */
public class SpeechUnavailableException extends SpeechRecognitionException {

    /**
     * @param message описание ошибки
     */
    public SpeechUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public SpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
