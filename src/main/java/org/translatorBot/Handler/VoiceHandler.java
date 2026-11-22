package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;
import org.translatorBot.History.HistoryException;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.FileDownloadException;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Speech.SpeechRecognitionException;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.SpeechUnrecognizedException;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Обработчик голосовых сообщений.
 *
 * <p>Выполняет цепочку «скачать → распознать → перевести → ответить текстом».
 * Слишком длинные голосовые сообщения (более {@value #MAX_DURATION_SECONDS} секунд)
 * отклоняются без обработки. Успешный перевод сохраняется в историю.
 *
 * @see MaterialHandler
 */
public class VoiceHandler implements MaterialHandler {
    /** Максимально допустимая длительность голосового сообщения в секундах. */
    static final int MAX_DURATION_SECONDS = 30;

    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Отправитель сообщений и загрузчик файлов. */
    private final MessageSender sender;
    /** Сервис распознавания речи. */
    private final SpeechRecognitionService speech;
    /** Сервис перевода текста. */
    private final TranslationService translator;
    /** Поставщик текстов интерфейса. */
    private final Ui ui;
    /** Хранилище истории переводов. */
    private final HistoryService history;

    /**
     * Создаёт обработчик голосовых сообщений.
     *
     * @param sender     отправитель сообщений и загрузчик файлов
     * @param speech     сервис распознавания речи
     * @param translator сервис перевода текста
     * @param ui         поставщик текстов интерфейса
     * @param history    хранилище истории переводов
     */
    public VoiceHandler(MessageSender sender, SpeechRecognitionService speech,
                        TranslationService translator, Ui ui, HistoryService history) {
        this.sender = sender;
        this.speech = speech;
        this.translator = translator;
        this.ui = ui;
        this.history = history;
    }

    /** {@inheritDoc} */
    @Override
    public MessageType type() {
        return MessageType.VOICE;
    }

    /**
     * Обрабатывает голосовое сообщение: проверяет длительность, распознаёт речь,
     * проверяет совпадение языка, переводит результат на целевой язык сессии
     * и отправляет текст пользователю.
     *
     * <p>В случае ошибки распознавания или перевода отправляет соответствующее
     * сообщение об ошибке. Состояние сессии НЕ сбрасывается в IDLE, чтобы
     * пользователь мог отправить следующую часть длинного сообщения без
     * необходимости заново выбирать языки.
     *
     * @param message входящее сообщение с голосовым вложением
     * @param session текущая сессия пользователя (источник и целевой язык)
     */
    @Override
    public void handle(UserMessage message, Session session) {
        long chatId = message.chatId();
        Integer duration = message.voiceDuration();

        if (duration != null && duration > MAX_DURATION_SECONDS) {
            sender.send(chatId, ui.voiceTooLong);
            return;
        }
        if (duration == null || duration == 0) {
            sender.send(chatId, ui.speechUnrecognized);
            return;
        }

        Optional<String> recognized = recognize(chatId, message.fileId(), session);
        if (recognized.isEmpty()) {
            return;
        }

        try {
            Optional<Language> detected = translator.detect(recognized.get());
            if (detected.isPresent() && detected.get() != session.getSource()) {
                sender.send(chatId, "⚠️ Распознанный язык (" + detected.get().label +
                        ") не совпадает с выбранным языком источника (" + session.getSource().label +
                        "). Пожалуйста, говори на правильном языке или выбери другой язык источника.");
                return;
            }

            String result = translator.translate(recognized.get(), session.getSource(), session.getTarget());

            boolean saved = saveHistory(message, session, recognized.get(), result);
            String reply = ui.recognized(recognized.get()) + "\n" + ui.translation(result);
            if (!saved) {
                reply = reply + "\n" + ui.historySaveFailed;
            }
            sender.send(chatId, reply, ui.afterTranslation());
        } catch (TranslationException e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }

    /**
     * Скачивает и распознаёт голосовое сообщение. Временный файл удаляется
     * в любом случае. При ошибке пользователю уже отправлено сообщение,
     * поэтому возвращается {@link Optional#empty()}.
     *
     * @param chatId  идентификатор чата
     * @param fileId  идентификатор голосового файла
     * @param session сессия с выбранным языком-источником
     * @return распознанный текст или пустой {@link Optional}
     */
    private Optional<String> recognize(long chatId, String fileId, Session session) {
        Path audio = null;
        try {
            audio = sender.download(fileId);
            byte[] bytes = Files.readAllBytes(audio);
            String recognized = speech.recognize(bytes, "voice.ogg", session.getSource());

            if (recognized == null || recognized.trim().isBlank()) {
                sender.send(chatId, ui.speechUnrecognized);
                return Optional.empty();
            }
            return Optional.of(recognized);
        } catch (SpeechUnrecognizedException e) {
            sender.send(chatId, ui.speechUnrecognized);
            return Optional.empty();
        } catch (SpeechRecognitionException e) {
            log.error("Speech recognition failed for chat {}", chatId, e);
            sender.send(chatId, ui.speechUnavailable);
            return Optional.empty();
        } catch (FileDownloadException | IOException e) {
            log.error("Voice download failed for chat {}", chatId, e);
            sender.send(chatId, ui.speechUnavailable);
            return Optional.empty();
        } finally {
            deleteQuietly(audio);
        }
    }

    /**
     * Сохраняет успешный перевод в историю, не прерывая ответ пользователю
     * при ошибке хранилища.
     *
     * @param message    входящее сообщение
     * @param session    сессия с выбранными языками
     * @param sourceText распознанный текст
     * @param translated переведённый текст
     * @return {@code true}, если запись сохранена
     */
    private boolean saveHistory(UserMessage message, Session session,
                                String sourceText, String translated) {
        TranslationRecord record = new TranslationRecord(
                MessageType.VOICE,
                session.getSource(),
                session.getTarget(),
                sourceText,
                translated,
                null,
                Instant.now());
        try {
            history.save(message.accountId(), record);
            return true;
        } catch (HistoryException e) {
            log.error("Failed to save translation history for account {}",
                    message.accountId(), e);
            return false;
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
