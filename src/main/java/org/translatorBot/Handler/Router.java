package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

import java.util.List;

/**
 * Маршрутизатор входящих обновлений. Принимает универсальные модели
 * (UserMessage, UserCallback) и распределяет их по обработчикам.
 * Не зависит от конкретной платформы (Telegram, VK и т.д.).
 */
public class Router {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SessionStore sessions;
    private final CommandHandler commands;
    private final CallbackHandler callbacks;
    private final DialogFlow flow;
    private final List<MaterialHandler> handlers;

    /**
     * Создаёт маршрутизатор с заданными зависимостями.
     *
     * @param sessions  хранилище сессий
     * @param commands  обработчик команд
     * @param callbacks обработчик нажатий кнопок
     * @param flow      шаги диалога
     * @param handlers  обработчики материала (текст, файл, голос)
     */
    public Router(SessionStore sessions, CommandHandler commands, CallbackHandler callbacks,
                  DialogFlow flow, List<MaterialHandler> handlers) {
        this.sessions = sessions;
        this.commands = commands;
        this.callbacks = callbacks;
        this.flow = flow;
        this.handlers = handlers;
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
        Session session = sessions.get(msg.chatId());

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
        handlers.stream()
                .filter(h -> h.supports(msg))
                .findFirst()
                .ifPresentOrElse(
                        h -> h.handle(msg, s),
                        () -> flow.showUnsupportedMaterial(msg.chatId()));
    }
}