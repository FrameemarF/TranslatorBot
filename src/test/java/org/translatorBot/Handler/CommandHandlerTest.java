package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.History.FakeHistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;
import org.translatorBot.User.FakeUserService;
import org.translatorBot.User.ResponseMode;
import org.translatorBot.User.UserProfile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Модульные тесты для {@link CommandHandler}.
 * <p>
 * Проверяют, что каждая поддерживаемая команда вызывает соответствующий метод
 * у диалога или профильных обработчиков, а неизвестная команда приводит
 * к показу ошибки и справки.
 */
class CommandHandlerTest {

    private final long chatId = 100L;

    private FakeDialogFlow flow;
    private FakeHistoryService history;
    private FakeProfileHandler profile;
    private FakeSettingsHandler settings;
    private FakeStatsHandler stats;
    private FakeUserService users;
    private Session session;
    private CommandHandler handler;

    /** Подготавливает тестовое окружение перед каждым тестом. */
    @BeforeEach
    void setUp() {
        flow = new FakeDialogFlow();
        history = new FakeHistoryService();
        profile = new FakeProfileHandler();
        settings = new FakeSettingsHandler();
        stats = new FakeStatsHandler();
        users = new FakeUserService();
        HistoryHandler historyHandler = new HistoryHandler(
                new RecordingSender(), history, users, new Ui(), Platform.TELEGRAM);
        session = new Session();
        handler = new CommandHandler(flow, historyHandler, profile, settings, stats,
                users, Platform.TELEGRAM);
    }

