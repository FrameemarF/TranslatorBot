package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.*;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.SpeechUnrecognizedException;

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

    private final Sender sender;
    private final SpeechRecognitionService speech;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * @param sender     отправитель сообщений и загрузчик файлов
     * @param speech     сервис распознавания речи
     * @param translator сервис перевода текста
     * @param ui         поставщик текстов интерфейса
     */
    public VoiceHandler(Sender sender, SpeechRecognitionService speech,
                        TranslationService translator, Ui ui) {
        this.sender = sender;
        this.speech = speech;
        this.translator = translator;
        this.ui = ui;
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(Message message) {
        return message.hasVoice();
    }

    /**
     * Обрабатывает голосовое сообщение: проверяет длительность, распознаёт речь,
     * переводит результат на целевой язык сессии и отправляет текст пользователю.
     *
     * <p>В случае ошибки распознавания или перевода отправляет соответствующее
     * сообщение об ошибке; состояние сессии при этом остаётся неизменным,
     * кроме успешного сценария, где оно сбрасывается в {@link Session.State#IDLE}.
     *
     * @param chatId  идентификатор чата
     * @param message входящее сообщение с голосовым вложением
     * @param session текущая сессия пользователя (источник и целевой язык)
     */
    @Override
    public void handle(long chatId, Message message, Session session) {
        Voice voice = message.getVoice();
        if (voice.getDuration() != null && voice.getDuration() > MAX_DURATION_SECONDS) {
            sender.send(chatId, ui.voiceTooLong);
            return;
        }

        String recognized;
        try {
            byte[] audio = sender.download(voice.getFileId());
            recognized = speech.recognize(audio, "voice.ogg", session.getSource());
        } catch (SpeechUnrecognizedException e) {
            sender.send(chatId, ui.speechUnrecognized);
            return;
        } catch (Exception e) {
            log.error("Speech recognition failed for chat {}", chatId, e);
            sender.send(chatId, ui.speechUnavailable);
            return;
        }

        try {
            String result = translator.translate(recognized, session.getSource(), session.getTarget());
            session.setState(Session.State.IDLE);
            sender.send(chatId, ui.recognized(recognized) + "\n" + ui.translation(result),
                    ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}