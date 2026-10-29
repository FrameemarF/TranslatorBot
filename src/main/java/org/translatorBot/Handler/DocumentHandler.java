package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.nio.charset.StandardCharsets;

/**
 * Перевод файла .txt: в ответ приходит файл name_translated.txt.
 * <p>
 * Проверяет расширение и размер файла, скачивает его содержимое,
 * переводит текст и отправляет результат обратно пользователю в виде документа.
 */
public class DocumentHandler implements MaterialHandler {
    /** Максимально допустимый размер загружаемого файла в байтах (5 МБ). */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final TranslationService translator;
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

    /**
     * Проверяет, является ли сообщение документом.
     *
     * @param message входящее сообщение
     * @return {@code true}, если сообщение содержит документ
     */
    @Override
    public boolean supports(UserMessage message) {
        return message.type() == MessageType.DOCUMENT;
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

        String text;
        try {
            byte[] bytes = sender.download(fileId);
            text = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "").strip();
        } catch (Exception e) {
            log.error("File download failed for chat {}", chatId, e);
            sender.send(chatId, ui.fileReadErr);
            return;
        }

        if (text.isEmpty()) {
            sender.send(chatId, ui.fileEmpty);
            return;
        }
        if (text.length() > TranslationService.MAX_TEXT_LENGTH) {
            sender.send(chatId, ui.tooLong);
            return;
        }

        try {
            String result = translator.translate(text, session.getSource(), session.getTarget());
            String base = name.substring(0, name.length() - 4);
            sender.sendDocument(chatId, base + "_translated.txt",
                    result.getBytes(StandardCharsets.UTF_8), ui.fileReady, ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}