package org.translatorBot.Handler;

import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

import java.util.Optional;

/**
 * Обработчик нажатий inline-кнопок.
 * <p>
 * Подтверждает нажатие у платформы, находит сессию пользователя и сопоставляет
 * данные кнопки ({@code callbackData}) с шагом диалога; сами шаги выполняет
 * {@link DialogFlow}, а историю — {@link HistoryHandler}.
 */
public class CallbackHandler {
    /** Отправитель сообщений (подтверждение нажатия). */
    private final MessageSender sender;
    /** Хранилище сессий пользователей. */
    private final SessionStore sessions;
    /** Шаги диалога. */
    private final DialogFlow flow;
    /** Обработчик истории переводов. */
    private final HistoryHandler history;
    /** Поиск языков по callback-данным. */
    private final LanguageCatalog languages;

    /**
     * Создаёт обработчик нажатий кнопок.
     *
     * @param sender   отправитель сообщений (подтверждение нажатия)
     * @param sessions хранилище сессий
     * @param flow     шаги диалога
     * @param history  обработчик истории переводов
     */
    public CallbackHandler(MessageSender sender, SessionStore sessions, DialogFlow flow,
                           HistoryHandler history) {
        this.sender = sender;
        this.sessions = sessions;
        this.flow = flow;
        this.history = history;
        this.languages = new LanguageCatalog();
    }

    /**
     * Подтверждает callback у платформы и выполняет действие, соответствующее
     * данным кнопки.
     *
     * @param q универсальный callback-запрос
     */
    public void handle(UserCallback q) {
        sender.ackCallback(q.callbackId());

        long chatId = q.chatId();
        long accountId = q.accountId();
        Session s = sessions.get(accountId);
        String data = q.data();

        switch (data) {
            case "TRANSLATE", "CHANGE_LANG" -> flow.startSourceChoice(chatId, s);
            case "HELP"                     -> flow.showHelp(chatId);
            case "MORE"                     -> flow.askForText(chatId, s);
            case "MENU"                     -> flow.showMenu(chatId, s);
            case "HISTORY"                  -> history.show(chatId, accountId);
            case "CLEAR_HISTORY"            -> history.clear(chatId, accountId);
            default                         -> handleLanguage(chatId, s, data);
        }
    }

    /**
     * Разбирает кнопки выбора языка вида {@code SRC_DE} / {@code TGT_FR}.
     * Неизвестные данные приводят к сообщению об ошибке.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     * @param data   данные кнопки
     */
    private void handleLanguage(long chatId, Session s, String data) {
        Optional<Language> lang = data.length() > 4
                ? languages.findByName(data.substring(4))
                : Optional.empty();

        if (lang.isPresent() && data.startsWith("SRC_")) {
            flow.pickSource(chatId, s, lang.get());
        } else if (lang.isPresent() && data.startsWith("TGT_")) {
            flow.pickTarget(chatId, s, lang.get());
        } else {
            flow.showUnknown(chatId);
        }
    }
}
