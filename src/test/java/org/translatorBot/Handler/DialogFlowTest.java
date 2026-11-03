package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

/**
 * Модульные тесты для {@link DialogFlow}.
 */
class DialogFlowTest {

    private final long chatId = 100L;

    private FakeSender sender;
    private Ui ui;
    private Session session;
    private DialogFlow flow;

    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        ui = new Ui();
        session = new Session();
        flow = new DialogFlow(sender, ui);
    }

    @Test
    void showMenuResetsSessionAndSendsStartText() {
        session.setState(Session.State.WAITING_TEXT);
        flow.showMenu(chatId, session);
        Assertions.assertEquals(Session.State.IDLE, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    @Test
    void showHelpSendsHelpText() {
        flow.showHelp(chatId);
        Assertions.assertEquals(ui.help, sender.lastText);
        Assertions.assertNull(sender.lastKeyboard);
    }

    @Test
    void showUnknownSendsUnknownText() {
        flow.showUnknown(chatId);
        Assertions.assertEquals(ui.unknownCmd, sender.lastText);
    }

    @Test
    void showUseStartSendsTextAndMenu() {
        flow.showUseStart(chatId);
        Assertions.assertEquals(ui.useStart, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    @Test
    void showUnsupportedMaterialSendsText() {
        flow.showUnsupportedMaterial(chatId);
        Assertions.assertEquals(ui.unsupportedMaterial, sender.lastText);
    }

    @Test
    void startSourceChoiceResetsSessionAndSendsSourceKeyboard() {
        flow.startSourceChoice(chatId, session);
        Assertions.assertEquals(Session.State.CHOOSING_SOURCE, session.getState());
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    @Test
    void pickSourceSavesSourceAndSendsTargetKeyboard() {
        flow.pickSource(chatId, session, Language.DE);
        Assertions.assertEquals(Language.DE, session.getSource());
        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session.getState());
    }

    @Test
    void pickTargetWithDifferentLanguagesSavesAndAsksForText() {
        session.setSource(Language.DE);
        flow.pickTarget(chatId, session, Language.FR);
        Assertions.assertEquals(Language.FR, session.getTarget());
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void pickTargetWithSameLanguageRejectsAndSendsWarning() {
        session.setSource(Language.DE);
        session.setState(Session.State.CHOOSING_TARGET);
        flow.pickTarget(chatId, session, Language.DE);
        Assertions.assertNull(session.getTarget());
        Assertions.assertEquals(ui.sameLanguages, sender.lastText);
    }

    @Test
    void askForTextChangesStateAndSendsText() {
        flow.askForText(chatId, session);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
        Assertions.assertEquals(ui.askText, sender.lastText);
    }

    @Test
    void goBackFromWaitingTextGoesToTargetChoice() {
        session.setState(Session.State.WAITING_TEXT);
        flow.goBack(chatId, session);
        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session.getState());
    }

    @Test
    void goBackFromChoosingTargetGoesToSourceChoice() {
        session.setState(Session.State.CHOOSING_TARGET);
        flow.goBack(chatId, session);
        Assertions.assertEquals(Session.State.CHOOSING_SOURCE, session.getState());
    }

    @Test
    void goBackFromIdleResetsAndGoesToMenu() {
        session.setState(Session.State.IDLE);
        flow.goBack(chatId, session);
        Assertions.assertEquals(Session.State.IDLE, session.getState());
        Assertions.assertEquals(ui.backToMenu, sender.lastText);
    }

    private static class FakeSender implements MessageSender {
        int sendCalls;
        String lastText;
        AppKeyboard lastKeyboard;

        @Override
        public void send(long chatId, String text) {
            sendCalls++;
            lastText = text;
            lastKeyboard = null;
        }

        @Override
        public void send(long chatId, String text, AppKeyboard keyboard) {
            sendCalls++;
            lastText = text;
            lastKeyboard = keyboard;
        }

        @Override public void ackCallback(String callbackId) {}
        @Override public byte[] download(String fileId) { return new byte[0]; }
        @Override public void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard) {}
    }
}