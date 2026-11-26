package org.translatorBot.Platforms.Discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Config.DiscordConfig;
import org.translatorBot.Handler.Router;
import org.translatorBot.Platforms.MessageSender;

import java.util.function.Function;

/**
 * Discord-бот: создаёт JDA-клиент, отправитель и регистрирует slash-команды.
 * <p>
 * Если токен не задан, бот считается выключенным и {@link #start()} ничего
 * не делает. Сценарий ({@link Router}) собирается снаружи через фабрику.
 */
public class DiscordBot {
    /** Логгер бота. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Настройки Discord. */
    private final DiscordConfig config;
    /** Преобразователь объектов Discord. */
    private final DiscordMapper mapper;
    /** Фабрика маршрутизатора для созданного отправителя. */
    private final Function<MessageSender, Router> routerFactory;

    /** Клиент Discord или {@code null}, если бот не запущен. */
    private JDA jda;

    /**
     * @param config        настройки Discord
     * @param mapper        преобразователь объектов Discord
     * @param routerFactory фабрика маршрутизатора
     */
    public DiscordBot(DiscordConfig config, DiscordMapper mapper,
                      Function<MessageSender, Router> routerFactory) {
        this.config = config;
        this.mapper = mapper;
        this.routerFactory = routerFactory;
    }

    /**
     * Запускает Discord-бота, если задан токен.
     *
     * @throws InterruptedException если ожидание готовности было прервано
     */
    public void start() throws InterruptedException {
        if (!config.enabled()) {
            log.warn("DISCORD_TOKEN is not set: Discord platform is disabled");
            return;
        }

        jda = JDABuilder.createDefault(config.token(),
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES)
                .setActivity(Activity.playing("перевод"))
                .build();
        jda.awaitReady();

        MessageSender sender = new SenderDiscord(jda);
        Router router = routerFactory.apply(sender);
        jda.addEventListener(new DiscordBotListener(router, mapper));
        registerCommands();
        log.info("Discord bot started as {}", jda.getSelfUser().getName());
    }

    /** Останавливает Discord-клиент, если бот был запущен. */
    public void stop() {
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    /** Регистрирует slash-команды Discord. */
    private void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("start", "Начать работу и показать меню"),
                Commands.slash("translate", "Начать перевод"),
                Commands.slash("profile", "Профиль и личная статистика"),
                Commands.slash("settings", "Язык-цель и режим ответа"),
                Commands.slash("stats", "Общая статистика"),
                Commands.slash("history", "Показать последние переводы"),
                Commands.slash("clear_history", "Удалить историю переводов"),
                Commands.slash("help", "Список команд"),
                Commands.slash("back", "Вернуться на предыдущий шаг")
        ).queue(null, error ->
                log.error("Failed to register Discord slash commands", error));
    }
}
