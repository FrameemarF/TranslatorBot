package org.translatorBot.Handler;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Session.Session;
import org.translatorBot.User.UserProfile;
import org.translatorBot.User.UserService;

/**
 * Обработчик команд пользователя.
 * <p>
 * Только сопоставляет команду с нужным действием: шаги диалога выполняет
 * {@link DialogFlow}, историю — {@link HistoryHandler}, профиль, настройки
 * и статистику — соответствующие обработчики.
 */
public class CommandHandler {
    /** Шаги диалога. */
    private final DialogFlow flow;
    /** История переводов. */
    private final HistoryHandler history;
    /** Профиль пользователя. */
    private final ProfileHandler profile;
    /** Настройки по умолчанию. */
    private final SettingsHandler settings;
    /** Статистика. */
    private final StatsHandler stats;
    /** Профили (для языка-цели по умолчанию). */
    private final UserService users;
    /** Платформа пользователя. */
    private final Platform platform;

    /**
     * @param flow     шаги диалога
     * @param history  история переводов
     * @param profile  профиль
     * @param settings настройки
     * @param stats    статистика
     * @param users    профили пользователей
     * @param platform платформа
     */
    public CommandHandler(DialogFlow flow, HistoryHandler history, ProfileHandler profile,
                          SettingsHandler settings, StatsHandler stats,
                          UserService users, Platform platform) {
        this.flow = flow;
        this.history = history;
        this.profile = profile;
        this.settings = settings;
        this.stats = stats;
        this.users = users;
        this.platform = platform;
    }

    /**
     * Выполняет команду. Текст после первого слова игнорируется.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param text      текст сообщения, начинающийся с «/»
     * @param session   сессия пользователя
     */
    public void handle(long chatId, long accountId, String text, Session session) {
        String command = text.trim().split("\\s+")[0];
        switch (command) {
            case "/start"         -> flow.showMenu(chatId, session);
            case "/help"          -> flow.showHelp(chatId);
            case "/translate"     -> flow.startTranslation(chatId, session, defaultTarget(accountId));
            case "/back"          -> flow.goBack(chatId, session);
            case "/history"       -> history.show(chatId, accountId);
            case "/clear_history" -> history.clear(chatId, accountId);
            case "/profile"       -> profile.show(chatId, accountId);
            case "/settings"      -> settings.show(chatId, accountId);
            case "/stats"         -> stats.show(chatId);
            default               -> {
                flow.showUnknown(chatId);
                flow.showHelp(chatId);
            }
        }
    }

    /**
     * Возвращает язык-цель по умолчанию.
     *
     * @param accountId идентификатор аккаунта
     * @return язык или {@code null}, если настройка не задана
     */
    private Language defaultTarget(long accountId) {
        return users.profile(platform, accountId)
                .map(UserProfile::target)
                .orElse(null);
    }
}
