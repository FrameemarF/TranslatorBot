package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

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
    private CallbackHandler handler;

    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        sessions = new InMemorySessionStore();
        session = sessions.get(chatId);
        flow = new FakeDialogFlow();
        handler = new CallbackHandler(sender, sessions, flow);
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
    void translateCallsStartSourceChoice() {
        handler.handle(createCallback("TRANSLATE"));
        Assertions.assertEquals(1, flow.startSourceChoiceCalls);
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

    private static class FakeSender implements MessageSender {
        int ackCalls;
        String lastAckId;

        @Override
        public void ackCallback(String callbackId) {
            ackCalls++;
            lastAckId = callbackId;
        }

        @Override public void send(long chatId, String text) {}
        @Override public void send(long chatId, String text, AppKeyboard keyboard) {}
        @Override public byte[] download(String fileId) { return new byte[0]; }
        @Override public void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard) {}
    }

    private static class FakeDialogFlow extends DialogFlow {
        int startSourceChoiceCalls;
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
        @Override public void showHelp(long chatId) { showHelpCalls++; }
        @Override public void askForText(long chatId, Session s) { askForTextCalls++; lastSession = s; }
        @Override public void showMenu(long chatId, Session s) { showMenuCalls++; lastSession = s; }
        @Override public void pickSource(long chatId, Session s, Language source) { pickSourceCalls++; lastLanguage = source; lastSession = s; }
        @Override public void pickTarget(long chatId, Session s, Language target) { pickTargetCalls++; lastLanguage = target; lastSession = s; }
        @Override public void showUnknown(long chatId) { showUnknownCalls++; }
    }
}