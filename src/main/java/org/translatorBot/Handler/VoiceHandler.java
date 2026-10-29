package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.SpeechUnrecognizedException;
import org.translatorBot.Ui;

/**
 * Обработчик голосовых сообщений.
 *
 * <p>Выполняет цепочку «скачать → распознать → перевести → ответить текстом».
 * Слишком длинные голосовые сообщения (более {@value #MAX_DURATION_SECONDS} секунд)
 * отклоняются без обработки.
 *
 * @see MaterialHandler
 */
public class VoiceHandler implements MaterialHandler {
    /** Максимально допустимая длительность голосового сообщения в секундах. */
    static final int MAX_DURATION_SECONDS = 30;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final SpeechRecognitionService speech;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * Создаёт обработчик голосовых сообщений.
     *
     * @param sender     отправитель сообщений и загрузчик файлов
     * @param speech     сервис распознавания речи
     * @param translator сервис перевода текста
     * @param ui         поставщик текстов интерфейса
     */
    public VoiceHandler(MessageSender sender, SpeechRecognitionService speech,
                        TranslationService translator, Ui ui) {
        this.sender = sender;
        this.speech = speech;
        this.translator = translator;
        this.ui = ui;
    }

    /**
     * Проверяет, является ли сообщение голосовым.
     *
     * @param message входящее сообщение
     * @return {@code true}, если сообщение содержит голосовое вложение
     */
    @Override
    public boolean supports(UserMessage message) {
        return message.type() == MessageType.VOICE;
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
        String fileId = message.fileId();
        Integer duration = message.voiceDuration();

        if (duration != null && duration > MAX_DURATION_SECONDS) {
            sender.send(chatId, ui.voiceTooLong);
            return;
        }
        if (duration == null || duration == 0) {
            sender.send(chatId, ui.speechUnrecognized);
            return;
        }

        String recognized;
        try {
            byte[] audio = sender.download(fileId);
            recognized = speech.recognize(audio, "voice.ogg", session.getSource());

            if (recognized == null || recognized.trim().isBlank()) {
                sender.send(chatId, ui.speechUnrecognized);
                return;
            }

        } catch (SpeechUnrecognizedException e) {
            sender.send(chatId, ui.speechUnrecognized);
            return;
        } catch (Exception e) {
            log.error("Speech recognition failed for chat {}", chatId, e);
            sender.send(chatId, ui.speechUnavailable);
            return;
        }

        try {
            Language detected = translator.detect(recognized);
            if (detected != null && detected != session.getSource()) {
                sender.send(chatId, "⚠️ Распознанный язык (" + detected.label +
                        ") не совпадает с выбранным языком источника (" + session.getSource().label +
                        "). Пожалуйста, говори на правильном языке или выбери другой язык источника.");
                return;
            }

            String result = translator.translate(recognized, session.getSource(), session.getTarget());

            sender.send(chatId, ui.recognized(recognized) + "\n" + ui.translation(result),
                    ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}