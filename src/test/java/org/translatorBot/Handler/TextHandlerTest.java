package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

/**
 * Модульные тесты для {@link TextHandler}.
 */
class TextHandlerTest {

    private final long chatId = 100L;

    private FakeSender sender;
    private FakeTranslator translator;
    private Ui ui;
    private TextHandler handler;
    private Session session;

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

    /** Создает UserMessage с текстом. */
    private UserMessage createMessage(String text) {
        return new UserMessage(chatId, text);
    }

    @Test
    void shouldRejectBlankText() {
        handler.handle(createMessage("   "), session);
        Assertions.assertEquals(ui.emptyText, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
    }

    @Test
    void shouldRejectTooLongText() {
        String longText = "a".repeat(TranslationService.MAX_TEXT_LENGTH + 1);
        handler.handle(createMessage(longText), session);
        Assertions.assertEquals(ui.tooLong, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
    }

    @Test
    void shouldTranslateTextAndChangeState() {
        translator.result = "Hello";
        translator.detectedLanguage = Language.RU;
        handler.handle(createMessage("Привет"), session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals("Привет", translator.lastText);
        Assertions.assertEquals(ui.translation("Hello"), sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
        Assertions.assertEquals(Session.State.IDLE, session.getState());
    }

    @Test
    void shouldHandleTranslationError() {
        translator.shouldThrow = true;
        translator.detectedLanguage = Language.RU;
        handler.handle(createMessage("Привет"), session);
        Assertions.assertEquals(ui.translateErr, sender.lastText);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void shouldRejectWrongLanguage() {
        translator.detectedLanguage = Language.EN;
        handler.handle(createMessage("Hello"), session);

        Assertions.assertTrue(sender.lastText.contains("не совпадает"));
        Assertions.assertEquals(0, translator.translateCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void shouldTranslateWhenLanguageDetectionFails() {
        translator.detectedLanguage = null;
        translator.result = "Привет";
        handler.handle(createMessage("Hello"), session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(ui.translation("Привет"), sender.lastText);
    }

    /** Заглушка MessageSender. */
    private static class FakeSender implements MessageSender {
        int sendCalls;
        String lastText;
        AppKeyboard lastKeyboard;

        @Override
        public void send(long chatId, String text, AppKeyboard keyboard) {
            sendCalls++;
            lastText = text;
            lastKeyboard = keyboard;
        }

        @Override
        public void send(long chatId, String text) {
            send(chatId, text, null);
        }

        @Override
        public void ackCallback(String callbackId) {}

        @Override
        public byte[] download(String fileId) { return new byte[0]; }

        @Override
        public void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard) {}
    }

    /** Заглушка TranslationService. */
    private static class FakeTranslator implements TranslationService {
        int translateCalls;
        String lastText;
        String result = "translated";
        boolean shouldThrow = false;
        Language detectedLanguage = Language.RU;

        @Override
        public String translate(String text, Language from, Language to) throws Exception {
            translateCalls++;
            lastText = text;
            if (shouldThrow) throw new Exception("Translation failed");
            return result;
        }

        @Override
        public Language detect(String text) throws Exception {
            if (shouldThrow) throw new Exception("Detection failed");
            return detectedLanguage;
        }
    }
}