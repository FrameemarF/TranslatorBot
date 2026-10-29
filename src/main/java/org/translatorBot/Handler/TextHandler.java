package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;
import org.translatorBot.Language;

/**
 * Перевод обычного текстового сообщения.
 * <p>
 * Проверяет текст на пустоту и превышение лимита длины, переводит его
 * с учетом выбранных в сессии языков и отправляет результат пользователю.
 */
public class TextHandler implements MaterialHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * Создаёт обработчик текстовых сообщений.
     *
     * @param sender     отправитель сообщений
     * @param translator сервис перевода текста
     * @param ui         источник текстов и клавиатур
     */
    public TextHandler(MessageSender sender, TranslationService translator, Ui ui) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
    }

    /**
     * Проверяет, является ли сообщение текстовым.
     *
     * @param message входящее сообщение
     * @return {@code true}, если сообщение содержит текст
     */
    @Override
    public boolean supports(UserMessage message) {
        return message.type() == MessageType.TEXT;
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
            Language detected = translator.detect(text);
            if (detected != null && detected != session.getSource()) {
                sender.send(chatId, "⚠️ Язык отправленного текста (" + detected.label +
                        ") не совпадает с выбранным языком источника (" + session.getSource().label +
                        "). Пожалуйста, отправь текст на правильном языке или выбери другой язык источника.");
                return;
            }
            String result = translator.translate(text, session.getSource(), session.getTarget());
            session.setState(Session.State.IDLE);
            sender.send(chatId, ui.translation(result), ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}