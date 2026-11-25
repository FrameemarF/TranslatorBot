package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Config.BotConfig;
import org.translatorBot.Config.SpeechConfig;
import org.translatorBot.Database.JdbcDatabase;
import org.translatorBot.Handler.CallbackHandler;
import org.translatorBot.Handler.CommandHandler;
import org.translatorBot.Handler.DialogFlow;
import org.translatorBot.Handler.DocumentHandler;
import org.translatorBot.Handler.HistoryHandler;
import org.translatorBot.Handler.Router;
import org.translatorBot.Handler.TextHandler;
import org.translatorBot.Handler.VoiceHandler;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.JdbcHistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.Platforms.Discord.DiscordBot;
import org.translatorBot.Platforms.Discord.DiscordMapper;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Platforms.Telegram.TelegramBot;
import org.translatorBot.Platforms.Telegram.TelegramMapper;
import org.translatorBot.Service.Speech.FallbackSpeechService;
import org.translatorBot.Service.Speech.GroqSpeechService;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.WhisperSpeechService;
import org.translatorBot.Service.Translation.LibreTranslateService;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.InMemorySessionStore;
import org.translatorBot.Session.SessionStore;

import java.util.concurrent.CountDownLatch;

/**
 * Точка сборки приложения (composition root).
 * <p>
 * Здесь один раз создаются общие сервисы — база, перевод, распознавание речи
 * и тексты интерфейса — и передаются в боты платформ через конструкторы.
 * Сами боты живут в {@link TelegramBot} и {@link DiscordBot}.
 */
public class BotApplication {
    /** Логгер приложения. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Общий доступ к базе данных. */
    private JdbcDatabase database;
    /** Общий сервис перевода. */
    private TranslationService translator;
    /** Общий сервис распознавания речи. */
    private SpeechRecognitionService speech;
    /** Общие тексты интерфейса. */
    private Ui ui;

    /**
     * Запускает ботов. Метод не выбрасывает исключения наружу: при фатальной
     * ошибке она логируется, а возвращается {@code false}.
     *
     * @return {@code true}, если боты завершились штатно; {@code false} — при ошибке
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
     * Инициализирует общие зависимости, запускает ботов и блокируется до
     * получения сигнала завершения.
     */
    private void start() {
        BotConfig config = new BotConfig();
        initServices(config);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(
                new Thread(shutdownLatch::countDown, "shutdown-hook"));

        TelegramBot telegram = new TelegramBot(config.telegram(), new TelegramMapper(),
                sender -> buildRouter(sender, Platform.TELEGRAM));
        DiscordBot discord = new DiscordBot(config.discord(), new DiscordMapper(),
                sender -> buildRouter(sender, Platform.DISCORD));

        try {
            telegram.start();
            discord.start();

            log.info("TranslateBot started (LibreTranslate URL: {})",
                    config.translation().url());
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start bot", e);
        } finally {
            discord.stop();
            telegram.stop();
        }

        log.info("TranslateBot stopped");
    }

    /**
     * Создаёт общие сервисы, которые не зависят от платформы.
     *
     * @param config корневая конфигурация
     */
    private void initServices(BotConfig config) {
        database = new JdbcDatabase(config.database());
        translator = new LibreTranslateService(config.translation().url());
        speech = buildSpeech(config.speech());
        ui = new Ui();
    }

    /**
     * Собирает маршрутизатор для платформы из общих сервисов.
     *
     * @param sender   отправитель сообщений платформы
     * @param platform платформа
     * @return готовый маршрутизатор
     */
    private Router buildRouter(MessageSender sender, Platform platform) {
        SessionStore sessions = new InMemorySessionStore();
        HistoryService history = new JdbcHistoryService(database, platform);

        DialogFlow flow = new DialogFlow(sender, ui);
        HistoryHandler historyHandler = new HistoryHandler(sender, history, ui);

        CommandHandler commands = new CommandHandler(flow, historyHandler);
        CallbackHandler callbacks = new CallbackHandler(sender, sessions, flow, historyHandler);

        return new Router(sessions, commands, callbacks, flow,
                new TextHandler(sender, translator, ui, history),
                new DocumentHandler(sender, translator, ui, history),
                new VoiceHandler(sender, speech, translator, ui, history));
    }

    /**
     * Собирает каскад распознавания речи: Groq, затем локальный Whisper.
     *
     * @param config настройки распознавания речи
     * @return сервис распознавания речи
     */
    private SpeechRecognitionService buildSpeech(SpeechConfig config) {
        if (config.groqApiKey() == null) {
            log.warn("GROQ_API_KEY is not set: speech recognition will use local Whisper only");
        }
        return new FallbackSpeechService(
                new GroqSpeechService(config.groqApiKey(), config.groqModel()),
                new WhisperSpeechService(config.whisperUrl()));
    }
}
