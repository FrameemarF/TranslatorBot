package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

/**
 * Модульные тесты для {@link Router}.
 * <p>
 * Проверяют только маршрутизацию: что {@link Router} вызывает нужный метод
 * у нужного обработчика в зависимости от типа входящей модели и состояния сессии.
 * Никаких зависимостей от Telegram API (Update, Message) здесь нет.
 */
class RouterTest {
    private final long chatId = 100L;

    private SessionStore sessions;
    private FakeCommandHandler fakeCommands;
    private FakeCallbackHandler fakeCallbacks;
    private FakeDialogFlow fakeFlow;
    private FakeTextHandler fakeText;
    private FakeDocumentHandler fakeDocuments;
    private FakeVoiceHandler fakeVoice;

    private Router router;

    /**
     * Настраивает заглушки обработчиков и создаёт {@link Router} перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        sessions = new InMemorySessionStore();
        fakeCommands = new FakeCommandHandler();
        fakeCallbacks = new FakeCallbackHandler();
        fakeFlow = new FakeDialogFlow();
        fakeText = new FakeTextHandler();
        fakeDocuments = new FakeDocumentHandler();
        fakeVoice = new FakeVoiceHandler();

        router = new Router(sessions, fakeCommands, fakeCallbacks, fakeFlow,
                fakeText, fakeDocuments, fakeVoice);
    }

    // ---------- тесты маршрутизации ----------

    /** Проверяет, что сообщение с командой уходит в {@link CommandHandler}. */
    @Test
    void commandGoesToCommandHandler() {
        UserMessage msg = new UserMessage(chatId, "/start");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
        Assertions.assertEquals(0, fakeDocuments.handleCalls);
        Assertions.assertEquals(0, fakeVoice.handleCalls);
    }

    /** Проверяет, что в команду передаётся идентификатор аккаунта пользователя. */
    @Test
    void commandReceivesAccountId() {
        UserMessage msg = new UserMessage(chatId, 777L, "/history");
        router.handleMessage(msg);

        Assertions.assertEquals(777L, fakeCommands.lastAccountId);
    }

    /** Проверяет, что callback-запрос уходит в {@link CallbackHandler}. */
    @Test
    void callbackGoesToCallbackHandler() {
        UserCallback callback = new UserCallback(chatId, "cb-1", "TRANSLATE");
        router.handleCallback(callback);

        Assertions.assertEquals(1, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
    }

    /** Проверяет, что текст в состоянии WAITING_TEXT уходит в {@link TextHandler}. */
    @Test
    void textGoesToTextHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        UserMessage msg = new UserMessage(chatId, "Привет");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeText.handleCalls);
        Assertions.assertEquals(0, fakeDocuments.handleCalls);
        Assertions.assertEquals(0, fakeVoice.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeFlow.showUseStartCalls);
    }

    /** Проверяет, что документ в состоянии WAITING_TEXT уходит в {@link DocumentHandler}. */
    @Test
    void documentGoesToDocumentHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        UserMessage msg = new UserMessage(chatId, "doc-1", "test.txt", 100L);
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeDocuments.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
        Assertions.assertEquals(0, fakeVoice.handleCalls);
    }

    /** Проверяет, что голосовое сообщение уходит в {@link VoiceHandler}. */
    @Test
    void voiceGoesToVoiceHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        UserMessage msg = new UserMessage(chatId, "voice-1", 5);
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeVoice.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
        Assertions.assertEquals(0, fakeDocuments.handleCalls);
    }

    /** Проверяет, что материал без выбранных языков перенаправляется на выбор языка. */
    @Test
    void materialWithoutLanguagesGoesToStartSourceChoice() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        // source и target не заданы

        UserMessage msg = new UserMessage(chatId, "Привет");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeFlow.startSourceChoiceCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    /** Проверяет, что текст вне сценария (IDLE) показывает подсказку через {@link DialogFlow}. */
    @Test
    void textOutsideScenarioGoesToShowUseStart() {
        // Сессия в IDLE по умолчанию
        UserMessage msg = new UserMessage(chatId, "Просто текст");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeFlow.showUseStartCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    // ---------- Заглушки обработчиков ----------

    /** Заглушка {@link CommandHandler}, считающая вызовы. */
    private static class FakeCommandHandler extends CommandHandler {
        int handleCalls;
        long lastAccountId;

        FakeCommandHandler() {
            super(null, null);
        }

        @Override
        public void handle(long chatId, long accountId, String text, Session session) {
            handleCalls++;
            lastAccountId = accountId;
        }
    }

    /** Заглушка {@link CallbackHandler}, считающая вызовы. */
    private static class FakeCallbackHandler extends CallbackHandler {
        int handleCalls;

        FakeCallbackHandler() {
            super(null, null, null, null);
        }

        @Override
        public void handle(UserCallback q) {
            handleCalls++;
        }
    }

    /** Заглушка {@link DialogFlow}, считающая вызовы методов. */
    private static class FakeDialogFlow extends DialogFlow {
        int startSourceChoiceCalls;
        int showUseStartCalls;

        FakeDialogFlow() {
            super(null, null);
        }

        @Override
        public void startSourceChoice(long chatId, Session s) {
            startSourceChoiceCalls++;
        }

        @Override
        public void showUseStart(long chatId) {
            showUseStartCalls++;
        }
    }

    /** Заглушка {@link TextHandler}, считающая вызовы. */
    private static class FakeTextHandler extends TextHandler {
        int handleCalls;

        FakeTextHandler() {
            super(null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link DocumentHandler}, считающая вызовы. */
    private static class FakeDocumentHandler extends DocumentHandler {
        int handleCalls;

        FakeDocumentHandler() {
            super(null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link VoiceHandler}, считающая вызовы. */
    private static class FakeVoiceHandler extends VoiceHandler {
        int handleCalls;

        FakeVoiceHandler() {
            super(null, null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }
}
