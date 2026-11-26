package org.translatorBot.Platforms.Telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Platforms.FileDownloadException;
import org.translatorBot.Platforms.MessageSender;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

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
    public Path download(String fileId) {
        try {
            org.telegram.telegrambots.meta.api.objects.File file =
                    client.execute(GetFile.builder().fileId(fileId).build());
            return client.downloadFile(file).toPath();
        } catch (TelegramApiException e) {
            throw new FileDownloadException("Failed to download file " + fileId, e);
        }
    }

    @Override
    public void sendDocument(long chatId, String fileName, Path content,
                             String caption, AppKeyboard keyboard) {
        SendDocument.SendDocumentBuilder<?, ?> b = SendDocument.builder()
                .chatId(String.valueOf(chatId))
                .document(new InputFile(content.toFile(), fileName))
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

    /**
     * Отправляет аудио голосовым сообщением.
     * <p>
     * Telegram показывает голосовым только OGG/Opus. Если сервис озвучки
     * вернул другой формат (например, MP3 без ffmpeg в контейнере), файл
     * отправляется как обычное аудио, чтобы ответ не потерялся.
     */
    @Override
    public void sendVoice(long chatId, byte[] audio, String fileName) {
        try {
            if (isOgg(audio)) {
                client.execute(SendVoice.builder()
                        .chatId(String.valueOf(chatId))
                        .voice(new InputFile(new ByteArrayInputStream(audio), fileName))
                        .build());
            } else {
                log.warn("Audio for chat {} is not OGG/Opus, sending as audio file", chatId);
                String name = fileName.endsWith(".ogg")
                        ? fileName.substring(0, fileName.length() - 4) + ".mp3"
                        : fileName;
                client.execute(SendAudio.builder()
                        .chatId(String.valueOf(chatId))
                        .audio(new InputFile(new ByteArrayInputStream(audio), name))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to send voice to chat {}", chatId, e);
        }
    }

    /**
     * Проверяет сигнатуру OGG-контейнера.
     *
     * @param audio содержимое аудиофайла
     * @return {@code true}, если файл начинается с {@code OggS}
     */
    private boolean isOgg(byte[] audio) {
        byte[] magic = {'O', 'g', 'g', 'S'};
        if (audio == null || audio.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (audio[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
