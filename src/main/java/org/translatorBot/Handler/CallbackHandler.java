package org.translatorBot.Handler;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.translatorBot.Language;
import org.translatorBot.Sender;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

/**
 * Обработчик нажатий inline-кнопок.
 * <p>
 * Подтверждает нажатие у Telegram, находит сессию пользователя и сопоставляет
 * данные кнопки ({@code callbackData}) с шагом диалога; сами шаги выполняет
 * {@link DialogFlow}.
 */
public class CallbackHandler {
    private final Sender sender;
    private final SessionStore sessions;
    private final DialogFlow flow;

    /**
     * Создаёт обработчик нажатий кнопок.
     *
     * @param sender   отправитель сообщений (подтверждение нажатия)
     * @param sessions хранилище сессий
     * @param flow     шаги диалога
     */
    public CallbackHandler(Sender sender, SessionStore sessions, DialogFlow flow) {
        this.sender = sender;
        this.sessions = sessions;
        this.flow = flow;
    }

    /**
     * Подтверждает callback у Telegram и выполняет действие, соответствующее
     * данным кнопки.
     *
     * @param q callback-запрос от Telegram
     */
    public void handle(CallbackQuery q) {
        sender.ackCallback(q.getId());
        long chatId = q.getMessage().getChatId();
        Session s = sessions.get(chatId);
        String data = q.getData();

        switch (data) {
            case "TRANSLATE", "CHANGE_LANG" -> flow.startSourceChoice(chatId, s);
            case "HELP"                     -> flow.showHelp(chatId);
            case "MORE"                     -> flow.askForText(chatId, s);
            case "MENU"                     -> flow.showMenu(chatId, s);
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
        Language lang = data.length() > 4 ? Language.find(data.substring(4)) : null;
        if (lang != null && data.startsWith("SRC_")) flow.pickSource(chatId, s, lang);
        else if (lang != null && data.startsWith("TGT_")) flow.pickTarget(chatId, s, lang);
        else flow.showUnknown(chatId);
    }
}