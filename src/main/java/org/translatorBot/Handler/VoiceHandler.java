package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Platforms.FileDownloadException;
import org.translatorBot.Platforms.MessageSender;
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
import java.util.List;
import java.util.Optional;

/**
 * Обработчик голосовых сообщений: скачать, распознать, перевести, ответить.
 */
public class VoiceHandler {
    /** Максимально допустимая длительность голосового в секундах. */
    static final int MAX_DURATION_SECONDS = 30;

    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final SpeechRecognitionService speech;
    private final TranslationService translator;
    private final Ui ui;
    private final HistoryService history;
    private final Platform platform;
    private final VoiceReplySender voiceReply;

    /**
     * @param sender     отправитель сообщений
     * @param speech     сервис распознавания речи
     * @param translator сервис перевода
     * @param ui         тексты интерфейса
     * @param history    история переводов
     * @param platform   платформа пользователя
     * @param voiceReply озвучивание перевода
     */
    public VoiceHandler(MessageSender sender, SpeechRecognitionService speech,
                        TranslationService translator, Ui ui, HistoryService history,
                        Platform platform, VoiceReplySender voiceReply) {
        this.sender = sender;
        this.speech = speech;
        this.translator = translator;
        this.ui = ui;
        this.history = history;
        this.platform = platform;
        this.voiceReply = voiceReply;
    }

    /**
     * Обрабатывает голосовое сообщение.
     *
     * @param message входящее сообщение
     * @param session сессия с выбранными языками
     */
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
                sender.send(chatId, mismatch(detected.get(), session.getSource()));
                return;
            }

            String result = translator.translate(recognized.get(),
                    session.getSource(), session.getTarget());
            List<byte[]> voice = voiceReply.prepare(message.accountId(), result, session.getTarget());
            boolean saved = history.save(platform, message.accountId(),
                    record(message, session, recognized.get(), result));

            String reply = ui.recognized(recognized.get()) + "\n" + ui.translation(result);
            if (!saved) {
                reply += "\n" + ui.historySaveFailed;
            }
            if (voice != null && voice.isEmpty()) {
                reply += "\n" + ui.ttsFailed;
            }
            sender.send(chatId, reply, ui.afterTranslation());
            voiceReply.send(chatId, voice);
        } catch (TranslationException e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }

    /**
     * Скачивает и распознаёт голосовое сообщение.
     *
     * @param chatId  идентификатор чата
     * @param fileId  идентификатор файла
     * @param session сессия с языком-источником
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
     * Сообщает о несовпадении языка речи и выбранного источника.
     *
     * @param detected обнаруженный язык
     * @param expected выбранный язык
     * @return текст предупреждения
     */
    private String mismatch(Language detected, Language expected) {
        return "⚠️ Распознанный язык (" + detected.label
                + ") не совпадает с выбранным языком источника (" + expected.label
                + "). Пожалуйста, говори на правильном языке или выбери другой язык источника.";
    }

    /**
     * Создаёт запись истории.
     *
     * @param message    сообщение
     * @param session    сессия
     * @param sourceText распознанный текст
     * @param translated перевод
     * @return запись истории
     */
    private TranslationRecord record(UserMessage message, Session session,
                                     String sourceText, String translated) {
        return new TranslationRecord(MessageType.VOICE, session.getSource(), session.getTarget(),
                sourceText, translated, null, Instant.now());
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
