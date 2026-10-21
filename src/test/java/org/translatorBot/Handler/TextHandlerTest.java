package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.translatorBot.Language;
import org.translatorBot.Sender;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

/**
 * Модульные тесты для {@link TextHandler}.
 * <p>
 * Проверяют валидацию входящего текста, успешный перевод с изменением
 * состояния сессии и корректную обработку ошибок сервиса перевода.
 */
class TextHandlerTest {

    private final long chatId = 100L;

    private FakeSender sender;
    private FakeTranslator translator;
    private Ui ui;
    private TextHandler handler;

    private Session session;

    /**
     * Подготавливает тестовое окружение: заглушки Sender, TranslationService,
     * реальный Ui, сессию и сам TextHandler.
     */
    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        translator = new FakeTranslator();
        ui = new Ui();
        handler = new TextHandler(sender, translator, ui);

        session = new Session();
        session.setSource(Language.RU);
        session.setTarget(Language.EN);
        session.setState(Session.State.WAITING_TEXT);
    }

    /**
     * Создаёт объект {@link Message} с заданным текстом.
     *
     * @param text текст сообщения
     * @return сообщение от Telegram
     */
    private Message createMessage(String text) {
        Message msg = new Message();
        msg.setText(text);
        return msg;
    }

    /**
     * Проверяет, что пустое сообщение (или из одних пробелов) отклоняется,
     * и сервис перевода не вызывается.
     */
    @Test
    void shouldRejectBlankText() {
        Message msg = createMessage("   ");

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.emptyText, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    /**
     * Проверяет, что текст слишком длинный отклоняется до вызова сервиса.
     */
    @Test
    void shouldRejectTooLongText() {
        String longText = "a".repeat(TranslationService.MAX_TEXT_LENGTH + 1);
        Message msg = createMessage(longText);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.tooLong, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
    }

    /**
     * Проверяет успешный сценарий: текст переводится, состояние меняется на IDLE,
     * а пользователю отправляется результат с клавиатурой.
     */
    @Test
    void shouldTranslateTextAndChangeState() {
        translator.result = "Hello";
        Message msg = createMessage("Привет");

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals("Привет", translator.lastText);
        Assertions.assertEquals(Language.RU, translator.lastFrom);
        Assertions.assertEquals(Language.EN, translator.lastTo);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.translation("Hello"), sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);

        Assertions.assertEquals(Session.State.IDLE, session.getState());
    }

    /**
     * Проверяет, что при ошибке сервиса перевода пользователь получает
     * дружелюбное сообщение, а состояние сессии не меняется.
     */
    @Test
    void shouldHandleTranslationError() {
        translator.shouldThrow = true;
        Message msg = createMessage("Привет");

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.translateErr, sender.lastText);

        // Состояние не должно измениться
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
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
        public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
            sendCalls++;
            lastText = text;
            lastKeyboard = keyboard;
        }
    }

    /** Заглушка {@link TranslationService}. */
    private class FakeTranslator implements TranslationService {
        int translateCalls;
        String lastText;
        Language lastFrom;
        Language lastTo;

        String result = "translated";
        boolean shouldThrow = false;

        @Override
        public String translate(String text, Language from, Language to) throws Exception {
            translateCalls++;
            lastText = text;
            lastFrom = from;
            lastTo = to;
            if (shouldThrow) throw new Exception("Translation failed");
            return result;
        }
    }
}