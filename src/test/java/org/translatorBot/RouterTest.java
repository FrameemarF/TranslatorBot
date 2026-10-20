package org.translatorBot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.translatorBot.Service.Translation.TranslationService;

import java.util.ArrayList;
import java.util.List;

/**
 * Модульные тесты для {@link Router}.
 * <p>
 * Используется без Mockito: {@link Sender} подменяется наследником,
 * записывающим отправленные тексты, {@link TranslationService} — собственной
 * реализацией, а входящие {@link Update} конструируются из JSON так, как их
 * присылает Telegram. Тесты проходят через публичный метод
 * {@link Router#consume(List)} и проверяют поведение бота в типичных
 * и граничных сценариях.
 */
class RouterTest {
    private final long chatId = 100L;
    private final ObjectMapper json = new ObjectMapper();

    /** Заглушка {@link Sender}, записывающая тексты отправленных сообщений. */
    private class RecordingSender extends Sender {
        final List<String> texts = new ArrayList<>();

        RecordingSender() {
            super(null); // клиент не нужен: send и ackCallback переопределены
        }

        @Override
        public void send(long chatId, String text) {
            texts.add(text);
        }

        @Override
        public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
            texts.add(text);
        }

        @Override
        public void ackCallback(String callbackId) { }
    }

    /** Заглушка {@link TranslationService} с управляемым поведением. */
    private class FakeTranslator implements TranslationService {
        String reply = "Hello";
        boolean fail = false;
        final List<String> calls = new ArrayList<>();

        @Override
        public String translate(String text, Language from, Language to) {
            calls.add(from + ">" + to + ":" + text);
            if (fail) throw new IllegalStateException("secret details");
            return reply;
        }
    }

    private RecordingSender sender;
    private SessionStore sessions;
    private FakeTranslator translator;
    private Ui ui;
    private Router router;

    /** Настраивает свежие экземпляры зависимостей и {@link Router} перед каждым тестом. */
    @BeforeEach
    void setUp() {
        sender = new RecordingSender();
        sessions = new SessionStore();
        translator = new FakeTranslator();
        ui = new Ui();
        router = new Router(sender, sessions, translator, ui);
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
     * Преобразует JSON-узел в объект {@link Update}.
     *
     * @param root корневой JSON-узел апдейта
     * @return десериализованный апдейт
     * @throws Exception если десериализация не удалась
     */
    private Update parse(ObjectNode root) throws Exception {
        return json.treeToValue(root, Update.class);
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
        router.consume(List.of(parse(root)));
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
        router.consume(List.of(parse(root)));
    }

    /**
     * Возвращает сессию тестового пользователя.
     *
     * @return сессия чата {@link #chatId}
     */
    private Session session() {
        return sessions.get(chatId);
    }

    /** Приводит сессию в состояние готовности к переводу: RU→EN, ожидание текста. */
    private void readyToTranslate() {
        session().setState(Session.State.WAITING_TEXT);
        session().setSource(Language.RU);
        session().setTarget(Language.EN);
    }

    // ---------- тесты ----------

    /** Проверяет, что {@code /start} сбрасывает сессию и показывает главное меню. */
    @Test
    void startResetsSessionAndShowsMenu() throws Exception {
        readyToTranslate();

        sendText("/start");

        Assertions.assertEquals(Session.State.IDLE, session().getState());
        Assertions.assertNull(session().getSource());
        Assertions.assertTrue(sender.texts.contains(ui.start));
    }

    /** Проверяет, что неизвестная команда приводит к сообщению об ошибке. */
    @Test
    void unknownCommandSendsError() throws Exception {
        sendText("/foobar");

        Assertions.assertTrue(sender.texts.contains(ui.unknownCmd));
    }

    /** Проверяет полный успешный сценарий перевода от {@code /start} до результата. */
    @Test
    void fullHappyPath() throws Exception {
        sendText("/start");
        click("TRANSLATE");
        click("SRC_RU");
        click("TGT_EN");
        sendText("Привет");

        Assertions.assertEquals(List.of("RU>EN:Привет"), translator.calls);
        Assertions.assertTrue(sender.texts.contains("✅ Перевод: Hello"));
        Assertions.assertEquals(Session.State.IDLE, session().getState());
    }

    /** Проверяет, что выбор одинаковых языков-источника и цели отклоняется. */
    @Test
    void sameTargetAsSourceIsRejected() throws Exception {
        click("SRC_RU");
        click("TGT_RU");

        Assertions.assertNull(session().getTarget());
        Assertions.assertEquals(Session.State.CHOOSING_TARGET, session().getState());
    }

    /** Проверяет, что текст длиннее лимита не отправляется на перевод. */
    @Test
    void tooLongTextIsRejected() throws Exception {
        readyToTranslate();

        sendText("a".repeat(4001));

        Assertions.assertTrue(translator.calls.isEmpty());
        Assertions.assertTrue(sender.texts.contains(ui.tooLong));
    }

    /** Проверяет, что при сбое перевода пользователь получает дружелюбное сообщение без деталей ошибки. */
    @Test
    void translationFailureShowsFriendlyError() throws Exception {
        readyToTranslate();
        translator.fail = true;

        sendText("Привет");

        Assertions.assertTrue(sender.texts.contains(ui.translateErr));
        for (String t : sender.texts) {
            Assertions.assertFalse(t.contains("secret"));
        }
        Assertions.assertEquals(Session.State.WAITING_TEXT, session().getState());
    }

    /** Проверяет, что при попытке перевода без выбранных языков сценарий начинается заново. */
    @Test
    void staleSessionWithoutLanguagesRestartsLanguageChoice() throws Exception {
        click("MORE"); // старая кнопка после /start: языки не выбраны

        sendText("Привет");

        Assertions.assertTrue(translator.calls.isEmpty());
        Assertions.assertEquals(Session.State.CHOOSING_SOURCE, session().getState());
    }
}