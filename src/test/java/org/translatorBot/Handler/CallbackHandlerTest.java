package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.History.FakeHistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.Ui;
import org.translatorBot.User.FakeUserService;
import org.translatorBot.User.ResponseMode;

import java.nio.file.Path;

/**
 * Модульные тесты для {@link CallbackHandler}.
 */
class CallbackHandlerTest {

    private final long chatId = 100L;
    private final String callbackId = "cb-123";

    private FakeSender sender;
    private SessionStore sessions;
    private Session session;
    private FakeDialogFlow flow;
    private FakeHistoryService history;
    private FakeProfileHandler profile;
    private FakeSettingsHandler settings;
    private FakeStatsHandler stats;
    private FakeUserService users;
    private CallbackHandler handler;

    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        sessions = new InMemorySessionStore();
        session = sessions.get(chatId);
        flow = new FakeDialogFlow();
        history = new FakeHistoryService();
        profile = new FakeProfileHandler();
        settings = new FakeSettingsHandler();
        stats = new FakeStatsHandler();
        users = new FakeUserService();
        HistoryHandler historyHandler = new HistoryHandler(
                sender, history, users, new Ui(), Platform.TELEGRAM);
        handler = new CallbackHandler(sender, sessions, flow, historyHandler,
                profile, settings, stats, users, Platform.TELEGRAM);
    }

    private UserCallback createCallback(String data) {
        return new UserCallback(chatId, callbackId, data);
    }

    @Test
    void shouldAlwaysAckCallback() {
        handler.handle(createCallback("TRANSLATE"));
        Assertions.assertEquals(1, sender.ackCalls);
        Assertions.assertEquals(callbackId, sender.lastAckId);
    }

    @Test
    void translateCallsStartTranslation() {
        handler.handle(createCallback("TRANSLATE"));
        Assertions.assertEquals(1, flow.startTranslationCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    @Test
    void changeLangCallsStartSourceChoice() {
        handler.handle(createCallback("CHANGE_LANG"));
        Assertions.assertEquals(1, flow.startSourceChoiceCalls);
    }

    @Test
    void helpCallsShowHelp() {
        handler.handle(createCallback("HELP"));
        Assertions.assertEquals(1, flow.showHelpCalls);
    }

    @Test
    void moreCallsAskForText() {
        handler.handle(createCallback("MORE"));
        Assertions.assertEquals(1, flow.askForTextCalls);
    }

    @Test
    void menuCallsShowMenu() {
        handler.handle(createCallback("MENU"));
        Assertions.assertEquals(1, flow.showMenuCalls);
    }

    @Test
    void sourceLanguageButtonCallsPickSource() {
        handler.handle(createCallback("SRC_DE"));
        Assertions.assertEquals(1, flow.pickSourceCalls);
        Assertions.assertEquals(Language.DE, flow.lastLanguage);
    }

    @Test
    void targetLanguageButtonCallsPickTarget() {
        handler.handle(createCallback("TGT_FR"));
        Assertions.assertEquals(1, flow.pickTargetCalls);
        Assertions.assertEquals(Language.FR, flow.lastLanguage);
    }

    @Test
    void unknownLanguageCodeCallsShowUnknown() {
        handler.handle(createCallback("SRC_XX"));
        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(0, flow.pickSourceCalls);
    }

    @Test
    void unknownPrefixCallsShowUnknown() {
        handler.handle(createCallback("UNKNOWN_DE"));
        Assertions.assertEquals(1, flow.showUnknownCalls);
    }

    /** Кнопка «История» запрашивает последние переводы пользователя. */
    @Test
    void historyButtonShowsHistory() {
        handler.handle(createCallback("HISTORY"));

        Assertions.assertEquals(1, history.recentCalls);
        Assertions.assertEquals(chatId, history.lastAccountId);
        Assertions.assertEquals(HistoryHandler.HISTORY_LIMIT + 1, history.lastLimit);
        Assertions.assertEquals(0, history.lastOffset);
    }

    /** Кнопка страницы запрашивает историю с нужным смещением и границей снимка. */
    @Test
    void historyPageButtonRequestsOffset() {
        handler.handle(createCallback("HISTORY_PAGE_2_42"));

        Assertions.assertEquals(1, history.recentCalls);
        Assertions.assertEquals(2 * HistoryHandler.HISTORY_LIMIT, history.lastOffset);
        Assertions.assertEquals(42, history.lastMaxId);
    }

    /** Старый формат кнопки без границы снимка тоже поддерживается. */
    @Test
    void legacyHistoryPageButtonUsesUnboundedSnapshot() {
        handler.handle(createCallback("HISTORY_PAGE_2"));

        Assertions.assertEquals(1, history.recentCalls);
        Assertions.assertEquals(Long.MAX_VALUE, history.lastMaxId);
    }

    /** Некорректная страница в callback-данных не ломает обработку. */
    @Test
    void invalidHistoryPageShowsUnknown() {
        handler.handle(createCallback("HISTORY_PAGE_bad"));

        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(0, history.recentCalls);
    }

    /** Кнопка «Очистить историю» удаляет историю пользователя. */
    @Test
    void clearHistoryButtonClearsHistory() {
        handler.handle(createCallback("CLEAR_HISTORY"));

        Assertions.assertEquals(1, history.clearCalls);
        Assertions.assertEquals(chatId, history.lastAccountId);
    }

    /** Кнопка «Профиль» показывает профиль. */
    @Test
    void profileButtonShowsProfile() {
        handler.handle(createCallback("PROFILE"));
        Assertions.assertEquals(1, profile.showCalls);
    }

    /** Кнопка «Статистика» показывает статистику. */
    @Test
    void statsButtonShowsStats() {
        handler.handle(createCallback("STATS"));
        Assertions.assertEquals(1, stats.showCalls);
    }

    /** Кнопка выбора языка-цели сохраняет настройку. */
    @Test
    void targetSettingSavesLanguage() {
        handler.handle(createCallback(Ui.SET_TARGET_PREFIX + "DE"));

        Assertions.assertEquals(1, settings.targetCalls);
        Assertions.assertEquals(Language.DE, settings.lastTarget);
    }

    /** Кнопка «Не задан» сбрасывает язык-цель. */
    @Test
    void noneTargetResetsLanguage() {
        handler.handle(createCallback(Ui.SET_TARGET_NONE));

        Assertions.assertEquals(1, settings.targetCalls);
        Assertions.assertNull(settings.lastTarget);
    }

    /** Кнопка режима ответа сохраняет режим. */
    @Test
    void modeSettingSavesMode() {
        handler.handle(createCallback(Ui.SET_MODE_VOICE));

        Assertions.assertEquals(1, settings.modeCalls);
        Assertions.assertEquals(ResponseMode.TEXT_AND_VOICE, settings.lastMode);
    }

    private static class FakeSender implements MessageSender {
        int ackCalls;
        String lastAckId;

        @Override public void ackCallback(String callbackId) { ackCalls++; lastAckId = callbackId; }
        @Override public void send(long chatId, String text) {}
        @Override public void send(long chatId, String text, AppKeyboard keyboard) {}
        @Override public Path download(String fileId) { return null; }
        @Override public void sendDocument(long chatId, String fileName, Path content, String caption, AppKeyboard keyboard) {}
    }

    private static class FakeDialogFlow extends DialogFlow {
        int startSourceChoiceCalls;
        int startTranslationCalls;
        int showHelpCalls;
        int askForTextCalls;
        int showMenuCalls;
        int pickSourceCalls;
        int pickTargetCalls;
        int showUnknownCalls;

        Language lastLanguage;
        Session lastSession;

        FakeDialogFlow() {
            super(null, null);
        }

        @Override public void startSourceChoice(long chatId, Session s) { startSourceChoiceCalls++; lastSession = s; }
        @Override public void startTranslation(long chatId, Session s, Language target) { startTranslationCalls++; lastSession = s; }
        @Override public void showHelp(long chatId) { showHelpCalls++; }
        @Override public void askForText(long chatId, Session s) { askForTextCalls++; lastSession = s; }
        @Override public void showMenu(long chatId, Session s) { showMenuCalls++; lastSession = s; }
        @Override public void pickSource(long chatId, Session s, Language source) { pickSourceCalls++; lastLanguage = source; lastSession = s; }
        @Override public void pickTarget(long chatId, Session s, Language target) { pickTargetCalls++; lastLanguage = target; lastSession = s; }
        @Override public void showUnknown(long chatId) { showUnknownCalls++; }
    }

    private static class FakeProfileHandler extends ProfileHandler {
        int showCalls;

        FakeProfileHandler() {
            super(null, null, null, null, null);
        }

        @Override public void show(long chatId, long accountId) { showCalls++; }
    }

    private static class FakeSettingsHandler extends SettingsHandler {
        int targetCalls;
        int modeCalls;
        Language lastTarget;
        ResponseMode lastMode;

        FakeSettingsHandler() {
            super(null, null, null, null);
        }

        @Override public void selectTarget(long chatId, long accountId, Language target) {
            targetCalls++;
            lastTarget = target;
        }

        @Override public void selectMode(long chatId, long accountId, ResponseMode mode) {
            modeCalls++;
            lastMode = mode;
        }
    }

    private static class FakeStatsHandler extends StatsHandler {
        int showCalls;

        FakeStatsHandler() {
            super(null, null, null);
        }

        @Override public void show(long chatId) { showCalls++; }
    }
}
