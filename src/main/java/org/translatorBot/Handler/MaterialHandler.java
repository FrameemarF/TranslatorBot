package org.translatorBot.Handler;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Session.Session;

/**
 * Обработчик одного типа материала для перевода (текст, файл, голос).
 * Router выбирает подходящий обработчик через {@link #supports(Message)}.
 * К моменту вызова {@link #handle} языки в сессии уже выбраны.
 */
public interface MaterialHandler {

    /** @return {@code true}, если обработчик умеет работать с этим сообщением */
    boolean supports(Message message);

    /** Обрабатывает материал: проверяет, переводит и отправляет ответ. */
    void handle(long chatId, Message message, Session session);
}
