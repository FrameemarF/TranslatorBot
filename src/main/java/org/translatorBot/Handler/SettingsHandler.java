package org.translatorBot.Handler;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Ui;
import org.translatorBot.User.ResponseMode;
import org.translatorBot.User.UserProfile;
import org.translatorBot.User.UserService;

import java.util.Optional;

/**
 * Экран настроек: язык-цель и режим ответа по умолчанию.
 */
public class SettingsHandler {
    private final MessageSender sender;
    private final UserService users;
    private final Ui ui;
    private final Platform platform;

    /**
     * @param sender   отправитель сообщений
     * @param users    профили пользователей
     * @param ui       тексты интерфейса
     * @param platform платформа пользователя
     */
    public SettingsHandler(MessageSender sender, UserService users, Ui ui, Platform platform) {
        this.sender = sender;
        this.users = users;
        this.ui = ui;
        this.platform = platform;
    }

    /**
     * Открывает настройки.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     */
    public void show(long chatId, long accountId) {
        Optional<UserProfile> profile = users.profile(platform, accountId);
        if (profile.isEmpty()) {
            sender.send(chatId, ui.settingsUnavailable);
            return;
        }
        sender.send(chatId, ui.settings(profile.get()),
                ui.settingsMenu(profile.get().target(), profile.get().responseMode()));
    }

    /**
     * Сохраняет язык-цель и показывает обновлённые настройки.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param target    язык или {@code null}, чтобы сбросить настройку
     */
    public void selectTarget(long chatId, long accountId, Language target) {
        if (users.saveTarget(platform, accountId, target)) {
            show(chatId, accountId);
        } else {
            sender.send(chatId, ui.settingsUnavailable);
        }
    }

    /**
     * Сохраняет режим ответа и подтверждает изменение.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param mode      новый режим
     */
    public void selectMode(long chatId, long accountId, ResponseMode mode) {
        if (!users.saveMode(platform, accountId, mode)) {
            sender.send(chatId, ui.settingsUnavailable);
            return;
        }
        Optional<UserProfile> profile = users.profile(platform, accountId);
        if (profile.isEmpty()) {
            sender.send(chatId, ui.settingsUnavailable);
            return;
        }
        sender.send(chatId, ui.settingsSaved(profile.get()), ui.menuOnly());
    }
}
