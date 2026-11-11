package org.translatorBot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.translatorBot.Discord.DiscordBotListener;
import org.translatorBot.Discord.DiscordMapper;
import org.translatorBot.Discord.SenderDiscord;
import org.translatorBot.Handler.CallbackHandler;
import org.translatorBot.Handler.CommandHandler;
import org.translatorBot.Handler.DialogFlow;
import org.translatorBot.Handler.DocumentHandler;
import org.translatorBot.Handler.HistoryHandler;
import org.translatorBot.Handler.MaterialHandler;
import org.translatorBot.Handler.Router;
import org.translatorBot.Handler.TextHandler;
import org.translatorBot.Handler.VoiceHandler;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.JdbcHistoryService;
import org.translatorBot.History.Platform;
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
 * Запускает и управляет жизненным циклом ботов Telegram и Discord.
 * <p>
 * Инициализирует конфигурацию, общий сценарий перевода и клиентов платформ,
 * регистрирует ботов и ожидает сигнала завершения.
 */
public class BotApplication {

    /** Логгер приложения. */
    private final Logger log = LoggerFactory.getLogger(getClass());

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
     * Инициализирует зависимости, регистрирует ботов и блокируется до получения
     * сигнала завершения (shutdown hook). Ошибки запуска оборачиваются
     * в {@link IllegalStateException} и обрабатываются в {@link #run()}.
     */
    private void start() {
        BotConfig config = new BotConfig();

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(
                new Thread(shutdownLatch::countDown, "shutdown-hook"));

        TelegramBotsLongPollingApplication telegram = null;
        JDA discord = null;
        try {
            telegram = startTelegram(config);
            discord = startDiscord(config);

            log.info("TranslateBot started (LibreTranslate URL: {})",
                    config.getLibreTranslateUrl());

            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bot interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start bot", e);
        } finally {
            stopDiscord(discord);
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
        HistoryService history = historyService(config, Platform.TELEGRAM);
        Router router = buildRouter(config, sender, history);

        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, new TelegramBotAdapter(router, mapper));
        return app;
    }

    /**
     * Запускает Discord-бота, если задан токен. Без токена Discord просто
     * пропускается, а бот продолжает работать в Telegram.
     *
     * @param config конфигурация бота
     * @return клиент Discord или {@code null}, если Discord не настроен
     * @throws InterruptedException если ожидание готовности было прервано
     */
    private JDA startDiscord(BotConfig config) throws InterruptedException {
        String token = config.getDiscordToken();
        if (token == null) {
            log.warn("DISCORD_TOKEN is not set: Discord platform is disabled");
            return null;
        }

        JDA jda = JDABuilder.createDefault(token,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES)
                .setActivity(net.dv8tion.jda.api.entities.Activity.playing("перевод"))
                .build();
        jda.awaitReady();

        DiscordMapper mapper = new DiscordMapper();
        MessageSender sender = new SenderDiscord(jda);
        HistoryService history = historyService(config, Platform.DISCORD);
        Router router = buildRouter(config, sender, history);
        jda.addEventListener(new DiscordBotListener(router, mapper));

        registerDiscordCommands(jda);
        log.info("Discord bot started as {}", jda.getSelfUser().getName());
        return jda;
    }

    /**
     * Регистрирует slash-команды Discord.
     *
     * @param jda клиент Discord
     */
    private void registerDiscordCommands(JDA jda) {
        jda.updateCommands().addCommands(
                Commands.slash("start", "Начать работу и показать меню"),
                Commands.slash("translate", "Начать перевод"),
                Commands.slash("history", "Показать последние переводы"),
                Commands.slash("clear_history", "Удалить историю переводов"),
                Commands.slash("help", "Список команд"),
                Commands.slash("back", "Вернуться на предыдущий шаг")
        ).queue(null, error ->
                log.error("Failed to register Discord slash commands", error));
    }

    /**
     * Останавливает Discord-клиент, если он был запущен.
     *
     * @param discord клиент Discord или {@code null}
     */
    private void stopDiscord(JDA discord) {
        if (discord != null) {
            discord.shutdownNow();
        }
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
     * Создаёт хранилище истории для указанной платформы.
     *
     * @param config   конфигурация бота
     * @param platform платформа
     * @return сервис истории
     */
    private HistoryService historyService(BotConfig config, Platform platform) {
        return new JdbcHistoryService(
                config.getDatabaseUrl(),
                config.getDatabaseUser(),
                config.getDatabasePassword(),
                platform);
    }

    /**
     * Создаёт и настраивает {@link Router} со всеми зависимостями.
     * <p>
     * Распознавание речи: сначала облачный Groq, при его недоступности
     * (лимиты, сеть, нет ключа) — автоматически локальный Whisper.
     *
     * @param config  конфигурация бота
     * @param sender  отправитель сообщений платформы
     * @param history хранилище истории платформы
     * @return готовый маршрутизатор для обработки обновлений
     */
    private Router buildRouter(BotConfig config, MessageSender sender,
                               HistoryService history) {
        SessionStore sessions = new InMemorySessionStore();
        TranslationService translator = new LibreTranslateService(
                config.getLibreTranslateUrl());
        SpeechRecognitionService speech = buildSpeech(config);

        Ui ui = new Ui();
        DialogFlow flow = new DialogFlow(sender, ui);
        HistoryHandler historyHandler = new HistoryHandler(sender, history, ui);
        List<MaterialHandler> handlers = List.of(
                new TextHandler(sender, translator, ui),
                new VoiceHandler(sender, speech, translator, ui),
                new DocumentHandler(sender, translator, ui));

        return new Router(sessions, new CommandHandler(flow, historyHandler),
                new CallbackHandler(sender, sessions, flow, historyHandler), flow, handlers);
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
