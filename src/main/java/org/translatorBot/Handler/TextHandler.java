package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;
import org.translatorBot.History.HistoryException;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

import java.time.Instant;
import java.util.Optional;

/**
 * Перевод обычного текстового сообщения.
 * <p>
 * Проверяет текст на пустоту и превышение лимита длины, переводит его
 * с учетом выбранных в сессии языков и отправляет результат пользователю.
 * Успешный перевод сохраняется в историю.
 */
public class TextHandler implements MaterialHandler {
    /** Логгер обработчика. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Отправитель сообщений. */
    private final MessageSender sender;
    /** Сервис перевода текста. */
    private final TranslationService translator;
    /** Источник текстов и клавиатур. */
    private final Ui ui;
    /** Хранилище истории переводов. */
    private final HistoryService history;

    /**
     * Создаёт обработчик текстовых сообщений.
     *
     * @param sender     отправитель сообщений
     * @param translator сервис перевода текста
     * @param ui         источник текстов и клавиатур
     * @param history    хранилище истории переводов
     */
    public TextHandler(MessageSender sender, TranslationService translator, Ui ui,
                       HistoryService history) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
        this.history = history;
    }

    /** {@inheritDoc} */
    @Override
    public MessageType type() {
        return MessageType.TEXT;
    }

    /**
     * Обрабатывает текст: проверяет его, переводит и отправляет ответ.
     * При ошибке перевода отправляет сообщение об ошибке.
     *
     * @param message входящее сообщение с текстом
     * @param session сессия пользователя с выбранными языками
     */
    @Override
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
                sender.send(chatId, "⚠️ Язык отправленного текста (" + detected.get().label +
                        ") не совпадает с выбранным языком источника (" + session.getSource().label +
                        "). Пожалуйста, отправь текст на правильном языке или выбери другой язык источника.");
                return;
            }
            String result = translator.translate(text, session.getSource(), session.getTarget());
            session.setState(Session.State.IDLE);
            boolean saved = saveHistory(message, session, text, result);
            String reply = ui.translation(result);
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
     * Сохраняет успешный перевод в историю, не прерывая ответ пользователю
     * при ошибке хранилища.
     *
     * @param message    входящее сообщение
     * @param session    сессия с выбранными языками
     * @param sourceText исходный текст
     * @param translated переведённый текст
     * @return {@code true}, если запись сохранена
     */
    private boolean saveHistory(UserMessage message, Session session,
                                String sourceText, String translated) {
        TranslationRecord record = new TranslationRecord(
                MessageType.TEXT,
                session.getSource(),
                session.getTarget(),
                sourceText,
                translated,
                null,
                Instant.now());
        return writeRecord(message.accountId(), record);
    }

    /**
     * Записывает перевод в историю и логирует ошибку при её недоступности.
     *
     * @param accountId идентификатор аккаунта
     * @param record    запись истории
     * @return {@code true}, если запись сохранена
     */
    private boolean writeRecord(long accountId, TranslationRecord record) {
        try {
            history.save(accountId, record);
            return true;
        } catch (HistoryException e) {
            log.error("Failed to save translation history for account {}", accountId, e);
            return false;
        }
    }
}
