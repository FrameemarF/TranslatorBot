package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

import java.util.List;

/**
 * Маршрутизатор входящих обновлений от Telegram.
 * <p>
 * Реализует {@link LongPollingUpdateConsumer}: принимает пачку {@link Update}
 * и только распределяет каждый апдейт по обработчикам — {@link CommandHandler}
 * для команд, {@link CallbackHandler} для нажатий inline-кнопок и
 * {@link MaterialHandler} для материала на перевод (текст, файл, голос).
 * Сами действия выполняют обработчики и {@link DialogFlow}.
 */
public class Router implements LongPollingUpdateConsumer {
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
     * Обрабатывает пачку обновлений, полученных от Telegram.
     *
     * @param updates список входящих обновлений
     */
    @Override
    public void consume(List<Update> updates) {
        updates.forEach(this::handleUpdate);
    }

    /**
     * Разбирает одно обновление и направляет его в обработчик сообщений
     * или нажатий inline-кнопок. Ошибки логируются и не прерывают обработку
     * остальных обновлений.
     *
     * @param update входящее обновление
     */
    private void handleUpdate(Update update) {
        try {
            if (update.hasCallbackQuery()) callbacks.handle(update.getCallbackQuery());
            else if (update.hasMessage()) handleMessage(update.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle update", e);
        }
    }

    /**
     * Направляет сообщение пользователя: команды — в {@link CommandHandler},
     * материал в состоянии ожидания — в {@link MaterialHandler}, иначе
     * показывает подсказку с главным меню.
     *
     * @param msg сообщение от Telegram
     */
    private void handleMessage(Message msg) {
        long chatId = msg.getChatId();
        Session session = sessions.get(chatId);

        if (isCommand(msg)) {
            commands.handle(chatId, msg.getText(), session);
        } else if (session.getState() == Session.State.WAITING_TEXT) {
            handleMaterial(chatId, msg, session);
        } else {
            flow.showUseStart(chatId);
        }
    }

    /**
     * Проверяет, является ли сообщение командой (текст начинается с «/»).
     *
     * @param msg сообщение от Telegram
     * @return {@code true}, если сообщение содержит команду
     */
    private boolean isCommand(Message msg) {
        return msg.hasText() && msg.getText().trim().startsWith("/");
    }

    /**
     * Передаёт материал подходящему {@link MaterialHandler}. Если языки не
     * выбраны (устаревшая кнопка) — начинает выбор заново.
     *
     * @param chatId идентификатор чата
     * @param msg    сообщение с материалом
     * @param s      сессия пользователя
     */
    private void handleMaterial(long chatId, Message msg, Session s) {
        if (s.getSource() == null || s.getTarget() == null) {
            flow.startSourceChoice(chatId, s);
            return;
        }
        handlers.stream()
                .filter(h -> h.supports(msg))
                .findFirst()
                .ifPresentOrElse(
                        h -> h.handle(chatId, msg, s),
                        () -> flow.showUnsupportedMaterial(chatId));
    }
}