package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.time.Instant;
import java.util.Optional;

/**
 * Перевод обычного текстового сообщения.
 */
public class TextHandler {
    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final TranslationService translator;
    private final Ui ui;
    private final HistoryService history;
    private final Platform platform;

    /**
     * @param sender     отправитель сообщений
     * @param translator сервис перевода
     * @param ui         тексты интерфейса
     * @param history    история переводов
     * @param platform   платформа пользователя
     */
    public TextHandler(MessageSender sender, TranslationService translator, Ui ui,
                       HistoryService history, Platform platform) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
        this.history = history;
        this.platform = platform;
    }

    /**
     * Обрабатывает текст: проверяет, переводит и отправляет ответ.
     *
     * @param message входящее сообщение
     * @param session сессия с выбранными языками
     */
    public void handle(UserMessage message, Session session) {
        long chatId = message.chatId();
        String text = message.text().trim();

        if (text.isBlank()) {
            sender.send(chatId, ui.emptyText);
            return;
        }
        if (text.length() > TranslationService.MAX_TEXT_LENGTH) {
            sender.send(chatId, ui.tooLong);
            return;
        }
        try {
            Optional<Language> detected = translator.detect(text);
            if (detected.isPresent() && detected.get() != session.getSource()) {
                sender.send(chatId, mismatch(detected.get(), session.getSource()));
                return;
            }
            String result = translator.translate(text, session.getSource(), session.getTarget());
            session.setState(Session.State.IDLE);

            boolean saved = history.save(platform, message.accountId(), record(message, session, text, result));
            String reply = ui.translation(result);
            if (!saved) {
                reply += "\n" + ui.historySaveFailed;
            }
            sender.send(chatId, reply, ui.afterTranslation());
        } catch (TranslationException e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }

    /**
     * Сообщает о несовпадении языка текста и выбранного источника.
     *
     * @param detected обнаруженный язык
     * @param expected выбранный язык
     * @return текст предупреждения
     */
    private String mismatch(Language detected, Language expected) {
        return "⚠️ Язык отправленного текста (" + detected.label
                + ") не совпадает с выбранным языком источника (" + expected.label
                + "). Пожалуйста, отправь текст на правильном языке или выбери другой язык источника.";
    }

    /**
     * Создаёт запись истории.
     *
     * @param message    сообщение
     * @param session    сессия
     * @param sourceText исходный текст
     * @param translated перевод
     * @return запись
     */
    private TranslationRecord record(UserMessage message, Session session,
                                     String sourceText, String translated) {
        return new TranslationRecord(MessageType.TEXT, session.getSource(), session.getTarget(),
                sourceText, translated, null, Instant.now());
    }
}
