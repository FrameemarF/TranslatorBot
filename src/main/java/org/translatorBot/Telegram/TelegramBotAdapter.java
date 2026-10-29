package org.translatorBot.Telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.translatorBot.Handler.Router;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;

import java.util.List;

/**
 * Адаптер для получения обновлений от Telegram.
 * Принимает пачки обновлений, конвертирует их в доменные модели
 * и передает в маршрутизатор.
 */
public class TelegramBotAdapter implements LongPollingUpdateConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Router router;
    private final TelegramMapper mapper;

    /**
     * Создает адаптер для Telegram.
     *
     * @param router маршрутизатор входящих сообщений
     * @param mapper преобразователь объектов Telegram
     */
    public TelegramBotAdapter(Router router, TelegramMapper mapper) {
        this.router = router;
        this.mapper = mapper;
    }

    /**
     * Обрабатывает пачку обновлений, полученных от Telegram.
     *
     * @param updates список входящих обновлений
     */
    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            try {
                processUpdate(update);
            } catch (Exception e) {
                log.error("Failed to process update", e);
            }
        }
    }

    /**
     * Разбирает одно обновление и передает его в маршрутизатор.
     *
     * @param update входящее обновление
     */
    private void processUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            UserCallback callback = mapper.toUserCallback(update.getCallbackQuery());
            router.handleCallback(callback);
        } else if (update.hasMessage()) {
            UserMessage message = mapper.toUserMessage(update.getMessage());
            if (message != null) {
                router.handleMessage(message);
            }
        }
    }
}