package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Обёртка над {@link TelegramClient} для отправки сообщений и подтверждения
 * callback-запросов. Ошибки отправки логируются, но не пробрасываются наружу.
 */
public class Sender {
    private final TelegramClient client;
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * @param client клиент Telegram, через который выполняются запросы
     */
    public Sender(TelegramClient client) { this.client = client; }

    /**
     * Отправляет текстовое сообщение без клавиатуры.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    public void send(long chatId, String text) { send(chatId, text, null); }

    /**
     * Отправляет текстовое сообщение с опциональной inline-клавиатурой.
     * При возникновении ошибки логирует её и не выбрасывает исключение.
     *
     * @param chatId   идентификатор чата
     * @param text     текст сообщения
     * @param keyboard клавиатура или {@code null}, если не нужна
     */
    public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage.SendMessageBuilder<?, ?> b = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text);
        if (keyboard != null) b.replyMarkup(keyboard);
        try {
            client.execute(b.build());
        } catch (Exception e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    /**
     * Подтверждает callback-запрос (убирает «часики» у пользователя).
     * При ошибке логирует предупреждение и не выбрасывает исключение.
     *
     * @param callbackId идентификатор callback-запроса
     */
    public void ackCallback(String callbackId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (Exception e) {
            log.warn("Failed to answer callback {}", callbackId, e);
        }
    }

    /**
     * Скачивает файл из Telegram по {@code fileId}. В отличие от отправки,
     * ошибки пробрасываются: вызывающему нужно решить, что сказать пользователю.
     *
     * @param fileId идентификатор файла
     * @return содержимое файла
     * @throws Exception при ошибке Telegram API или чтения
     */
    public byte[] download(String fileId) throws Exception {
        org.telegram.telegrambots.meta.api.objects.File file =
                client.execute(GetFile.builder().fileId(fileId).build());
        try (InputStream in = client.downloadFileAsStream(file)) {
            return in.readAllBytes();
        }
    }

    /**
     * Отправляет документ с подписью и клавиатурой. Ошибки логируются и не пробрасываются.
     *
     * @param fileName имя файла, которое увидит пользователь
     * @param content  содержимое файла
     * @param caption  подпись
     * @param keyboard клавиатура или {@code null}
     */
    public void sendDocument(long chatId, String fileName, byte[] content,
                             String caption, InlineKeyboardMarkup keyboard) {
        SendDocument.SendDocumentBuilder<?, ?> b = SendDocument.builder()
                .chatId(String.valueOf(chatId))
                .document(new InputFile(new ByteArrayInputStream(content), fileName))
                .caption(caption);
        if (keyboard != null) b.replyMarkup(keyboard);
        try {
            client.execute(b.build());
        } catch (Exception e) {
            log.error("Failed to send document to chat {}", chatId, e);
        }
    }
}
