package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.translatorBot.Handler.CallbackHandler;
import org.translatorBot.Handler.CommandHandler;
import org.translatorBot.Handler.DialogFlow;
import org.translatorBot.Handler.DocumentHandler;
import org.translatorBot.Handler.MaterialHandler;
import org.translatorBot.Handler.Router;
import org.translatorBot.Handler.TextHandler;
import org.translatorBot.Handler.VoiceHandler;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Sender.SenderTG;
import org.translatorBot.Service.Speech.FallbackSpeechService;
import org.translatorBot.Service.Speech.GroqSpeechService;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.WhisperSpeechService;
import org.translatorBot.Service.Translation.LibreTranslateService;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.SessionStore;
import org.translatorBot.Telegram.TelegramBotAdapter;
import org.translatorBot.Telegram.TelegramMapper;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Запускает и управляет жизненным циклом бота.
 * <p>
 * Инициализирует конфигурацию, общий сценарий перевода и клиента платформы,
 * регистрирует бота и ожидает сигнала завершения.
 */
public class BotApplication {

    /** Логгер приложения. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Запускает бота. Метод не выбрасывает исключения наружу: при фатальной
     * ошибке она логируется, а возвращается {@code false}.
     *
     * @return {@code true}, если бот завершился штатно; {@code false} — при ошибке
     */
    public boolean run() {
        try {
            start();
            return true;
        } catch (RuntimeException e) {
            log.error("Fatal error, bot is stopping", e);
            return false;
        }
    }

    /**
     * Инициализирует зависимости, регистрирует бота и блокируется до получения
     * сигнала завершения (shutdown hook). Ошибки запуска оборачиваются
     * в {@link IllegalStateException} и обрабатываются в {@link #run()}.
     */
    private void start() {
        BotConfig config = new BotConfig();

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(
                new Thread(shutdownLatch::countDown, "shutdown-hook"));

        TelegramBotsLongPollingApplication telegram = null;
        try {
            telegram = startTelegram(config);

            log.info("TranslateBot started (LibreTranslate URL: {})",
                    config.getLibreTranslateUrl());
            System.out.println("Translation bot is working");

            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start bot", e);
        } finally {
            stopTelegram(telegram);
        }

        log.info("TranslateBot stopped");
    }

    /**
     * Запускает Telegram-бота и возвращает приложение long polling.
     *
     * @param config конфигурация бота
     * @return запущенное приложение Telegram
     * @throws TelegramApiException если бота не удалось зарегистрировать
     */
    private TelegramBotsLongPollingApplication startTelegram(BotConfig config)
            throws TelegramApiException {
        String token = config.getBotToken();
        TelegramMapper mapper = new TelegramMapper();
        MessageSender sender = new SenderTG(new OkHttpTelegramClient(token), mapper);
        Router router = buildRouter(config, sender);

        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, new TelegramBotAdapter(router, mapper));
        return app;
    }

    /**
     * Закрывает приложение Telegram, если оно было запущено.
     *
     * @param telegram приложение Telegram или {@code null}
     */
    private void stopTelegram(TelegramBotsLongPollingApplication telegram) {
        if (telegram == null) {
            return;
        }
        try {
            telegram.close();
        } catch (Exception e) {
            log.warn("Failed to stop Telegram application", e);
        }
    }

    /**
     * Создаёт и настраивает {@link Router} со всеми зависимостями.
     * <p>
     * Распознавание речи: сначала облачный Groq, при его недоступности
     * (лимиты, сеть, нет ключа) — автоматически локальный Whisper.
     *
     * @param config конфигурация бота
     * @param sender отправитель сообщений платформы
     * @return готовый маршрутизатор для обработки обновлений
     */
    private Router buildRouter(BotConfig config, MessageSender sender) {
        SessionStore sessions = new InMemorySessionStore();
        TranslationService translator = new LibreTranslateService(
                config.getLibreTranslateUrl());
        SpeechRecognitionService speech = buildSpeech(config);

        Ui ui = new Ui();
        DialogFlow flow = new DialogFlow(sender, ui);
        List<MaterialHandler> handlers = List.of(
                new TextHandler(sender, translator, ui),
                new VoiceHandler(sender, speech, translator, ui),
                new DocumentHandler(sender, translator, ui));

        return new Router(sessions, new CommandHandler(flow),
                new CallbackHandler(sender, sessions, flow), flow, handlers);
    }

    /**
     * Собирает каскад распознавания речи: Groq, затем локальный Whisper.
     *
     * @param config конфигурация бота
     * @return сервис распознавания речи
     */
    private SpeechRecognitionService buildSpeech(BotConfig config) {
        if (config.getGroqApiKey() == null) {
            log.warn("GROQ_API_KEY is not set: speech recognition will use local Whisper only");
        }
        return new FallbackSpeechService(
                new GroqSpeechService(config.getGroqApiKey(), config.getGroqModel()),
                new WhisperSpeechService(config.getWhisperUrl()));
    }
}
