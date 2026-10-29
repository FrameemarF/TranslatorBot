package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.SpeechUnrecognizedException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.io.IOException;

/**
 * Модульные тесты для {@link VoiceHandler}.
 */
class VoiceHandlerTest {
    private final long chatId = 100L;
    private final String fileId = "voice-file-id";

    private RecordingSender sender;
    private FakeSpeech speech;
    private FakeTranslator translator;
    private Ui ui;
    private VoiceHandler handler;
    private Session session;

    @BeforeEach
    void setUp() {
        sender = new RecordingSender();
        speech = new FakeSpeech();
        translator = new FakeTranslator();
        ui = new Ui();
        handler = new VoiceHandler(sender, speech, translator, ui);
        session = new Session();
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);
    }

    private UserMessage voiceMessage(int duration) {
        return new UserMessage(chatId, fileId, duration);
    }

    private UserMessage textMessage(String text) {
        return new UserMessage(chatId, text);
    }

    @Test
    void supportsOnlyVoiceMessages() {
        Assertions.assertTrue(handler.supports(voiceMessage(5)));
        Assertions.assertFalse(handler.supports(textMessage("Привет")));
    }

    @Test
    void successfulVoiceIsTranslatedAndAnswered() {
        translator.detectedLanguage = Language.RU;
        handler.handle(voiceMessage(10), session);
        Assertions.assertEquals(ui.recognized("Привет, мир") + "\n" + ui.translation("Hello, world"), sender.lastText);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void tooLongVoiceIsRejectedWithoutProcessing() {
        handler.handle(voiceMessage(31), session);
        Assertions.assertEquals(ui.voiceTooLong, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCount);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void voiceExactlyAtLimitIsAccepted() {
        translator.detectedLanguage = Language.RU;
        handler.handle(voiceMessage(30), session);
        Assertions.assertEquals(1, sender.downloadCount);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void zeroDurationVoiceIsRejected() {
        handler.handle(voiceMessage(0), session);
        Assertions.assertEquals(ui.speechUnrecognized, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCount);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void unrecognizedSpeechShowsHint() {
        speech.error = new SpeechUnrecognizedException();
        handler.handle(voiceMessage(10), session);
        Assertions.assertEquals(ui.speechUnrecognized, sender.lastText);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void unavailableSpeechServiceShowsUnavailableMessage() {
        speech.error = new IOException("api key missing");
        handler.handle(voiceMessage(10), session);
        Assertions.assertEquals(ui.speechUnavailable, sender.lastText);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void translationFailureShowsTranslateError() {
        translator.fail = true;
        translator.detectedLanguage = Language.RU;
        handler.handle(voiceMessage(10), session);
        Assertions.assertEquals(ui.translateErr, sender.lastText);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void wrongLanguageIsRejected() {
        translator.detectedLanguage = Language.EN;
        handler.handle(voiceMessage(10), session);

        Assertions.assertTrue(sender.lastText.contains("не совпадает"));
        Assertions.assertEquals(0, translator.translateCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void nullDetectedLanguageStillTranslates() {
        translator.detectedLanguage = null;
        handler.handle(voiceMessage(10), session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    private static class RecordingSender implements MessageSender {
        byte[] content = new byte[]{1, 2, 3};
        Exception downloadError;
        int downloadCount = 0;
        String lastText;

        @Override
        public void send(long chatId, String text) {
            lastText = text;
        }

        @Override
        public void send(long chatId, String text, AppKeyboard keyboard) {
            lastText = text;
        }

        @Override
        public byte[] download(String fileId) throws Exception {
            downloadCount++;
            if (downloadError != null) throw downloadError;
            return content;
        }

        @Override public void ackCallback(String callbackId) {}
        @Override public void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard) {}
    }

    private static class FakeSpeech implements SpeechRecognitionService {
        String reply = "Привет, мир";
        Exception error;

        @Override
        public String recognize(byte[] audio, String fileName, Language language) throws Exception {
            if (error != null) throw error;
            return reply;
        }
    }

    private static class FakeTranslator implements TranslationService {
        String reply = "Hello, world";
        boolean fail = false;
        int translateCalls = 0;
        Language detectedLanguage = Language.RU;

        @Override
        public String translate(String text, Language from, Language to) {
            translateCalls++;
            if (fail) throw new IllegalStateException("translation failed");
            return reply;
        }

        @Override
        public Language detect(String text) throws Exception {
            if (fail) throw new Exception("Detection failed");
            return detectedLanguage;
        }
    }
}