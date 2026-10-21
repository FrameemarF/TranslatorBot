package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.translatorBot.Language;
import org.translatorBot.Sender;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Модульные тесты для {@link DialogFlow}.
 * <p>
 * Проверяют изменение состояния {@link Session} и отправку корректных
 * сообщений/клавиатур на каждом шаге сценария. Используется ручная
 * заглушка {@link Sender}, записывающая вызовы.
 */
class DialogFlowTest {

    private final long chatId = 100L;

    private FakeSender sender;
    private Ui ui;
    private Session session;
    private DialogFlow flow;

    /**
     * Подготавливает тестовое окружение: заглушку Sender, реальный Ui,
     * сессию и сам DialogFlow.
     */
    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        ui = new Ui();
        session = new Session();
        flow = new DialogFlow(sender, ui);
    }

    /** Проверяет, что {@link DialogFlow#showMenu} сбрасывает сессию и отправляет приветствие. */
    @Test
    void showMenuResetsSessionAndSendsStartText() {
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        flow.showMenu(chatId, session);

        Assertions.assertEquals(Session.State.IDLE, session.getState());
        Assertions.assertNull(session.getSource());
        Assertions.assertNull(session.getTarget());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.start, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Проверяет, что {@link DialogFlow#showHelp} отправляет текст справки без клавиатуры. */
    @Test
    void showHelpSendsHelpText() {
        flow.showHelp(chatId);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.help, sender.lastText);
        Assertions.assertNull(sender.lastKeyboard);
    }

    /** Проверяет, что {@link DialogFlow#showUnknown} отправляет сообщение об ошибке. */
    @Test
    void showUnknownSendsUnknownText() {
        flow.showUnknown(chatId);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.unknownCmd, sender.lastText);
    }

    /** Проверяет, что {@link DialogFlow#showUseStart} отправляет подсказку и меню. */
    @Test
    void showUseStartSendsTextAndMenu() {
        flow.showUseStart(chatId);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.useStart, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Проверяет, что {@link DialogFlow#showUnsupportedMaterial} отправляет сообщение об ошибке. */
    @Test
    void showUnsupportedMaterialSendsText() {
        flow.showUnsupportedMaterial(chatId);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.unsupportedMaterial, sender.lastText);
    }

    /** Проверяет, что {@link DialogFlow#startSourceChoice} сбрасывает сессию и предлагает выбор языка. */
    @Test
    void startSourceChoiceResetsSessionAndSendsSourceKeyboard() {
        session.setSource(Language.RU);

        flow.startSourceChoice(chatId, session);

        Assertions.assertEquals(Session.State.CHOOSING_SOURCE, session.getState());
        Assertions.assertNull(session.getSource());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.chooseSourceLang, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Проверяет, что {@link DialogFlow#pickSource} сохраняет язык и переводит на выбор цели. */
    @Test
    void pickSourceSavesSourceAndSendsTargetKeyboard() {
        flow.pickSource(chatId, session, Language.DE);

        Assertions.assertEquals(Language.DE, session.getSource());
        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.chooseTargetLang, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /**
     * Проверяет, что {@link DialogFlow#pickTarget} сохраняет язык и просит текст,
     * если языки разные.
     */
    @Test
    void pickTargetWithDifferentLanguagesSavesAndAsksForText() {
        session.setSource(Language.DE);

        flow.pickTarget(chatId, session, Language.FR);

        Assertions.assertEquals(Language.FR, session.getTarget());
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.askText, sender.lastText);
        Assertions.assertNull(sender.lastKeyboard);
    }

    /**
     * Проверяет, что {@link DialogFlow#pickTarget} отклоняет выбор, если
     * язык-цель совпадает с языком-источником.
     */
    @Test
    void pickTargetWithSameLanguageRejectsAndSendsWarning() {
        session.setSource(Language.DE);
        session.setState(Session.State.CHOOSING_TARGET);

        flow.pickTarget(chatId, session, Language.DE);

        Assertions.assertNull(session.getTarget()); // Не сохранился
        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.sameLanguages, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Проверяет, что {@link DialogFlow#askForText} меняет состояние и просит текст. */
    @Test
    void askForTextChangesStateAndSendsText() {
        flow.askForText(chatId, session);

        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.askText, sender.lastText);
    }

    /**
     * Проверяет {@link DialogFlow#goBack} из состояния {@link Session.State#WAITING_TEXT}:
     * должен вернуть на выбор языка-цели.
     */
    @Test
    void goBackFromWaitingTextGoesToTargetChoice() {
        session.setState(Session.State.WAITING_TEXT);

        flow.goBack(chatId, session);

        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.chooseTargetLang, sender.lastText);
    }

    /**
     * Проверяет {@link DialogFlow#goBack} из состояния {@link Session.State#CHOOSING_TARGET}:
     * должен вернуть на выбор языка-источника.
     */
    @Test
    void goBackFromChoosingTargetGoesToSourceChoice() {
        session.setState(Session.State.CHOOSING_TARGET);

        flow.goBack(chatId, session);

        Assertions.assertEquals(Session.State.CHOOSING_SOURCE, session.getState());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.chooseSourceLang, sender.lastText);
    }

    /**
     * Проверяет {@link DialogFlow#goBack} из любого другого состояния (например, IDLE):
     * должен сбросить сессию и вернуть в главное меню.
     */
    @Test
    void goBackFromIdleResetsAndGoesToMenu() {
        session.setState(Session.State.IDLE);
        session.setSource(Language.RU);

        flow.goBack(chatId, session);

        Assertions.assertEquals(Session.State.IDLE, session.getState());
        Assertions.assertNull(session.getSource());
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.backToMenu, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Заглушка {@link Sender}, запоминающая параметры вызова. */
    private class FakeSender extends Sender {
        int sendCalls;
        String lastText;
        InlineKeyboardMarkup lastKeyboard;

        FakeSender() {
            super(null);
        }

        @Override
        public void send(long chatId, String text) {
            sendCalls++;
            lastText = text;
            lastKeyboard = null;
        }

        @Override
        public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
            sendCalls++;
            lastText = text;
            lastKeyboard = keyboard;
        }
    }
}