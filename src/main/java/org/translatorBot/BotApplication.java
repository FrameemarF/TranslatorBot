package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.translatorBot.Service.Translation.LibreTranslateService;
import org.translatorBot.Service.Translation.TranslationService;

import java.util.concurrent.CountDownLatch;

/**
 * Запускает и управляет жизненным циклом Telegram-бота.
 * <p>
 * Инициализирует конфигурацию, маршрутизатор и клиент, регистрирует бота
 * в long-polling приложении и ожидает сигнала завершения.
 */
public class BotApplication {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Запускает бота.
     * <p>
     * При возникновении фатальной ошибки логирует её и завершает процесс с кодом 1.
     */
    public void run() {
        try {
            start();
        } catch (Exception e) {
            log.error("Fatal error, bot is stopping", e);
            System.exit(1);
        }
    }

    /**
     * Инициализирует зависимости, регистрирует бота и блокируется до получения
     * сигнала завершения (shutdown hook).
     *
     * @throws Exception если не удалось загрузить конфигурацию или запустить приложение
     */
    private void start() throws Exception {
        BotConfig config = new BotConfig();
        String token = config.getBotToken();

        Router router = buildRouter(config);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(
                new Thread(shutdownLatch::countDown, "shutdown-hook"));

        try (TelegramBotsLongPollingApplication app =
                     new TelegramBotsLongPollingApplication()) {

            app.registerBot(token, router);

            log.info("TranslateBot started (LibreTranslate URL: {})",
                    config.getLibreTranslateUrl());
            System.out.println("Translation bot is working");

            shutdownLatch.await();
        }

        log.info("TranslateBot stopped");
    }

    /**
     * Создаёт и настраивает {@link Router} со всеми зависимостями.
     *
     * @param config конфигурация бота
     * @return готовый маршрутизатор для обработки обновлений
     */
    Router buildRouter(BotConfig config) {
        TelegramClient client = new OkHttpTelegramClient(config.getBotToken());
        Sender sender = new Sender(client);
        SessionStore sessions = new SessionStore();
        TranslationService translator = new LibreTranslateService(
                config.getLibreTranslateUrl());
        Ui ui = new Ui();
        return new Router(sender, sessions, translator, ui);
    }
}