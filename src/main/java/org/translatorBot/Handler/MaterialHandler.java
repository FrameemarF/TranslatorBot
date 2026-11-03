package org.translatorBot.Handler;

import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.Session;

/**
 * Обработчик одного типа материала для перевода (текст, файл, голос).
 * Router выбирает подходящий обработчик через {@link #supports(UserMessage)}.
 * К моменту вызова {@link #handle(UserMessage, Session)} языки в сессии уже выбраны.
 */
public interface MaterialHandler {

    /**
     * Проверяет, умеет ли обработчик работать с данным сообщением.
     *
     * @param message входящее сообщение
     * @return {@code true}, если обработчик поддерживает этот тип
     */
    boolean supports(UserMessage message);

    /**
     * Обрабатывает материал: проверяет, переводит и отправляет ответ.
     *
     * @param message входящее сообщение
     * @param session сессия пользователя
     */
    void handle(UserMessage message, Session session);
}