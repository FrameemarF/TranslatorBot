package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.Platform;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.User.UserService;

import java.time.Instant;

/**
 * Маршрутизатор входящих обновлений. Принимает универсальные модели
 * (UserMessage, UserCallback) и распределяет их по обработчикам.
 * <p>
 * Один экземпляр создаётся для конкретной платформы: он знает своё
 * {@link Platform} и общий {@link UserService}. Общие сервисы (история,
 * профили, статистика) не создаются заново для каждой платформы.
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
    /** Обработчик текста. */
    private final TextHandler text;
    /** Обработчик файлов. */
    private final DocumentHandler documents;
    /** Обработчик голосовых. */
    private final VoiceHandler voice;
    /** Платформа пользователя. */
    private final Platform platform;
    /** Профили пользователей (регистрация при первом обращении). */
    private final UserService users;

    /**
     * @param sessions  хранилище сессий
     * @param commands  обработчик команд
     * @param callbacks обработчик нажатий кнопок
     * @param flow      шаги диалога
     * @param text      обработчик текста
     * @param documents обработчик файлов
     * @param voice     обработчик голосовых
     * @param platform  платформа пользователя
     * @param users     профили пользователей
     */
    public Router(SessionStore sessions, CommandHandler commands, CallbackHandler callbacks,
                  DialogFlow flow, TextHandler text, DocumentHandler documents,
                  VoiceHandler voice, Platform platform, UserService users) {
        this.sessions = sessions;
        this.commands = commands;
        this.callbacks = callbacks;
        this.flow = flow;
        this.text = text;
        this.documents = documents;
        this.voice = voice;
        this.platform = platform;
        this.users = users;
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
     * Обрабатывает входящее сообщение: регистрирует пользователя, а затем
     * распределяет сообщение по командам и типам материала.
     *
     * @param msg универсальное сообщение
     */
    public void handleMessage(UserMessage msg) {
        users.touch(platform, msg.accountId(), msg.userName(), Instant.now());
        Session session = sessions.get(msg.accountId());

        if (msg.type() == MessageType.TEXT && msg.text().trim().startsWith("/")) {
            commands.handle(msg.chatId(), msg.accountId(), msg.text(), session);
        } else if (session.getState() == Session.State.WAITING_TEXT) {
            handleMaterial(msg, session);
        } else {
            flow.showUseStart(msg.chatId());
        }
    }

    /**
     * Передаёт материал подходящему обработчику. Если языки не выбраны
     * (устаревшая кнопка) — начинает выбор заново.
     *
     * @param msg сообщение с материалом
     * @param s   сессия пользователя
     */
    private void handleMaterial(UserMessage msg, Session s) {
        if (s.getSource() == null || s.getTarget() == null) {
            flow.startSourceChoice(msg.chatId(), s);
            return;
        }
        switch (msg.type()) {
            case TEXT -> text.handle(msg, s);
            case DOCUMENT -> documents.handle(msg, s);
            case VOICE -> voice.handle(msg, s);
            default -> {
                log.debug("No material handler for {}", msg.type());
                flow.showUnsupportedMaterial(msg.chatId());
            }
        }
    }
}
