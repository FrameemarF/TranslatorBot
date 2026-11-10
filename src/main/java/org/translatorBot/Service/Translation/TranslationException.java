package org.translatorBot.Service.Translation;

/**
 * Ошибка сервиса перевода: недоступность сервиса, ошибочный HTTP-код,
 * пустой или некорректный ответ. Наследуется от {@link RuntimeException},
 * чтобы вызывающий код не был обязан обрабатывать её на каждом уровне.
 */
public class TranslationException extends RuntimeException {

    /**
     * @param message описание ошибки
     */
    public TranslationException(String message) {
        super(message);
    }

    /**
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
