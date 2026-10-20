package org.translatorBot.Handler;

import org.translatorBot.Session.Session;

/**
 * Обработчик команд пользователя ({@code /start}, {@code /help},
 * {@code /translate}, {@code /back}).
 * <p>
 * Только сопоставляет команду с шагом диалога; сами шаги выполняет
 * {@link DialogFlow}.
 */
public class CommandHandler {
    private final DialogFlow flow;

    /**
     * Создаёт обработчик команд.
     *
     * @param flow шаги диалога
     */
    public CommandHandler(DialogFlow flow) {
        this.flow = flow;
    }

    /**
     * Выполняет команду, с которой начинается текст сообщения. Всё, что идёт
     * после первого слова, игнорируется. Неизвестная команда приводит к
     * сообщению об ошибке и справке.
     *
     * @param chatId  идентификатор чата
     * @param text    текст сообщения, начинающийся с «/»
     * @param session сессия пользователя
     */
    public void handle(long chatId, String text, Session session) {
        String command = text.trim().split("\\s+")[0];
        switch (command) {
            case "/start"     -> flow.showMenu(chatId, session);
            case "/help"      -> flow.showHelp(chatId);
            case "/translate" -> flow.startSourceChoice(chatId, session);
            case "/back"      -> flow.goBack(chatId, session);
            default           -> {
                flow.showUnknown(chatId);
                flow.showHelp(chatId);
            }
        }
    }
}