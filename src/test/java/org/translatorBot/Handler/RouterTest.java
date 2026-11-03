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

import java.util.List;

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
    private FakeMaterialHandler fakeMaterialHandler;

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
        fakeMaterialHandler = new FakeMaterialHandler();

        router = new Router(sessions, fakeCommands, fakeCallbacks, fakeFlow,
                List.of(fakeMaterialHandler));
    }

    // ---------- тесты маршрутизации ----------

    /** Проверяет, что сообщение с командой уходит в {@link CommandHandler}. */
    @Test
    void commandGoesToCommandHandler() {
        UserMessage msg = new UserMessage(chatId, "/start");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что callback-запрос уходит в {@link CallbackHandler}. */
    @Test
    void callbackGoesToCallbackHandler() {
        UserCallback callback = new UserCallback(chatId, "cb-1", "TRANSLATE");
        router.handleCallback(callback);

        Assertions.assertEquals(1, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что материал в состоянии WAITING_TEXT уходит в {@link MaterialHandler}. */
    @Test
    void materialGoesToMaterialHandlerWhenWaiting() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);

        UserMessage msg = new UserMessage(chatId, "Привет");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeMaterialHandler.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeFlow.showUseStartCalls);
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
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что текст вне сценария (IDLE) показывает подсказку через {@link DialogFlow}. */
    @Test
    void textOutsideScenarioGoesToShowUseStart() {
        // Сессия в IDLE по умолчанию
        UserMessage msg = new UserMessage(chatId, "Просто текст");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeFlow.showUseStartCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что если обработчик не найден, вызывается {@link DialogFlow#showUnsupportedMaterial}. */
    @Test
    void unsupportedMaterialGoesToFlow() {
        Session session = sessions.get(chatId);
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);
        fakeMaterialHandler.supportsReturn = false;

        UserMessage msg = new UserMessage(chatId, "Привет");
        router.handleMessage(msg);

        Assertions.assertEquals(1, fakeFlow.showUnsupportedMaterialCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    // ---------- Заглушки обработчиков ----------

    /** Заглушка {@link CommandHandler}, считающая вызовы. */
    private static class FakeCommandHandler extends CommandHandler {
        int handleCalls;

        FakeCommandHandler() {
            super(null);
        }

        @Override
        public void handle(long chatId, String text, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link CallbackHandler}, считающая вызовы. */
    private static class FakeCallbackHandler extends CallbackHandler {
        int handleCalls;

        FakeCallbackHandler() {
            super(null, null, null);
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
        int showUnsupportedMaterialCalls;

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

        @Override
        public void showUnsupportedMaterial(long chatId) {
            showUnsupportedMaterialCalls++;
        }
    }

    /** Заглушка {@link MaterialHandler} с управляемым {@code supports}. */
    private static class FakeMaterialHandler implements MaterialHandler {
        int handleCalls;
        boolean supportsReturn = true;

        @Override
        public boolean supports(UserMessage message) {
            return supportsReturn;
        }

        @Override
        public void handle(UserMessage message, Session session) {
            handleCalls++;
        }
    }
}