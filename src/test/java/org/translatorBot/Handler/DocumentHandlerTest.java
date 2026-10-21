package org.translatorBot.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.translatorBot.Language;
import org.translatorBot.Sender;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.nio.charset.StandardCharsets;

/**
 * Модульные тесты для {@link DocumentHandler}.
 * <p>
 * Проверяют валидацию файлов (формат, размер, пустота), успешный сценарий
 * скачивания и перевода файла, а также обработку ошибок чтения и перевода.
 * Используются ручные заглушки Sender и TranslationService.
 */
class DocumentHandlerTest {

    private final long chatId = 100L;
    private final ObjectMapper json = new ObjectMapper();

    private FakeSender sender;
    private FakeTranslator translator;
    private Ui ui;
    private DocumentHandler handler;
    private Session session;

    /**
     * Подготавливает тестовое окружение: заглушки, сессию и сам обработчик.
     */
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

    /**
     * Создаёт объект {@link Message} с документом.
     *
     * @param fileName имя файла
     * @param fileSize размер файла в байтах или {@code null}
     * @return сообщение с документом
     * @throws Exception если десериализация не удалась
     */
    private Message createDocumentMessage(String fileName, Long fileSize) throws Exception {
        ObjectNode chat = json.createObjectNode();
        chat.put("id", chatId);
        chat.put("type", "private");

        ObjectNode doc = json.createObjectNode();
        doc.put("file_id", "doc-123");
        doc.put("file_unique_id", "unique-123");
        if (fileName != null) doc.put("file_name", fileName);
        if (fileSize != null) doc.put("file_size", fileSize);

        ObjectNode msg = json.createObjectNode();
        msg.put("message_id", 1);
        msg.put("date", 1);
        msg.set("chat", chat);
        msg.set("document", doc);

        return json.treeToValue(msg, Message.class);
    }

    /**
     * Проверяет, что сообщение с документом поддерживается обработчиком.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldSupportDocumentMessage() throws Exception {
        Message msg = createDocumentMessage("test.txt", 100L);
        Assertions.assertTrue(handler.supports(msg));
    }

    /**
     * Проверяет, что сообщение без документа не поддерживается.
     */
    @Test
    void shouldNotSupportTextMessage() {
        Message msg = new Message();
        msg.setText("just text");
        Assertions.assertFalse(handler.supports(msg));
    }

    /**
     * Проверяет, что файл с неподходящим расширением отклоняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldRejectNonTxtFile() throws Exception {
        Message msg = createDocumentMessage("image.png", 100L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileNotTxt, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    /**
     * Проверяет, что файл без имени отклоняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldRejectFileWithoutName() throws Exception {
        Message msg = createDocumentMessage(null, 100L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileNotTxt, sender.lastText);
    }

    /**
     * Проверяет, что файл, превышающий лимит размера, отклоняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldRejectTooBigFile() throws Exception {
        long bigSize = DocumentHandler.MAX_FILE_BYTES + 1;
        Message msg = createDocumentMessage("test.txt", bigSize);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileTooBig, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    /**
     * Проверяет, что файл нулевого размера отклоняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldRejectZeroSizeFile() throws Exception {
        Message msg = createDocumentMessage("test.txt", 0L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileEmpty, sender.lastText);
        Assertions.assertEquals(0, sender.downloadCalls);
    }

    /**
     * Проверяет, что при ошибке скачивания отправляется сообщение об ошибке.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldHandleDownloadError() throws Exception {
        sender.shouldThrowOnDownload = true;
        Message msg = createDocumentMessage("test.txt", 100L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.downloadCalls);
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileReadErr, sender.lastText);
    }

    /**
     * Проверяет, что пустой файл (после удаления BOM и пробелов) отклоняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldRejectFileWithBlankContent() throws Exception {
        sender.downloadedBytes = "  \uFEFF \n ".getBytes(StandardCharsets.UTF_8);
        Message msg = createDocumentMessage("test.txt", 100L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.fileEmpty, sender.lastText);
        Assertions.assertEquals(0, translator.translateCalls);
    }

    /**
     * Проверяет успешный сценарий: файл скачивается, текст переводится,
     * сессия сбрасывается, а пользователю отправляется файл с переводом.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldTranslateFileAndSendDocument() throws Exception {
        String fileContent = "Привет мир";
        sender.downloadedBytes = fileContent.getBytes(StandardCharsets.UTF_8);
        translator.result = "Hello world";

        Message msg = createDocumentMessage("test.txt", 100L);

        handler.handle(chatId, msg, session);

        // Проверка скачивания
        Assertions.assertEquals(1, sender.downloadCalls);
        Assertions.assertEquals("doc-123", sender.lastDownloadFileId);

        // Проверка перевода
        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(fileContent, translator.lastText);
        Assertions.assertEquals(Language.RU, translator.lastFrom);
        Assertions.assertEquals(Language.EN, translator.lastTo);

        // Проверка отправки файла
        Assertions.assertEquals(1, sender.sendDocumentCalls);
        Assertions.assertEquals("test_translated.txt", sender.lastDocumentFileName);
        Assertions.assertEquals("Hello world", new String(sender.lastDocumentContent, StandardCharsets.UTF_8));
        Assertions.assertEquals(ui.fileReady, sender.lastDocumentCaption);

        // Проверка состояния сессии
        Assertions.assertEquals(Session.State.IDLE, session.getState());
    }

    /**
     * Проверяет, что при ошибке перевода пользователь получает сообщение
     * об ошибке, а состояние сессии не меняется.
     *
     * @throws Exception если десериализация не удалась
     */
    @Test
    void shouldHandleTranslationError() throws Exception {
        sender.downloadedBytes = "Привет".getBytes(StandardCharsets.UTF_8);
        translator.shouldThrow = true;

        Message msg = createDocumentMessage("test.txt", 100L);

        handler.handle(chatId, msg, session);

        Assertions.assertEquals(1, translator.translateCalls);
        Assertions.assertEquals(1, sender.sendCalls);
        Assertions.assertEquals(ui.translateErr, sender.lastText);
        Assertions.assertEquals(0, sender.sendDocumentCalls);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    /** Заглушка {@link Sender}, перехватывающая скачивание и отправку файлов. */
    private class FakeSender extends Sender {
        int sendCalls;
        String lastText;

        int downloadCalls;
        String lastDownloadFileId;
        byte[] downloadedBytes = new byte[0];
        boolean shouldThrowOnDownload = false;

        int sendDocumentCalls;
        String lastDocumentFileName;
        byte[] lastDocumentContent;
        String lastDocumentCaption;

        FakeSender() {
            super(null);
        }

        @Override
        public void send(long chatId, String text) {
            sendCalls++;
            lastText = text;
        }

        @Override
        public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
            sendCalls++;
            lastText = text;
        }

        @Override
        public byte[] download(String fileId) throws Exception {
            downloadCalls++;
            lastDownloadFileId = fileId;
            if (shouldThrowOnDownload) throw new Exception("Download failed");
            return downloadedBytes;
        }

        @Override
        public void sendDocument(long chatId, String fileName, byte[] content, String caption, InlineKeyboardMarkup keyboard) {
            sendDocumentCalls++;
            lastDocumentFileName = fileName;
            lastDocumentContent = content;
            lastDocumentCaption = caption;
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