package org.translatorBot.Handler;

import org.translatorBot.Session.Session;

/**
 * Обработчик команд пользователя ({@code /start}, {@code /help},
 * {@code /translate}, {@code /back}, {@code /history}, {@code /clear_history}).
 * <p>
 * Только сопоставляет команду с шагом диалога; сами шаги выполняет
 * {@link DialogFlow}, а историю — {@link HistoryHandler}.
 */
public class CommandHandler {
    /** Шаги диалога. */
    private final DialogFlow flow;
    /** Обработчик истории переводов. */
    private final HistoryHandler history;

    /**
     * Создаёт обработчик команд.
     *
     * @param flow    шаги диалога
     * @param history обработчик истории переводов
     */
    public CommandHandler(DialogFlow flow, HistoryHandler history) {
        this.flow = flow;
        this.history = history;
    }

    /**
     * Выполняет команду, с которой начинается текст сообщения. Всё, что идёт
     * после первого слова, игнорируется. Неизвестная команда приводит к
     * сообщению об ошибке и справке.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     * @param text      текст сообщения, начинающийся с «/»
     * @param session   сессия пользователя
     */
    public void handle(long chatId, long accountId, String text, Session session) {
        String command = text.trim().split("\\s+")[0];
        switch (command) {
            case "/start"         -> flow.showMenu(chatId, session);
            case "/help"          -> flow.showHelp(chatId);
            case "/translate"     -> flow.startSourceChoice(chatId, session);
            case "/back"          -> flow.goBack(chatId, session);
            case "/history"       -> history.show(chatId, accountId);
            case "/clear_history" -> history.clear(chatId, accountId);
            default               -> {
                flow.showUnknown(chatId);
                flow.showHelp(chatId);
            }
        }
    }
}
