package org.translatorBot.History;

/**
 * Ошибка работы с хранилищем истории переводов: база данных недоступна,
 * запрос не выполнен или ответ содержит некорректные данные. Наследуется
 * от {@link RuntimeException}, чтобы вызывающий код не был обязан объявлять
 * её в сигнатуре.
 */
public class HistoryException extends RuntimeException {

    /**
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public HistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
