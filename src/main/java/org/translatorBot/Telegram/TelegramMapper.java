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
import java.util.Optional;

/**
 * Преобразует объекты Telegram API в универсальные доменные модели
 * и обратно. Изолирует зависимость от библиотеки Telegram.
 */
public class TelegramMapper {
    /** Логгер маппера. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Преобразует сообщение Telegram в универсальное входящее сообщение.
     *
     * @param msg сообщение от Telegram
     * @return универсальное сообщение или {@link Optional#empty()},
     *         если тип не поддерживается
     */
    public Optional<UserMessage> toUserMessage(Message msg) {
        long chatId = msg.getChatId();
        long accountId = msg.getFrom() == null ? chatId : msg.getFrom().getId();

        if (msg.hasText()) {
            return Optional.of(new UserMessage(chatId, accountId, msg.getText()));
        }
        if (msg.hasDocument()) {
            Document doc = msg.getDocument();
            return Optional.of(new UserMessage(chatId, accountId,
                    doc.getFileId(), doc.getFileName(), doc.getFileSize()));
        }
        if (msg.hasVoice()) {
            Voice voice = msg.getVoice();
            return Optional.of(new UserMessage(chatId, accountId,
                    voice.getFileId(), voice.getDuration()));
        }

        log.warn("Unsupported message type from chat {}", chatId);
        return Optional.empty();
    }

    /**
     * Преобразует нажатие кнопки Telegram в универсальный callback.
     *
     * @param q запрос от Telegram
     * @return универсальный callback
     */
    public UserCallback toUserCallback(CallbackQuery q) {
        long chatId = q.getMessage().getChatId();
        long accountId = q.getFrom() == null ? chatId : q.getFrom().getId();
        String callbackId = q.getId();
        String data = q.getData();
        return new UserCallback(chatId, accountId, callbackId, data);
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
