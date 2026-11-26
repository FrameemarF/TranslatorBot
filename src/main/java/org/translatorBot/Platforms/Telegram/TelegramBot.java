package org.translatorBot.Platforms.Telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.translatorBot.Config.TelegramConfig;
import org.translatorBot.Handler.Router;
import org.translatorBot.Platforms.MessageSender;

import java.util.function.Function;

/**
 * Telegram-бот: создаёт клиент, отправитель и long polling приложение.
 * <p>
 * Сценарий ({@link Router}) собирается снаружи через фабрику, поэтому бот
 * отвечает только за транспорт своей платформы.
 */
public class TelegramBot {
    /** Логгер бота. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Настройки Telegram. */
    private final TelegramConfig config;
    /** Преобразователь объектов Telegram. */
    private final TelegramMapper mapper;
    /** Фабрика маршрутизатора для созданного отправителя. */
    private final Function<MessageSender, Router> routerFactory;

    /** Запущенное приложение long polling или {@code null}. */
    private TelegramBotsLongPollingApplication application;

    /**
     * @param config        настройки Telegram
     * @param mapper        преобразователь объектов Telegram
     * @param routerFactory фабрика маршрутизатора
     */
    public TelegramBot(TelegramConfig config, TelegramMapper mapper,
                       Function<MessageSender, Router> routerFactory) {
        this.config = config;
        this.mapper = mapper;
        this.routerFactory = routerFactory;
    }

    /**
     * Запускает получение обновлений Telegram.
     *
     * @throws TelegramApiException если бота не удалось зарегистрировать
     */
    public void start() throws TelegramApiException {
        MessageSender sender = new SenderTG(new OkHttpTelegramClient(config.token()), mapper);
        Router router = routerFactory.apply(sender);

        application = new TelegramBotsLongPollingApplication();
        application.registerBot(config.token(), new TelegramBotAdapter(router, mapper));
        log.info("Telegram bot started");
    }

    /** Останавливает получение обновлений, если бот был запущен. */
    public void stop() {
        if (application == null) {
            return;
        }
        try {
            application.close();
        } catch (Exception e) {
            log.warn("Failed to stop Telegram application", e);
        }
    }
}
