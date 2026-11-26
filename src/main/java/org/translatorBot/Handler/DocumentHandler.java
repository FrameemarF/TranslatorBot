package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Platforms.FileDownloadException;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Перевод файла .txt: в ответ приходит файл name_translated.txt.
 */
public class DocumentHandler {
    /** Максимально допустимый размер файла в байтах (5 МБ). */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final TranslationService translator;
    private final Ui ui;
    private final HistoryService history;
    private final Platform platform;
    private final VoiceReplySender voiceReply;

    /**
     * @param sender     отправитель сообщений
     * @param translator сервис перевода
     * @param ui         тексты интерфейса
     * @param history    история переводов
     * @param platform   платформа пользователя
     * @param voiceReply озвучивание перевода
     */
    public DocumentHandler(MessageSender sender, TranslationService translator, Ui ui,
                           HistoryService history, Platform platform,
                           VoiceReplySender voiceReply) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
        this.history = history;
        this.platform = platform;
        this.voiceReply = voiceReply;
    }

    /**
     * Обрабатывает файл: проверяет формат, скачивает, переводит и отправляет.
     *
     * @param message входящее сообщение с файлом
     * @param session сессия с выбранными языками
     */
    public void handle(UserMessage message, Session session) {
        long chatId = message.chatId();
        String name = message.fileName();
        Long size = message.fileSize();

        if (name == null || !name.toLowerCase().endsWith(".txt")) {
            sender.send(chatId, ui.fileNotTxt);
            return;
        }
        if (size != null && size > MAX_FILE_BYTES) {
            sender.send(chatId, ui.fileTooBig);
            return;
        }
        if (size != null && size == 0) {
            sender.send(chatId, ui.fileEmpty);
            return;
        }

        Path downloaded = null;
        Path translated = null;
        try {
            downloaded = sender.download(message.fileId());
            String text = Files.readString(downloaded, StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .strip();

            if (text.isEmpty()) {
                sender.send(chatId, ui.fileEmpty);
                return;
            }
            if (text.length() > TranslationService.MAX_TEXT_LENGTH) {
                sender.send(chatId, ui.tooLong);
                return;
            }

            String result = translator.translate(text, session.getSource(), session.getTarget());
            String base = name.substring(0, name.length() - 4);
            translated = Files.createTempFile("translatorbot-", ".txt");
            Files.writeString(translated, result, StandardCharsets.UTF_8);

            List<byte[]> voice = voiceReply.prepare(message.accountId(), result, session.getTarget());
            boolean saved = history.save(platform, message.accountId(), record(message, session, text, result));
            String caption = ui.fileReady;
            if (!saved) {
                caption += "\n" + ui.historySaveFailed;
            }
            if (voice != null && voice.isEmpty()) {
                caption += "\n" + ui.ttsFailed;
            }
            sender.sendDocument(chatId, base + "_translated.txt", translated,
                    caption, ui.afterTranslation());
            voiceReply.send(chatId, voice);
        } catch (FileDownloadException | IOException e) {
            log.error("File read failed for chat {}", chatId, e);
            sender.send(chatId, ui.fileReadErr);
        } catch (TranslationException e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        } finally {
            deleteQuietly(downloaded);
            deleteQuietly(translated);
        }
    }

    /**
     * Создаёт запись истории.
     *
     * @param message    сообщение
     * @param session    сессия
     * @param sourceText исходный текст
     * @param translated перевод
     * @return запись истории
     */
    private TranslationRecord record(UserMessage message, Session session,
                                     String sourceText, String translated) {
        return new TranslationRecord(MessageType.DOCUMENT, session.getSource(), session.getTarget(),
                sourceText, translated, message.fileName(), Instant.now());
    }

    /**
     * Удаляет временный файл, не мешая основному сценарию.
     *
     * @param path путь или {@code null}
     */
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temporary file {}", path, e);
        }
    }
}
