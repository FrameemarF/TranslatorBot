package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.nio.charset.StandardCharsets;

/**
 * Модульные тесты для {@link DocumentHandler}.
 */
class DocumentHandlerTest {

    private final long chatId = 100L;

    private FakeSender sender;
    private FakeTranslator translator;
    private Ui ui;
    private DocumentHandler handler;
    private Session session;

    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        translator = new FakeTranslator();
        ui = new Ui();
        handler = new DocumentHandler(sender, translator, ui);
        session = new Session();
        session.setSource(Language.RU);
        session.setTarget(Language.EN);
        session.setState(Session.State.WAITING_TEXT);
    }

    private UserMessage createDocumentMessage(String fileName, Long fileSize) {
        return new UserMessage(chatId, "doc-123", fileName, fileSize);
    }

    @Test
    void shouldSupportDocumentMessage() {
        UserMessage msg = createDocumentMessage("test.txt", 100L);
        Assertions.assertTrue(handler.supports(msg));
    }

    @Test
    void shouldNotSupportTextMessage() {
        UserMessage msg = new UserMessage(chatId, "just text");
        Assertions.assertFalse(handler.supports(msg));
    }

    @Test
    void shouldRejectNonTxtFile() {
        UserMessage msg = createDocumentMessage("image.png", 100L);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileNotTxt, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    @Test
    void shouldRejectFileWithoutName() {
        UserMessage msg = createDocumentMessage(null, 100L);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileNotTxt, sender.lastText);
    }

    @Test
    void shouldRejectTooBigFile() {
        long bigSize = DocumentHandler.MAX_FILE_BYTES + 1;
        UserMessage msg = createDocumentMessage("test.txt", bigSize);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileTooBig, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    @Test
    void shouldRejectZeroSizeFile() {
        UserMessage msg = createDocumentMessage("test.txt", 0L);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileEmpty, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    @Test
    void shouldHandleDownloadError() {
        sender.shouldThrowOnDownload = true;
        UserMessage msg = createDocumentMessage("test.txt", 100L);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileReadErr, sender.lastText);
    }

    @Test
    void shouldRejectFileWithBlankContent() {
        sender.downloadedBytes = "  \uFEFF \n ".getBytes(StandardCharsets.UTF_8);
        UserMessage msg = createDocumentMessage("test.txt", 100L);
        handler.handle(msg, session);
        Assertions.assertEquals(ui.fileEmpty, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
    }

    @Test
    void shouldTranslateFileAndSendDocument() {
        String fileContent = "Привет мир";
        sender.downloadedBytes = fileContent.getBytes(StandardCharsets.UTF_8);
        translator.result = "Hello world";
        UserMessage msg = createDocumentMessage("test.txt", 100L);

        handler.handle(msg, session);

        Assertions.assertEquals(1, sender.downloadCalls);
        Assertions.assertEquals("doc-123", sender.lastDownloadFileId);
        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(fileContent, translator.lastText);
        Assertions.assertEquals(1, sender.sendDocumentCalls);
        Assertions.assertEquals("test_translated.txt", sender.lastDocumentFileName);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    @Test
    void shouldHandleTranslationError() {
        sender.downloadedBytes = "Привет".getBytes(StandardCharsets.UTF_8);
        translator.shouldThrow = true;
        UserMessage msg = createDocumentMessage("test.txt", 100L);

        handler.handle(msg, session);

        Assertions.assertEquals(ui.translateErr, sender.lastText);
        Assertions.assertEquals(0, sender.sendDocumentCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    private static class FakeSender implements MessageSender {
        int sendCalls; String lastText;
        int downloadCalls; String lastDownloadFileId;
        byte[] downloadedBytes = new byte[0];
        boolean shouldThrowOnDownload = false;
        int sendDocumentCalls; String lastDocumentFileName;

        @Override public void send(long chatId, String text) { sendCalls++; lastText = text; }
        @Override public void send(long chatId, String text, AppKeyboard keyboard) { sendCalls++; lastText = text; }

        @Override
        public byte[] download(String fileId) throws Exception {
            downloadCalls++;
            lastDownloadFileId = fileId;
            if (shouldThrowOnDownload) throw new Exception("Download failed");
            return downloadedBytes;
        }

        @Override
        public void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard) {
            sendDocumentCalls++;
            lastDocumentFileName = fileName;
        }

        @Override public void ackCallback(String callbackId) {}
    }

    private static class FakeTranslator implements TranslationService {
        int translateCalls; String lastText;
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