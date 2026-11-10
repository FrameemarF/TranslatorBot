package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.FileDownloadException;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Перевод файла .txt: в ответ приходит файл name_translated.txt.
 * <p>
 * Проверяет расширение и размер файла, скачивает его во временный файл,
 * читает текст, переводит и отправляет результат обратно пользователю
 * в виде документа. Временные файлы удаляются после обработки.
 */
public class DocumentHandler implements MaterialHandler {
    /** Максимально допустимый размер загружаемого файла в байтах (5 МБ). */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Отправитель сообщений и загрузчик файлов. */
    private final MessageSender sender;
    /** Сервис перевода текста. */
    private final TranslationService translator;
    /** Источник текстов и клавиатур. */
    private final Ui ui;

    /**
     * Создаёт обработчик текстовых файлов.
     *
     * @param sender     отправитель сообщений и загрузчик файлов
     * @param translator сервис перевода текста
     * @param ui         источник текстов и клавиатур
     */
    public DocumentHandler(MessageSender sender, TranslationService translator, Ui ui) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
    }

    /** {@inheritDoc} */
    @Override
    public MessageType type() {
        return MessageType.DOCUMENT;
    }

    /**
     * Обрабатывает файл: проверяет формат и размер, скачивает, переводит
     * и отправляет результат пользователю. При ошибках отправляет
     * соответствующее сообщение.
     *
     * @param message входящее сообщение с документом
     * @param session сессия пользователя с выбранными языками
     */
    @Override
    public void handle(UserMessage message, Session session) {
        long chatId = message.chatId();
        String name = message.fileName();
        String fileId = message.fileId();
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
            downloaded = sender.download(fileId);
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

            sender.sendDocument(chatId, base + "_translated.txt", translated,
                    ui.fileReady, ui.afterTranslation());
        } catch (FileDownloadException e) {
            log.error("File download failed for chat {}", chatId, e);
            sender.send(chatId, ui.fileReadErr);
        } catch (IOException e) {
            log.error("File read/write failed for chat {}", chatId, e);
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
     * Удаляет временный файл, не мешая основному сценарию при ошибке удаления.
     *
     * @param path путь к файлу или {@code null}
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
