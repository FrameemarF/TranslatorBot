package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Маршрутизатор входящих обновлений. Принимает универсальные модели
 * (UserMessage, UserCallback) и распределяет их по обработчикам.
 * Не зависит от конкретной платформы (Telegram, Discord и т.д.).
 */
public class Router {
    /** Логгер маршрутизатора. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Хранилище сессий пользователей. */
    private final SessionStore sessions;
    /** Обработчик команд. */
    private final CommandHandler commands;
    /** Обработчик нажатий кнопок. */
    private final CallbackHandler callbacks;
    /** Шаги диалога. */
    private final DialogFlow flow;
    /** Обработчики материалов по типу сообщения. */
    private final Map<MessageType, MaterialHandler> handlers;

    /**
     * Создаёт маршрутизатор с заданными зависимостями. Обработчики
     * раскладываются по типам сообщений; дубликат типа — ошибка конфигурации.
     *
     * @param sessions  хранилище сессий
     * @param commands  обработчик команд
     * @param callbacks обработчик нажатий кнопок
     * @param flow      шаги диалога
     * @param handlers  обработчики материала (текст, файл, голос)
     * @throws IllegalArgumentException если для одного типа передано два обработчика
     */
    public Router(SessionStore sessions, CommandHandler commands, CallbackHandler callbacks,
                  DialogFlow flow, List<MaterialHandler> handlers) {
        this.sessions = sessions;
        this.commands = commands;
        this.callbacks = callbacks;
        this.flow = flow;
        this.handlers = new EnumMap<>(MessageType.class);
        for (MaterialHandler handler : handlers) {
            MaterialHandler previous = this.handlers.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate material handler for " + handler.type());
            }
        }
    }

    /**
     * Обрабатывает нажатие inline-кнопки.
     *
     * @param callback универсальный callback
     */
    public void handleCallback(UserCallback callback) {
        callbacks.handle(callback);
    }

    /**
     * Обрабатывает входящее сообщение.
     *
     * @param msg универсальное сообщение
     */
    public void handleMessage(UserMessage msg) {
        Session session = sessions.get(msg.accountId());

        if (msg.type() == MessageType.TEXT && msg.text().trim().startsWith("/")) {
            commands.handle(msg.chatId(), msg.text(), session);
        } else if (session.getState() == Session.State.WAITING_TEXT) {
            handleMaterial(msg, session);
        } else {
            flow.showUseStart(msg.chatId());
        }
    }

    /**
     * Передаёт материал подходящему обработчику. Если языки не
     * выбраны (устаревшая кнопка) — начинает выбор заново.
     *
     * @param msg сообщение с материалом
     * @param s   сессия пользователя
     */
    private void handleMaterial(UserMessage msg, Session s) {
        if (s.getSource() == null || s.getTarget() == null) {
            flow.startSourceChoice(msg.chatId(), s);
            return;
        }
        MaterialHandler handler = handlers.get(msg.type());
        if (handler == null) {
            log.debug("No material handler for {}", msg.type());
            flow.showUnsupportedMaterial(msg.chatId());
            return;
        }
        handler.handle(msg, s);
    }
}
