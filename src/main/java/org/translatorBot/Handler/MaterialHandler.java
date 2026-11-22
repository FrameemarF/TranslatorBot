package org.translatorBot.Handler;

import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.Session;

/**
 * Обработчик одного типа материала для перевода (текст, файл, голос).
 * Router раскладывает обработчики по типам сообщений через {@link #type()},
 * поэтому к моменту вызова {@link #handle(UserMessage, Session)} языки
 * в сессии уже выбраны.
 */
public interface MaterialHandler {

    /**
     * Возвращает тип сообщения, который обрабатывает реализация.
     *
     * @return поддерживаемый тип материала
     */
    MessageType type();

    /**
     * Обрабатывает материал: проверяет, переводит и отправляет ответ.
     *
     * @param message входящее сообщение
     * @param session сессия пользователя
     */
    void handle(UserMessage message, Session session);
}
