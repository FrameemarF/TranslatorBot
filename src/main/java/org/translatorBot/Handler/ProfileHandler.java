package org.translatorBot.Handler;

import org.translatorBot.History.Platform;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Stats.StatisticsService;
import org.translatorBot.Stats.TranslationStatistics;
import org.translatorBot.Ui;
import org.translatorBot.User.UserProfile;
import org.translatorBot.User.UserService;

import java.util.Optional;

/**
 * Показывает профиль пользователя и его личную статистику.
 */
public class ProfileHandler {
    private final MessageSender sender;
    private final UserService users;
    private final StatisticsService statistics;
    private final Ui ui;
    private final Platform platform;

    /**
     * @param sender     отправитель сообщений
     * @param users      профили пользователей
     * @param statistics статистика
     * @param ui         тексты интерфейса
     * @param platform   платформа пользователя
     */
    public ProfileHandler(MessageSender sender, UserService users,
                          StatisticsService statistics, Ui ui, Platform platform) {
        this.sender = sender;
        this.users = users;
        this.statistics = statistics;
        this.ui = ui;
        this.platform = platform;
    }

    /**
     * Отправляет профиль и личную статистику.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     */
    public void show(long chatId, long accountId) {
        Optional<UserProfile> profile = users.profile(platform, accountId);
        Optional<TranslationStatistics> stats = statistics.personal(platform, accountId);
        if (profile.isEmpty() || stats.isEmpty()) {
            sender.send(chatId, ui.profileUnavailable);
            return;
        }
        sender.send(chatId, ui.profile(profile.get(), platform, stats.get()), ui.menuOnly());
    }
}
