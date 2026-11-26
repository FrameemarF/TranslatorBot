package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.User.FakeUserService;

/**
 * Модульные тесты для {@link Router}.
 * <p>
 * Проверяют только маршрутизацию: что {@link Router} вызывает нужный метод
 * у нужного обработчика в зависимости от типа входящей модели и состояния
 * сессии, а также регистрирует пользователя.
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
    private FakeUserService users;

    private Router router;

    /** Настраивает заглушки обработчиков и создаёт {@link Router} перед каждым тестом. */
    @BeforeEach
    void setUp() {
        sessions = new InMemorySessionStore();
        fakeCommands = new FakeCommandHandler();
        fakeCallbacks = new FakeCallbackHandler();
        fakeFlow = new FakeDialogFlow();
        fakeText = new FakeTextHandler();
        fakeDocuments = new FakeDocumentHandler();
        fakeVoice = new FakeVoiceHandler();
        users = new FakeUserService();

        router = new Router(sessions, fakeCommands, fakeCallbacks, fakeFlow,
                fakeText, fakeDocuments, fakeVoice, Platform.TELEGRAM, users);
    }

    /** Сообщение с командой уходит в {@link CommandHandler}. */
    @Test
    void commandGoesToCommandHandler() {
        router.handleMessage(new UserMessage(chatId, "/start"));

        Assertions.assertEquals(1, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    /** В команду передаётся идентификатор аккаунта пользователя. */
    @Test
    void commandReceivesAccountId() {
        router.handleMessage(new UserMessage(chatId, 777L, "/history"));

        Assertions.assertEquals(777L, fakeCommands.lastAccountId);
    }

    /** Каждое входящее сообщение регистрирует пользователя. */
    @Test
    void messageRegistersUser() {
        router.handleMessage(new UserMessage(chatId, 777L, "/start").withUserName("Илья"));

        Assertions.assertEquals(1, users.touchCalls);
    }

    /** Callback-запрос уходит в {@link CallbackHandler}. */
    @Test
    void callbackGoesToCallbackHandler() {
        router.handleCallback(new UserCallback(chatId, "cb-1", "TRANSLATE"));

        Assertions.assertEquals(1, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
    }

    /** Текст в состоянии WAITING_TEXT уходит в {@link TextHandler}. */
    @Test
    void textGoesToTextHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        router.handleMessage(new UserMessage(chatId, "Привет"));

        Assertions.assertEquals(1, fakeText.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeFlow.showUseStartCalls);
    }

    /** Файл в состоянии WAITING_TEXT уходит в {@link DocumentHandler}. */
    @Test
    void documentGoesToDocumentHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        router.handleMessage(new UserMessage(chatId, "doc-1", "test.txt", 100L));

        Assertions.assertEquals(1, fakeDocuments.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    /** Голосовое в состоянии WAITING_TEXT уходит в {@link VoiceHandler}. */
    @Test
    void voiceGoesToVoiceHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        router.handleMessage(new UserMessage(chatId, "voice-1", 5));

        Assertions.assertEquals(1, fakeVoice.handleCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    /** Материал без выбранных языков перенаправляется на выбор языка. */
    @Test
    void materialWithoutLanguagesGoesToStartSourceChoice() {
        sessions.get(chatId).setState(Session.State.WAITING_TEXT);

        router.handleMessage(new UserMessage(chatId, "Привет"));

        Assertions.assertEquals(1, fakeFlow.startSourceChoiceCalls);
        Assertions.assertEquals(0, fakeText.handleCalls);
    }

    /** Текст вне сценария (IDLE) показывает подсказку через {@link DialogFlow}. */
    @Test
    void textOutsideScenarioGoesToShowUseStart() {
        router.handleMessage(new UserMessage(chatId, "Просто текст"));

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
            super(null, null, null, null, null, null, null);
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
            super(null, null, null, null, null, null, null, null, null);
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

    /** Заглушка {@link TextHandler}. */
    private static class FakeTextHandler extends TextHandler {
        int handleCalls;

        FakeTextHandler() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link DocumentHandler}. */
    private static class FakeDocumentHandler extends DocumentHandler {
        int handleCalls;

        FakeDocumentHandler() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link VoiceHandler}. */
    private static class FakeVoiceHandler extends VoiceHandler {
        int handleCalls;

        FakeVoiceHandler() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }
}
