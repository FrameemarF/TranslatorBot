package org.translatorBot.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.translatorBot.Language;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.Session;
import org.translatorBot.Session.SessionStore;

/**
 * Модульные тесты для {@link CallbackHandler}.
 * <p>
 * Проверяют, что обработчик корректно подтверждает callback у Telegram
 * и направляет данные кнопки в нужный метод {@link DialogFlow},
 * включая разбор кнопок выбора языка. Используются ручные заглушки.
 */
class CallbackHandlerTest {

    private final long chatId = 100L;
    private final String callbackId = "cb-123";
    private final ObjectMapper json = new ObjectMapper();

    private FakeSender sender;
    private SessionStore sessions;
    private Session session;
    private FakeDialogFlow flow;
    private CallbackHandler handler;

    /**
     * Подготавливает тестовое окружение: заглушки, сессию и обработчик.
     */
    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        sessions = new InMemorySessionStore();
        session = sessions.get(chatId);
        flow = new FakeDialogFlow();
        handler = new CallbackHandler(sender, sessions, flow);
    }

    /**
     * Создаёт объект {@link CallbackQuery} с заданными данными кнопки,
     * десериализуя его из JSON (как это делает Telegram).
     *
     * @param data данные кнопки
     * @return готовый запрос
     * @throws Exception если десериализация не удалась
     */
    private CallbackQuery createQuery(String data) throws Exception {
        ObjectNode user = json.createObjectNode();
        user.put("id", 1);
        user.put("first_name", "Test");
        user.put("is_bot", false);

        ObjectNode chat = json.createObjectNode();
        chat.put("id", chatId);
        chat.put("type", "private");

        ObjectNode message = json.createObjectNode();
        message.put("message_id", 1);
        message.put("date", 1);
        message.set("chat", chat);

        ObjectNode cb = json.createObjectNode();
        cb.put("id", callbackId);
        cb.set("from", user);
        cb.put("chat_instance", "ci");
        cb.put("data", data);
        cb.set("message", message);

        return json.treeToValue(cb, CallbackQuery.class);
    }

    /**
     * Проверяет, что при любом нажатии кнопки сначала отправляется подтверждение
     * (убираются «часики»).
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void shouldAlwaysAckCallback() throws Exception {
        handler.handle(createQuery("TRANSLATE"));
        Assertions.assertEquals(1, sender.ackCalls);
        Assertions.assertEquals(callbackId, sender.lastAckId);
    }

    /**
     * Проверяет, что кнопка {@code TRANSLATE} вызывает {@link DialogFlow#startSourceChoice}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void translateCallsStartSourceChoice() throws Exception {
        handler.handle(createQuery("TRANSLATE"));
        Assertions.assertEquals(1, flow.startSourceChoiceCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что кнопка {@code CHANGE_LANG} вызывает {@link DialogFlow#startSourceChoice}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void changeLangCallsStartSourceChoice() throws Exception {
        handler.handle(createQuery("CHANGE_LANG"));
        Assertions.assertEquals(1, flow.startSourceChoiceCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что кнопка {@code HELP} вызывает {@link DialogFlow#showHelp}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void helpCallsShowHelp() throws Exception {
        handler.handle(createQuery("HELP"));
        Assertions.assertEquals(1, flow.showHelpCalls);
    }

    /**
     * Проверяет, что кнопка {@code MORE} вызывает {@link DialogFlow#askForText}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void moreCallsAskForText() throws Exception {
        handler.handle(createQuery("MORE"));
        Assertions.assertEquals(1, flow.askForTextCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что кнопка {@code MENU} вызывает {@link DialogFlow#showMenu}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void menuCallsShowMenu() throws Exception {
        handler.handle(createQuery("MENU"));
        Assertions.assertEquals(1, flow.showMenuCalls);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что кнопка выбора языка-источника (например, {@code SRC_DE})
     * правильно парсится и вызывает {@link DialogFlow#pickSource}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void sourceLanguageButtonCallsPickSource() throws Exception {
        handler.handle(createQuery("SRC_DE"));

        Assertions.assertEquals(1, flow.pickSourceCalls);
        Assertions.assertEquals(Language.DE, flow.lastLanguage);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что кнопка выбора языка-цели (например, {@code TGT_FR})
     * правильно парсится и вызывает {@link DialogFlow#pickTarget}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void targetLanguageButtonCallsPickTarget() throws Exception {
        handler.handle(createQuery("TGT_FR"));

        Assertions.assertEquals(1, flow.pickTargetCalls);
        Assertions.assertEquals(Language.FR, flow.lastLanguage);
        Assertions.assertSame(session, flow.lastSession);
    }

    /**
     * Проверяет, что неизвестный код языка (например, {@code SRC_XX})
     * приводит к вызову {@link DialogFlow#showUnknown}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void unknownLanguageCodeCallsShowUnknown() throws Exception {
        handler.handle(createQuery("SRC_XX"));

        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(0, flow.pickSourceCalls);
    }

    /**
     * Проверяет, что неизвестный префикс (например, {@code UNKNOWN_DE})
     * приводит к вызову {@link DialogFlow#showUnknown}.
     *
     * @throws Exception если построение запроса не удалось
     */
    @Test
    void unknownPrefixCallsShowUnknown() throws Exception {
        handler.handle(createQuery("UNKNOWN_DE"));

        Assertions.assertEquals(1, flow.showUnknownCalls);
        Assertions.assertEquals(0, flow.pickSourceCalls);
        Assertions.assertEquals(0, flow.pickTargetCalls);
    }

    /** Заглушка {@link Sender}, запоминающая вызовы подтверждения callback. */
    private class FakeSender extends Sender {
        int ackCalls;
        String lastAckId;

        FakeSender() {
            super(null);
        }

        @Override
        public void ackCallback(String callbackId) {
            ackCalls++;
            lastAckId = callbackId;
        }
    }

    /** Заглушка {@link DialogFlow}, запоминающая вызовы методов. */
    private class FakeDialogFlow extends DialogFlow {
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

        @Override
        public void startSourceChoice(long chatId, Session s) {
            startSourceChoiceCalls++;
            lastSession = s;
        }

        @Override
        public void showHelp(long chatId) {
            showHelpCalls++;
        }

        @Override
        public void askForText(long chatId, Session s) {
            askForTextCalls++;
            lastSession = s;
        }

        @Override
        public void showMenu(long chatId, Session s) {
            showMenuCalls++;
            lastSession = s;
        }

        @Override
        public void pickSource(long chatId, Session s, Language source) {
            pickSourceCalls++;
            lastLanguage = source;
            lastSession = s;
        }

        @Override
        public void pickTarget(long chatId, Session s, Language target) {
            pickTargetCalls++;
            lastLanguage = target;
            lastSession = s;
        }

        @Override
        public void showUnknown(long chatId) {
            showUnknownCalls++;
        }
    }
}