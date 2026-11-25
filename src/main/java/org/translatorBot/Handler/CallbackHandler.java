package org.translatorBot.Handler;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.Ui;
import org.translatorBot.User.ResponseMode;
import org.translatorBot.User.UserProfile;
import org.translatorBot.User.UserService;

import java.util.Optional;

/**
 * Обработчик нажатий inline-кнопок.
 */
public class CallbackHandler {
    private final MessageSender sender;
    private final SessionStore sessions;
    private final DialogFlow flow;
    private final HistoryHandler history;
    private final ProfileHandler profile;
    private final SettingsHandler settings;
    private final StatsHandler stats;
    private final UserService users;
    private final Platform platform;
    private final LanguageCatalog languages = new LanguageCatalog();

    /**
     * @param sender   отправитель сообщений
     * @param sessions хранилище сессий
     * @param flow     шаги диалога
     * @param history  история
     * @param profile  профиль
     * @param settings настройки
     * @param stats    статистика
     * @param users    профили пользователей
     * @param platform платформа
     */
    public CallbackHandler(MessageSender sender, SessionStore sessions, DialogFlow flow,
                           HistoryHandler history, ProfileHandler profile,
                           SettingsHandler settings, StatsHandler stats,
                           UserService users, Platform platform) {
        this.sender = sender;
        this.sessions = sessions;
        this.flow = flow;
        this.history = history;
        this.profile = profile;
        this.settings = settings;
        this.stats = stats;
        this.users = users;
        this.platform = platform;
    }

    /**
     * Подтверждает callback и выполняет действие кнопки.
     *
     * @param q универсальный callback
     */
    public void handle(UserCallback q) {
        sender.ackCallback(q.callbackId());

        long chatId = q.chatId();
        long accountId = q.accountId();
        Session session = sessions.get(accountId);
        String data = q.data();

        switch (data) {
            case "TRANSLATE" -> flow.startTranslation(chatId, session, defaultTarget(accountId));
            case "CHANGE_LANG" -> flow.startSourceChoice(chatId, session);
            case "HELP" -> flow.showHelp(chatId);
            case "MORE" -> flow.askForText(chatId, session);
            case "MENU" -> flow.showMenu(chatId, session);
            case "HISTORY" -> history.show(chatId, accountId);
            case "CLEAR_HISTORY" -> history.clear(chatId, accountId);
            case "PROFILE" -> profile.show(chatId, accountId);
            case "SETTINGS" -> settings.show(chatId, accountId);
            case "STATS" -> stats.show(chatId);
            default -> {
                if (!handleSettings(chatId, accountId, data)
                        && !handleHistoryPage(chatId, accountId, data)) {
                    handleLanguage(chatId, session, data);
                }
            }
        }
    }

    /**
     * Обрабатывает кнопки настроек.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param data      данные кнопки
     * @return {@code true}, если данные относятся к настройкам
     */
    private boolean handleSettings(long chatId, long accountId, String data) {
        if (Ui.SET_MODE_TEXT.equals(data)) {
            settings.selectMode(chatId, accountId, ResponseMode.TEXT);
            return true;
        }
        if (Ui.SET_MODE_VOICE.equals(data)) {
            settings.selectMode(chatId, accountId, ResponseMode.TEXT_AND_VOICE);
            return true;
        }
        if (!data.startsWith(Ui.SET_TARGET_PREFIX)) {
            return false;
        }
        String value = data.substring(Ui.SET_TARGET_PREFIX.length());
        if ("NONE".equals(value)) {
            settings.selectTarget(chatId, accountId, null);
            return true;
        }
        Optional<Language> language = languages.findByName(value);
        if (language.isPresent()) {
            settings.selectTarget(chatId, accountId, language.get());
        } else {
            flow.showUnknown(chatId);
        }
        return true;
    }

    /**
     * Разбирает кнопки навигации вида {@code HISTORY_PAGE_2_12345}.
     * Поддерживается и старый формат без границы снимка.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param data      данные кнопки
     * @return {@code true}, если данные относятся к истории
     */
    private boolean handleHistoryPage(long chatId, long accountId, String data) {
        if (!data.startsWith(Ui.HISTORY_PAGE_PREFIX)) {
            return false;
        }
        try {
            String value = data.substring(Ui.HISTORY_PAGE_PREFIX.length());
            int separator = value.indexOf('_');
            String pagePart = separator >= 0 ? value.substring(0, separator) : value;
            long anchorId = separator >= 0
                    ? Long.parseLong(value.substring(separator + 1))
                    : Long.MAX_VALUE;
            int page = Integer.parseInt(pagePart);
            if (page < 0 || anchorId < 0) {
                flow.showUnknown(chatId);
                return true;
            }
            history.showPage(chatId, accountId, page, anchorId);
        } catch (NumberFormatException e) {
            flow.showUnknown(chatId);
        }
        return true;
    }

    /**
     * Разбирает кнопки выбора языка {@code SRC_DE} / {@code TGT_FR}.
     *
     * @param chatId  идентификатор чата
     * @param session сессия пользователя
     * @param data    данные кнопки
     */
    private void handleLanguage(long chatId, Session session, String data) {
        Optional<Language> language = data.length() > 4
                ? languages.findByName(data.substring(4))
                : Optional.empty();
        if (language.isPresent() && data.startsWith("SRC_")) {
            flow.pickSource(chatId, session, language.get());
        } else if (language.isPresent() && data.startsWith("TGT_")) {
            flow.pickTarget(chatId, session, language.get());
        } else {
            flow.showUnknown(chatId);
        }
    }

    /**
     * Возвращает язык-цель по умолчанию.
     *
     * @param accountId идентификатор аккаунта
     * @return язык или {@code null}
     */
    private Language defaultTarget(long accountId) {
        return users.profile(platform, accountId)
                .map(UserProfile::target)
                .orElse(null);
    }
}
