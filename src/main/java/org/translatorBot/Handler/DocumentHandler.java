package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Sender;
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

    private final Sender sender;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * Создаёт обработчик текстовых файлов.
     *
     * @param sender     отправитель сообщений и загрузчик файлов
     * @param translator сервис перевода текста
     * @param ui         источник текстов и клавиатур
     */
    public DocumentHandler(Sender sender, TranslationService translator, Ui ui) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
    }

    /**
     * Проверяет, является ли сообщение документом.
     *
     * @param message сообщение от Telegram
     * @return {@code true}, если сообщение содержит документ
     */
    @Override
    public boolean supports(Message message) {
        return message.hasDocument();
    }

    /**
     * Обрабатывает файл: проверяет формат и размер, скачивает, переводит
     * и отправляет результат пользователю. При ошибках отправляет
     * соответствующее сообщение.
     *
     * @param chatId  идентификатор чата
     * @param message сообщение с документом
     * @param session сессия пользователя с выбранными языками
     */
    @Override
    public void handle(long chatId, Message message, Session session) {
        Document doc = message.getDocument();
        String name = doc.getFileName();

        if (name == null || !name.toLowerCase().endsWith(".txt")) {
            sender.send(chatId, ui.fileNotTxt);
            return;
        }
        if (doc.getFileSize() != null && doc.getFileSize() > MAX_FILE_BYTES) {
            sender.send(chatId, ui.fileTooBig);
            return;
        }
        if (doc.getFileSize() != null && doc.getFileSize() == 0) {
            sender.send(chatId, ui.fileEmpty);
            return;
        }

        String text;
        try {
            byte[] bytes = sender.download(doc.getFileId());
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
            session.setState(Session.State.IDLE);
            String base = name.substring(0, name.length() - 4);
            sender.sendDocument(chatId, base + "_translated.txt",
                    result.getBytes(StandardCharsets.UTF_8), ui.fileReady, ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}