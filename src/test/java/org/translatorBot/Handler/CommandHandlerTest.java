package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Session.Session;

/**
 * Модульные тесты для {@link CommandHandler}.
 * <p>
 * Проверяют, что каждая поддерживаемая команда вызывает соответствующий метод
 * у {@link DialogFlow}, а неизвестная команда приводит к показу ошибки и справки.
 * Используется ручная заглушка {@link DialogFlow} без Mockito.
 */
class CommandHandlerTest {

    private final long chatId = 100L;

    private FakeDialogFlow flow;
    private Session session;
    private CommandHandler handler;

    /**
     * Подготавливает тестовое окружение перед каждым тестом:
     * создаёт заглушку {@link DialogFlow}, сессию и сам обработчик.
     */
    @BeforeEach
    void setUp() {
        flow = new FakeDialogFlow();
        session = new Session();
        handler = new CommandHandler(flow);
    }

    /** Проверяет, что команда {@code /start} вызывает {@link DialogFlow#showMenu}. */
    @Test
    void startCallsShowMenu() {
        handler.handle(chatId, "/start", session);

        Assertions.assertEquals(1, flow.showMenuCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
        Assertions.assertSame(session, flow.lastSession);
    }

    /** Проверяет, что команда {@code /help} вызывает {@link DialogFlow#showHelp}. */
    @Test
    void helpCallsShowHelp() {
        handler.handle(chatId, "/help", session);

        Assertions.assertEquals(1, flow.showHelpCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
    }

    /** Проверяет, что команда {@code /translate} вызывает {@link DialogFlow#startSourceChoice}. */
    @Test
    void translateCallsStartSourceChoice() {
        handler.handle(chatId, "/translate", session);

        Assertions.assertEquals(1, flow.startSourceChoiceCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
        Assertions.assertSame(session, flow.lastSession);
    }

    /** Проверяет, что команда {@code /back} вызывает {@link DialogFlow#goBack}. */
    @Test
    void backCallsGoBack() {
        handler.handle(chatId, "/back", session);

        Assertions.assertEquals(1, flow.goBackCalls);
        Assertions.assertEquals(chatId, flow.lastChatId);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что неизвестная команда вызывает показ ошибки и справки,
     * а другие методы диалога не затрагиваются.
     */
    @Test
    void unknownCommandCallsShowUnknownAndShowHelp() {
        handler.handle(chatId, "/foobar", session);

        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(1, flow.showHelpCalls);
        Assertions.assertEquals(0, flow.showMenuCalls);
        Assertions.assertEquals(0, flow.startSourceChoiceCalls);
        Assertions.assertEquals(0, flow.goBackCalls);
    }

    /**
     * Проверяет, что аргументы после команды (через пробел) игнорируются,
     * и выполняется сама команда.
     */
    @Test
    void commandWithArgumentsIgnoresExtraText() {
        handler.handle(chatId, "/start some_text", session);

        Assertions.assertEquals(1, flow.showMenuCalls);
    }

    /** Заглушка {@link DialogFlow}, запоминающая вызовы методов. */
    private class FakeDialogFlow extends DialogFlow {
        int showMenuCalls;
        int showHelpCalls;
        int startSourceChoiceCalls;
        int goBackCalls;
        int showUnknownCalls;

        long lastChatId;
        Session lastSession;

        FakeDialogFlow() {
            super(null, null);
        }

        @Override
        public void showMenu(long chatId, Session s) {
            showMenuCalls++;
            lastChatId = chatId;
            lastSession = s;
        }

        @Override
        public void showHelp(long chatId) {
            showHelpCalls++;
            lastChatId = chatId;
        }

        @Override
        public void startSourceChoice(long chatId, Session s) {
            startSourceChoiceCalls++;
            lastChatId = chatId;
            lastSession = s;
        }

        @Override
        public void goBack(long chatId, Session s) {
            goBackCalls++;
            lastChatId = chatId;
            lastSession = s;
        }

        @Override
        public void showUnknown(long chatId) {
            showUnknownCalls++;
        }
    }
}