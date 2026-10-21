package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Тесты {@link Sender}: формирование запросов и устойчивость к ошибкам клиента.
 * <p>
 * {@link TelegramClient} имеет десятки методов, реализовывать их все вручную нет смысла,
 * поэтому вместо класса используется {@link Proxy}, который записывает вызовы
 * {@code execute(...)} и подменяет скачивание файлов. Mockito не нужен.
 */
class SenderTest {
    /** Запросы, переданные в {@code TelegramClient.execute(...)} в порядке вызова. */
    private final List<Object> executed = new ArrayList<>();
    /** Если {@code true}, клиент бросает {@link TelegramApiException} на каждый вызов. */
    private boolean failing = false;

    /** Байты, которые вернет фейковое скачивание файла. */
    private byte[] fileBytes = "file content".getBytes(StandardCharsets.UTF_8);
    /** Если {@code true}, скачивание файла бросит исключение. */
    private boolean downloadFailing = false;

    private Sender sender;

    /**
     * Создаёт прокси-клиент, записывающий вызовы {@code execute},
     * и оборачивает его в {@link Sender}.
     */
    @BeforeEach
    void setUp() {
        TelegramClient client = (TelegramClient) Proxy.newProxyInstance(
                TelegramClient.class.getClassLoader(),
                new Class<?>[]{TelegramClient.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();

                    if (methodName.equals("execute")) {
                        executed.add(args[0]);
                        if (failing) {
                            throw new TelegramApiException("network down");
                        }
                        // Если это GetFile, возвращаем фейковый объект File
                        if (args[0] instanceof GetFile) {
                            return new File();
                        }
                        return null;
                    }

                    if (methodName.equals("downloadFileAsStream")) {
                        if (downloadFailing) {
                            throw new TelegramApiException("download error");
                        }
                        return new ByteArrayInputStream(fileBytes);
                    }

                    return null;
                });
        sender = new Sender(client);
    }

    /**
     * Проверяет, что был выполнен ровно один запрос, и возвращает его как {@link SendMessage}.
     *
     * @return единственное отправленное сообщение
     */
    private SendMessage onlySentMessage() {
        Assertions.assertEquals(1, executed.size());
        return (SendMessage) executed.get(0);
    }

    /** Отправляет простое сообщение без клавиатуры с корректными chatId и текстом. */
    @Test
    void sendsPlainMessage() {
        sender.send(123L, "hello");

        SendMessage sent = onlySentMessage();
        Assertions.assertEquals("123", sent.getChatId());
        Assertions.assertEquals("hello", sent.getText());
        Assertions.assertNull(sent.getReplyMarkup());
    }

    /** Отправляет сообщение с inline-клавиатурой и прикрепляет её без копирования. */
    @Test
    void sendsMessageWithKeyboard() {
        InlineKeyboardMarkup keyboard = new Ui().mainMenu();

        sender.send(5L, "menu", keyboard);

        SendMessage sent = onlySentMessage();
        Assertions.assertEquals("5", sent.getChatId());
        Assertions.assertSame(keyboard, sent.getReplyMarkup());
    }

    /** При {@code null}-клавиатуре replyMarkup не устанавливается. */
    @Test
    void nullKeyboardMeansNoReplyMarkup() {
        sender.send(7L, "text", null);

        Assertions.assertNull(onlySentMessage().getReplyMarkup());
    }

    /** Отрицательные chatId (группы/каналы) корректно преобразуются в строку. */
    @Test
    void handlesNegativeChatIds() {
        sender.send(-1001234L, "group");

        Assertions.assertEquals("-1001234", onlySentMessage().getChatId());
    }

    /** Исключения клиента при отправке сообщения проглатываются и не пробрасываются. */
    @Test
    void sendSwallowsClientExceptions() {
        failing = true;

        Assertions.assertDoesNotThrow(() -> sender.send(1L, "text"));
        Assertions.assertEquals(1, executed.size());
    }

    /** {@code ackCallback} отвечает на callback с переданным идентификатором. */
    @Test
    void ackCallbackAnswersWithGivenId() {
        sender.ackCallback("cb-42");

        Assertions.assertEquals(1, executed.size());
        AnswerCallbackQuery answer = (AnswerCallbackQuery) executed.get(0);
        Assertions.assertEquals("cb-42", answer.getCallbackQueryId());
    }

    /** Исключения клиента при подтверждении callback проглатываются. */
    @Test
    void ackCallbackSwallowsClientExceptions() {
        failing = true;

        Assertions.assertDoesNotThrow(() -> sender.ackCallback("cb-1"));
    }

    /**
     * Проверяет, что {@code download} сначала запрашивает {@link GetFile},
     * а затем возвращает байты из {@code downloadFileAsStream}.
     *
     * @throws Exception если скачивание не удалось
     */
    @Test
    void downloadRequestsFileAndReturnsBytes() throws Exception {
        byte[] result = sender.download("file-123");

        Assertions.assertArrayEquals(fileBytes, result);
        Assertions.assertEquals(1, executed.size());

        GetFile getFile = (GetFile) executed.get(0);
        Assertions.assertEquals("file-123", getFile.getFileId());
    }

    /**
     * Проверяет, что {@code download} пробрасывает исключение, если клиент упал.
     */
    @Test
    void downloadPropagatesException() {
        downloadFailing = true;

        Assertions.assertThrows(TelegramApiException.class,
                () -> sender.download("file-123"));
    }

    /**
     * Проверяет, что {@code sendDocument} формирует запрос с правильными
     * параметрами и не падает при передаче клавиатуры.
     */
    @Test
    void sendDocumentFormsCorrectRequest() {
        byte[] content = "translated text".getBytes(StandardCharsets.UTF_8);
        InlineKeyboardMarkup keyboard = new Ui().afterTranslation();

        sender.sendDocument(100L, "file.txt", content, "caption", keyboard);

        Assertions.assertEquals(1, executed.size());
        SendDocument sent = (SendDocument) executed.get(0);

        Assertions.assertEquals("100", sent.getChatId());
        Assertions.assertEquals("caption", sent.getCaption());
        Assertions.assertSame(keyboard, sent.getReplyMarkup());

        // Проверяем имя файла
        Assertions.assertEquals("file.txt", sent.getDocument().getMediaName());
    }

    /**
     * Проверяет, что {@code sendDocument} проглатывает исключения клиента.
     */
    @Test
    void sendDocumentSwallowsClientExceptions() {
        failing = true;

        Assertions.assertDoesNotThrow(() ->
                sender.sendDocument(1L, "f.txt", new byte[0], "cap", null));
    }
}