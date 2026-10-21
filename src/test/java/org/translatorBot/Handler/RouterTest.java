package org.translatorBot.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Language;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

import java.util.List;

/**
 * Модульные тесты для {@link Router}.
 * <p>
 * Проверяют только маршрутизацию: что {@link Router} вызывает нужный метод
 * у нужного обработчика ({@link CommandHandler}, {@link CallbackHandler},
 * {@link DialogFlow} или {@link MaterialHandler}) в зависимости от типа
 * входящего обновления и состояния сессии. Поведение самих обработчиков
 * проверяется в их собственных тестах.
 */
class RouterTest {
    private final long chatId = 100L;
    private final ObjectMapper json = new ObjectMapper();

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

    // ---------- помощники ----------

    /**
     * Формирует JSON-узел сообщения от Telegram.
     *
     * @param text текст сообщения или {@code null}, если текста нет
     * @return JSON-узел сообщения
     */
    private ObjectNode messageNode(String text) {
        ObjectNode chat = json.createObjectNode();
        chat.put("id", chatId);
        chat.put("type", "private");
        ObjectNode m = json.createObjectNode();
        m.put("message_id", 1);
        m.put("date", 1);
        m.set("chat", chat);
        if (text != null) m.put("text", text);
        return m;
    }

    /**
     * Прогоняет через {@link Router} апдейт с текстовым сообщением.
     *
     * @param text текст сообщения
     * @throws Exception если построение апдейта не удалось
     */
    private void sendText(String text) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("update_id", 1);
        root.set("message", messageNode(text));
        Update update = json.treeToValue(root, Update.class);
        router.consume(List.of(update));
    }

    /**
     * Прогоняет через {@link Router} апдейт с нажатием inline-кнопки.
     *
     * @param data значение {@code callbackData} нажатой кнопки
     * @throws Exception если построение апдейта не удалось
     */
    private void click(String data) throws Exception {
        ObjectNode user = json.createObjectNode();
        user.put("id", 1);
        user.put("first_name", "Test");
        user.put("is_bot", false);
        ObjectNode cb = json.createObjectNode();
        cb.put("id", "cb-1");
        cb.set("from", user);
        cb.put("chat_instance", "ci");
        cb.put("data", data);
        cb.set("message", messageNode(null));
        ObjectNode root = json.createObjectNode();
        root.put("update_id", 2);
        root.set("callback_query", cb);
        Update update = json.treeToValue(root, Update.class);
        router.consume(List.of(update));
    }

    /**
     * Возвращает сессию тестового пользователя.
     *
     * @return сессия чата {@link #chatId}
     */
    private Session session() {
        return sessions.get(chatId);
    }

    // ---------- тесты маршрутизации ----------

    /** Проверяет, что сообщение с командой уходит в {@link CommandHandler}. */
    @Test
    void commandGoesToCommandHandler() throws Exception {
        sendText("/start");

        Assertions.assertEquals(1, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что callback-запрос уходит в {@link CallbackHandler}. */
    @Test
    void callbackGoesToCallbackHandler() throws Exception {
        click("TRANSLATE");

        Assertions.assertEquals(1, fakeCallbacks.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что материал в состоянии WAITING_TEXT уходит в {@link MaterialHandler}. */
    @Test
    void materialGoesToMaterialHandlerWhenWaiting() throws Exception {
        session().setState(Session.State.WAITING_TEXT);
        session().setSource(Language.RU);
        session().setTarget(Language.EN);

        sendText("Привет");

        Assertions.assertEquals(1, fakeMaterialHandler.handleCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeFlow.showUseStartCalls);
    }

    /** Проверяет, что материал без выбранных языков перенаправляется на выбор языка. */
    @Test
    void materialWithoutLanguagesGoesToStartSourceChoice() throws Exception {
        session().setState(Session.State.WAITING_TEXT);
        // source и target не заданы

        sendText("Привет");

        Assertions.assertEquals(1, fakeFlow.startSourceChoiceCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что текст вне сценария (IDLE) показывает подсказку через {@link DialogFlow}. */
    @Test
    void textOutsideScenarioGoesToShowUseStart() throws Exception {
        // Сессия в IDLE по умолчанию

        sendText("Просто текст");

        Assertions.assertEquals(1, fakeFlow.showUseStartCalls);
        Assertions.assertEquals(0, fakeCommands.handleCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что если обработчик не найден, вызывается {@link DialogFlow#showUnsupportedMaterial}. */
    @Test
    void unsupportedMaterialGoesToFlow() throws Exception {
        session().setState(Session.State.WAITING_TEXT);
        session().setSource(Language.RU);
        session().setTarget(Language.EN);
        fakeMaterialHandler.supportsReturn = false;

        sendText("Привет");

        Assertions.assertEquals(1, fakeFlow.showUnsupportedMaterialCalls);
        Assertions.assertEquals(0, fakeMaterialHandler.handleCalls);
    }

    /** Проверяет, что исключение в обработчике не прерывает обработку следующего апдейта. */
    @Test
    void exceptionInHandlerDoesNotStopNextUpdate() throws Exception {
        fakeCallbacks.throwException = true;

        Assertions.assertDoesNotThrow(() -> {
            // Первый апдейт (упадёт), второй — обычный текст вне сценария
            click("TRANSLATE");
            sendText("Просто текст");
        });

        // Второй апдейт всё равно должен обработаться
        Assertions.assertEquals(1, fakeFlow.showUseStartCalls);
    }

    // ---------- Заглушки обработчиков ----------

    /** Заглушка {@link CommandHandler}, считающая вызовы. */
    private class FakeCommandHandler extends CommandHandler {
        int handleCalls;

        FakeCommandHandler() {
            super(null);
        }

        @Override
        public void handle(long chatId, String text, Session session) {
            handleCalls++;
        }
    }

    /** Заглушка {@link CallbackHandler}, считающая вызовы и умеющая бросать исключение. */
    private class FakeCallbackHandler extends CallbackHandler {
        int handleCalls;
        boolean throwException;

        FakeCallbackHandler() {
            super(null, null, null);
        }

        @Override
        public void handle(CallbackQuery q) {
            handleCalls++;
            if (throwException) throw new RuntimeException("Test exception");
        }
    }

    /** Заглушка {@link DialogFlow}, считающая вызовы методов. */
    private class FakeDialogFlow extends DialogFlow {
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
    private class FakeMaterialHandler implements MaterialHandler {
        int handleCalls;
        boolean supportsReturn = true;

        @Override
        public boolean supports(Message message) {
            return supportsReturn;
        }

        @Override
        public void handle(long chatId, Message message, Session session) {
            handleCalls++;
        }
    }
}