package org.translatorBot.Telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.translatorBot.Model.AppButton;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Преобразует объекты Telegram API в универсальные доменные модели
 * и обратно. Изолирует зависимость от библиотеки Telegram.
 */
public class TelegramMapper {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Преобразует сообщение Telegram в универсальное входящее сообщение.
     *
     * @param msg сообщение от Telegram
     * @return универсальное сообщение или {@code null}, если тип не поддерживается
     */
    public UserMessage toUserMessage(Message msg) {
        long chatId = msg.getChatId();

        if (msg.hasText()) {
            return new UserMessage(chatId, msg.getText());
        }
        if (msg.hasDocument()) {
            Document doc = msg.getDocument();
            return new UserMessage(chatId, doc.getFileId(), doc.getFileName(), doc.getFileSize());
        }
        if (msg.hasVoice()) {
            Voice voice = msg.getVoice();
            return new UserMessage(chatId, voice.getFileId(), voice.getDuration());
        }

        log.warn("Unsupported message type from chat {}", chatId);
        return null;
    }

    /**
     * Преобразует нажатие кнопки Telegram в универсальный callback.
     *
     * @param q запрос от Telegram
     * @return универсальный callback
     */
    public UserCallback toUserCallback(CallbackQuery q) {
        long chatId = q.getMessage().getChatId();
        String callbackId = q.getId();
        String data = q.getData();
        return new UserCallback(chatId, callbackId, data);
    }

    /**
     * Преобразует универсальную клавиатуру в разметку клавиатуры Telegram.
     *
     * @param keyboard универсальная клавиатура
     * @return разметка Telegram или {@code null}, если клавиатуры нет
     */
    public InlineKeyboardMarkup toTelegramKeyboard(AppKeyboard keyboard) {
        if (keyboard == null) {
            return null;
        }

        List<InlineKeyboardRow> telegramRows = new ArrayList<>();
        for (List<AppButton> appRow : keyboard.rows()) {
            InlineKeyboardRow telegramRow = new InlineKeyboardRow();
            for (AppButton appButton : appRow) {
                telegramRow.add(InlineKeyboardButton.builder()
                        .text(appButton.text())
                        .callbackData(appButton.callbackData())
                        .build());
            }
            telegramRows.add(telegramRow);
        }

        return InlineKeyboardMarkup.builder().keyboard(telegramRows).build();
    }
}