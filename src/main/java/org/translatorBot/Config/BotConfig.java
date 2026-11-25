package org.translatorBot.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Корневая конфигурация приложения.
 * <p>
 * Файл {@code application.properties} читается один раз здесь, а компоненты
 * получают только свою часть настроек: Telegram, Discord, база, перевод
 * и распознавание речи. Благодаря этому, например, Discord-боту
 * не передаются данные для подключения к базе данных.
 */
public class BotConfig {
    /** Настройки Telegram. */
    private final TelegramConfig telegram;
    /** Настройки Discord. */
    private final DiscordConfig discord;
    /** Настройки базы данных. */
    private final DatabaseConfig database;
    /** Настройки перевода. */
    private final TranslationConfig translation;
    /** Настройки распознавания речи. */
    private final SpeechConfig speech;

    /**
     * Загружает конфигурацию из {@code application.properties} и переменных
     * окружения (переменные имеют приоритет).
     *
     * @throws IllegalStateException если не задан обязательный токен Telegram
     */
    public BotConfig() {
        Properties props = loadProperties();

        String botToken = firstNonBlank(
                System.getenv("BOT_TOKEN"), props.getProperty("bot.token"));
        if (botToken == null) {
            throw new IllegalStateException(
                    "Bot token is missing: set BOT_TOKEN env or bot.token in application.properties");
        }

        this.telegram = new TelegramConfig(botToken);
        this.discord = new DiscordConfig(firstNonBlank(
                System.getenv("DISCORD_TOKEN"), props.getProperty("discord.token")));
        this.database = new DatabaseConfig(
                firstNonBlank(System.getenv("DATABASE_URL"), props.getProperty("database.url"),
                        "jdbc:postgresql://localhost:5432/translatorbot"),
                firstNonBlank(System.getenv("DATABASE_USER"), props.getProperty("database.user"),
                        "translatorbot"),
                firstNonBlank(System.getenv("DATABASE_PASSWORD"),
                        props.getProperty("database.password"), "translatorbot"));
        this.translation = new TranslationConfig(firstNonBlank(
                System.getenv("LIBRETRANSLATE_URL"), props.getProperty("libretranslate.url"),
                "http://localhost:5000"));
        this.speech = new SpeechConfig(
                firstNonBlank(System.getenv("SPEECH_PROVIDER"),
                        props.getProperty("speech.provider"), "groq"),
                firstNonBlank(System.getenv("GROQ_API_KEY"), props.getProperty("groq.api-key")),
                firstNonBlank(System.getenv("GROQ_MODEL"), props.getProperty("groq.model"),
                        "whisper-large-v3-turbo"),
                firstNonBlank(System.getenv("WHISPER_URL"), props.getProperty("whisper.url"),
                        "http://localhost:9000"));
    }

    /**
     * Читает {@code application.properties} из classpath.
     *
     * @return загруженные свойства (пустые, если файла нет)
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = BotConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }
        return props;
    }

    /**
     * Возвращает первое непустое (после trim) значение.
     *
     * @param values значения в порядке приоритета
     * @return первое непустое значение или {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** @return настройки Telegram */
    public TelegramConfig telegram() {
        return telegram;
    }

    /** @return настройки Discord */
    public DiscordConfig discord() {
        return discord;
    }

    /** @return настройки базы данных */
    public DatabaseConfig database() {
        return database;
    }

    /** @return настройки перевода */
    public TranslationConfig translation() {
        return translation;
    }

    /** @return настройки распознавания речи */
    public SpeechConfig speech() {
        return speech;
    }
}
