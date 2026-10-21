package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Sender;
import org.translatorBot.Session.Session;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Ui;

/**
 * Перевод обычного текстового сообщения.
 * <p>
 * Проверяет текст на пустоту и превышение лимита длины, переводит его
 * с учетом выбранных в сессии языков и отправляет результат пользователю.
 */
public class TextHandler implements MaterialHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Sender sender;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * Создаёт обработчик текстовых сообщений.
     *
     * @param sender     отправитель сообщений
     * @param translator сервис перевода текста
     * @param ui         источник текстов и клавиатур
     */
    public TextHandler(Sender sender, TranslationService translator, Ui ui) {
        this.sender = sender;
        this.translator = translator;
        this.ui = ui;
    }

    /**
     * Проверяет, является ли сообщение текстовым.
     *
     * @param message сообщение от Telegram
     * @return {@code true}, если сообщение содержит текст
     */
    @Override
    public boolean supports(Message message) {
        return message.hasText();
    }

    /**
     * Обрабатывает текст: проверяет его, переводит и отправляет ответ.
     * При ошибке перевода отправляет сообщение об ошибке.
     *
     * @param chatId  идентификатор чата
     * @param message сообщение с текстом
     * @param session сессия пользователя с выбранными языками
     */
    @Override
    public void handle(long chatId, Message message, Session session) {
        String text = message.getText().trim();
        if (text.isBlank()) {
            sender.send(chatId, ui.emptyText);
            return;
        }
        if (text.length() > TranslationService.MAX_TEXT_LENGTH) {
            sender.send(chatId, ui.tooLong);
            return;
        }
        try {
            String result = translator.translate(text, session.getSource(), session.getTarget());
            session.setState(Session.State.IDLE);
            sender.send(chatId, ui.translation(result), ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}