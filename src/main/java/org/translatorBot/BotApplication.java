package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.translatorBot.Handler.*;
import org.translatorBot.Service.Speech.FallbackSpeechService;
import org.translatorBot.Service.Speech.GroqSpeechService;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.WhisperSpeechService;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.Service.Translation.LibreTranslateService;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Telegram.TelegramBotAdapter;
import org.translatorBot.Telegram.TelegramMapper;

import java.util.List;
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
        TelegramMapper mapper = new TelegramMapper();
        TelegramBotAdapter adapter = new TelegramBotAdapter(router, mapper);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(
                new Thread(shutdownLatch::countDown, "shutdown-hook"));

        try (TelegramBotsLongPollingApplication app =
                     new TelegramBotsLongPollingApplication()) {

            app.registerBot(token, adapter);

            log.info("TranslateBot started (LibreTranslate URL: {})",
                    config.getLibreTranslateUrl());
            System.out.println("Translation bot is working");

            shutdownLatch.await();
        }

        log.info("TranslateBot stopped");
    }

    /**
     * Создаёт и настраивает {@link Router} со всеми зависимостями.
     * <p>
     * Распознавание речи: сначала облачный Groq, при его недоступности
     * (лимиты, сеть, нет ключа) — автоматически локальный Whisper.
     *
     * @param config конфигурация бота
     * @return готовый маршрутизатор для обработки обновлений
     */
    Router buildRouter(BotConfig config) {
        TelegramClient client = new OkHttpTelegramClient(config.getBotToken());

        TelegramMapper mapper = new TelegramMapper();
        MessageSender sender = new SenderTG(client, mapper);

        SessionStore sessions = new InMemorySessionStore();
        TranslationService translator = new LibreTranslateService(
                config.getLibreTranslateUrl());

        if (config.getGroqApiKey() == null) {
            log.warn("GROQ_API_KEY is not set: speech recognition will use local Whisper only");
        }
        SpeechRecognitionService speech = new FallbackSpeechService(
                new GroqSpeechService(config.getGroqApiKey(), config.getGroqModel()),
                new WhisperSpeechService(config.getWhisperUrl()));

        Ui ui = new Ui();
        DialogFlow flow = new DialogFlow(sender, ui);
        List<MaterialHandler> handlers = List.of(
                new TextHandler(sender, translator, ui),
                new VoiceHandler(sender, speech, translator, ui),
                new DocumentHandler(sender, translator, ui));

        return new Router(sessions, new CommandHandler(flow),
                new CallbackHandler(sender, sessions, flow), flow, handlers);
    }
}