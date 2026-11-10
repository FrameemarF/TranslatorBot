package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.FileDownloadException;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Модульные тесты для {@link DocumentHandler}.
 */
class DocumentHandlerTest {

    private final long chatId = 100L;

    @TempDir
    Path tempDir;

    private FakeSender sender;
    private FakeTranslator translator;
    private Ui ui;
    private DocumentHandler handler;
    private Session session;

    @BeforeEach
    void setUp() {
        sender = new FakeSender(tempDir);
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
    void handlesDocumentMessages() {
        Assertions.assertEquals(MessageType.DOCUMENT, handler.type());
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
        Assertions.assertEquals("Hello world", sender.lastDocumentContent);
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
        private final Path tempDir;
        int sendCalls;
        String lastText;
        int downloadCalls;
        String lastDownloadFileId;
        byte[] downloadedBytes = new byte[0];
        boolean shouldThrowOnDownload = false;
        int sendDocumentCalls;
        String lastDocumentFileName;
        String lastDocumentContent;
        String lastDocumentCaption;

        FakeSender(Path tempDir) {
            this.tempDir = tempDir;
        }

        @Override
        public void send(long chatId, String text) {
            sendCalls++;
            lastText = text;
        }

        @Override
        public void send(long chatId, String text, AppKeyboard keyboard) {
            sendCalls++;
            lastText = text;
        }

        @Override
        public Path download(String fileId) {
            downloadCalls++;
            lastDownloadFileId = fileId;
            if (shouldThrowOnDownload) {
                throw new FileDownloadException("Download failed", new IOException("test"));
            }
            try {
                Path file = tempDir.resolve("download-" + downloadCalls + ".txt");
                Files.write(file, downloadedBytes);
                return file;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendDocument(long chatId, String fileName, Path content,
                                 String caption, AppKeyboard keyboard) {
            sendDocumentCalls++;
            lastDocumentFileName = fileName;
            lastDocumentCaption = caption;
            try {
                lastDocumentContent = Files.readString(content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void ackCallback(String callbackId) {}
    }

    private static class FakeTranslator implements TranslationService {
        int translateCalls;
        String lastText;
        String result = "translated";
        boolean shouldThrow = false;
        Language detectedLanguage = Language.RU;

        @Override
        public String translate(String text, Language from, Language to) {
            translateCalls++;
            lastText = text;
            if (shouldThrow) throw new TranslationException("Translation failed");
            return result;
        }

        @Override
        public Optional<Language> detect(String text) {
            if (shouldThrow) throw new TranslationException("Detection failed");
            return Optional.ofNullable(detectedLanguage);
        }
    }
}
