package org.translatorBot.Sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Telegram.TelegramMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Реализация {@link MessageSender} для Telegram.
 * Обёртка над {@link TelegramClient} для отправки сообщений и подтверждения
 * callback-запросов.
 */
public class SenderTG implements MessageSender {
    private final TelegramClient client;
    private final TelegramMapper mapper;
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * @param client клиент Telegram, через который выполняются запросы
     * @param mapper маппер для конвертации клавиатур
     */
    public SenderTG(TelegramClient client, TelegramMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public void send(long chatId, String text) {
        send(chatId, text, null);
    }

    @Override
    public void send(long chatId, String text, AppKeyboard keyboard) {
        SendMessage.SendMessageBuilder<?, ?> b = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text);
        if (keyboard != null) {
            b.replyMarkup(mapper.toTelegramKeyboard(keyboard));
        }
        try {
            client.execute(b.build());
        } catch (Exception e) {
            log.error("Failed to send message to chat {}", chatId, e);
        }
    }

    @Override
    public void ackCallback(String callbackId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (Exception e) {
            log.warn("Failed to answer callback {}", callbackId, e);
        }
    }

    @Override
    public byte[] download(String fileId) throws Exception {
        org.telegram.telegrambots.meta.api.objects.File file =
                client.execute(GetFile.builder().fileId(fileId).build());
        try (InputStream in = client.downloadFileAsStream(file)) {
            return in.readAllBytes();
        }
    }

    @Override
    public void sendDocument(long chatId, String fileName, byte[] content,
                             String caption, AppKeyboard keyboard) {
        SendDocument.SendDocumentBuilder<?, ?> b = SendDocument.builder()
                .chatId(String.valueOf(chatId))
                .document(new InputFile(new ByteArrayInputStream(content), fileName))
                .caption(caption);
        if (keyboard != null) {
            b.replyMarkup(mapper.toTelegramKeyboard(keyboard));
        }
        try {
            client.execute(b.build());
        } catch (Exception e) {
            log.error("Failed to send document to chat {}", chatId, e);
        }
    }
}