    /** Команда {@code /start} вызывает {@link DialogFlow#showMenu}. */
    @Test
    void startCallsShowMenu() {
        handler.handle(chatId, chatId, "/start", session);

        Assertions.assertEquals(1, flow.showMenuCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
        Assertions.assertSame(session, flow.lastSession);
    }

    /** Команда {@code /help} вызывает {@link DialogFlow#showHelp}. */
    @Test
    void helpCallsShowHelp() {
        handler.handle(chatId, chatId, "/help", session);

        Assertions.assertEquals(1, flow.showHelpCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
    }

    /** Команда {@code /translate} начинает перевод без языка по умолчанию. */
    @Test
    void translateCallsStartTranslation() {
        handler.handle(chatId, chatId, "/translate", session);

        Assertions.assertEquals(1, flow.startTranslationCalls);
        Assertions.assertNull(flow.lastDefaultTarget);
        Assertions.assertSame(session, flow.lastSession);
    }

    /** Команда {@code /translate} передаёт язык-цель из настроек. */
    @Test
    void translateUsesDefaultTargetFromProfile() {
        users.profile = new UserProfile("Илья", Instant.now(), Language.EN,
                ResponseMode.TEXT, ZoneOffset.UTC);

        handler.handle(chatId, chatId, "/translate", session);

        Assertions.assertEquals(Language.EN, flow.lastDefaultTarget);
    }

    /** Команда {@code /back} вызывает {@link DialogFlow#goBack}. */
    @Test
    void backCallsGoBack() {
        handler.handle(chatId, chatId, "/back", session);

        Assertions.assertEquals(1, flow.goBackCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    /** Команда {@code /history} запрашивает историю пользователя. */
    @Test
    void historyCallsHistoryService() {
        handler.handle(chatId, chatId, "/history", session);

        Assertions.assertEquals(1, history.recentCalls);
        Assertions.assertEquals(chatId, history.lastAccountId);
        Assertions.assertEquals(HistoryHandler.HISTORY_LIMIT + 1, history.lastLimit);
        Assertions.assertEquals(0, history.lastOffset);
    }

    /** Команда {@code /clear_history} удаляет историю пользователя. */
    @Test
    void clearHistoryCallsHistoryService() {
        handler.handle(chatId, chatId, "/clear_history", session);

        Assertions.assertEquals(1, history.clearCalls);
        Assertions.assertEquals(chatId, history.lastAccountId);
    }

    /** Команда {@code /profile} показывает профиль. */
    @Test
    void profileCallsProfileHandler() {
        handler.handle(chatId, chatId, "/profile", session);

        Assertions.assertEquals(1, profile.showCalls);
        Assertions.assertEquals(chatId, profile.lastAccountId);
    }

    /** Команда {@code /settings} открывает настройки. */
    @Test
    void settingsCallsSettingsHandler() {
        handler.handle(chatId, chatId, "/settings", session);

        Assertions.assertEquals(1, settings.showCalls);
        Assertions.assertEquals(chatId, settings.lastAccountId);
    }

    /** Команда {@code /stats} показывает статистику. */
    @Test
    void statsCallsStatsHandler() {
        handler.handle(chatId, chatId, "/stats", session);

        Assertions.assertEquals(1, stats.showCalls);
        Assertions.assertEquals(chatId, stats.lastChatId);
    }

    /** Неизвестная команда вызывает показ ошибки и справки. */
    @Test
    void unknownCommandCallsShowUnknownAndShowHelp() {
        handler.handle(chatId, chatId, "/foobar", session);

        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(1, flow.showHelpCalls);
        Assertions.assertEquals(0, flow.showMenuCalls);
        Assertions.assertEquals(0, flow.startTranslationCalls);
    }

    /** Аргументы после команды игнорируются. */
    @Test
    void commandWithArgumentsIgnoresExtraText() {
        handler.handle(chatId, chatId, "/start some_text", session);

        Assertions.assertEquals(1, flow.showMenuCalls);
    }

    /** Минимальная заглушка отправителя для {@link HistoryHandler}. */
    private static class RecordingSender implements MessageSender {
        @Override public void send(long chatId, String text) {}
        @Override public void send(long chatId, String text, AppKeyboard keyboard) {}
        @Override public void ackCallback(String callbackId) {}
        @Override public Path download(String fileId) { return null; }
        @Override public void sendDocument(long chatId, String fileName, Path content, String caption, AppKeyboard keyboard) {}
    }

    /** Заглушка {@link DialogFlow}, запоминающая вызовы методов. */
    private static class FakeDialogFlow extends DialogFlow {
        int showMenuCalls;
        int showHelpCalls;
        int startTranslationCalls;
        int goBackCalls;
        int showUnknownCalls;

        long lastChatId;
        Session lastSession;
        Language lastDefaultTarget;

        FakeDialogFlow() {
            super(null, null);
        }

        @Override public void showMenu(long chatId, Session s) { showMenuCalls++; lastChatId = chatId; lastSession = s; }
        @Override public void showHelp(long chatId) { showHelpCalls++; lastChatId = chatId; }
        @Override public void startTranslation(long chatId, Session s, Language defaultTarget) {
            startTranslationCalls++;
            lastChatId = chatId;
            lastSession = s;
            lastDefaultTarget = defaultTarget;
        }
        @Override public void goBack(long chatId, Session s) { goBackCalls++; lastChatId = chatId; lastSession = s; }
        @Override public void showUnknown(long chatId) { showUnknownCalls++; }
    }

    /** Заглушка {@link ProfileHandler}. */
    private static class FakeProfileHandler extends ProfileHandler {
        int showCalls;
        long lastAccountId;

        FakeProfileHandler() {
            super(null, null, null, null, null);
        }

        @Override public void show(long chatId, long accountId) { showCalls++; lastAccountId = accountId; }
    }

    /** Заглушка {@link SettingsHandler}. */
    private static class FakeSettingsHandler extends SettingsHandler {
        int showCalls;
        long lastAccountId;

        FakeSettingsHandler() {
            super(null, null, null, null);
        }

        @Override public void show(long chatId, long accountId) { showCalls++; lastAccountId = accountId; }
    }

    /** Заглушка {@link StatsHandler}. */
    private static class FakeStatsHandler extends StatsHandler {
        int showCalls;
        long lastChatId;

        FakeStatsHandler() {
            super(null, null, null);
        }

        @Override public void show(long chatId) { showCalls++; lastChatId = chatId; }
    }
}
