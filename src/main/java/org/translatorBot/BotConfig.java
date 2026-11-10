package org.translatorBot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Конфигурация бота: токены Telegram и Discord, параметры LibreTranslate,
 * распознавания речи и базы данных.
 * <p>
 * Значения загружаются из переменных окружения (приоритет) или из
 * {@code application.properties} в classpath. Для токена Telegram отсутствие
 * значения — критическая ошибка; токен Discord опционален: без него бот
 * работает только в Telegram.
 */
public class BotConfig {
    /** Токен Telegram-бота. */
    private final String botToken;
    /** Токен Discord-бота или {@code null}, если Discord не настроен. */
    private final String discordToken;
    /** Базовый URL LibreTranslate. */
    private final String libreTranslateUrl;
    /** API-ключ Groq. */
    private final String groqApiKey;
    /** Модель Whisper на Groq. */
    private final String groqModel;
    /** Провайдер распознавания речи. */
    private final String speechProvider;
    /** Адрес локального Whisper-сервиса. */
    private final String whisperUrl;
    /** JDBC-адрес базы данных истории. */
    private final String databaseUrl;
    /** Имя пользователя базы данных. */
    private final String databaseUser;
    /** Пароль пользователя базы данных. */
    private final String databasePassword;

    /**
     * Загружает конфигурацию из {@code application.properties} и переменных окружения.
     *
     * @throws IllegalStateException если файл не удалось прочитать или не задан токен бота
     */
    public BotConfig() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }

        this.botToken = firstNonBlank(
                System.getenv("BOT_TOKEN"),
                props.getProperty("bot.token"));
        if (botToken == null) {
            throw new IllegalStateException(
                    "Bot token is missing: set BOT_TOKEN env or bot.token in application.properties");
        }

        this.discordToken = firstNonBlank(
                System.getenv("DISCORD_TOKEN"),
                props.getProperty("discord.token"));

        this.libreTranslateUrl = firstNonBlank(
                System.getenv("LIBRETRANSLATE_URL"),
                props.getProperty("libretranslate.url"),
                "http://localhost:5000");

        this.groqApiKey = firstNonBlank(
                System.getenv("GROQ_API_KEY"),
                props.getProperty("groq.api-key"));

        this.groqModel = firstNonBlank(
                System.getenv("GROQ_MODEL"),
                props.getProperty("groq.model"),
                "whisper-large-v3-turbo");

        this.speechProvider = firstNonBlank(
                System.getenv("SPEECH_PROVIDER"),
                props.getProperty("speech.provider"),
                "groq");

        this.whisperUrl = firstNonBlank(
                System.getenv("WHISPER_URL"),
                props.getProperty("whisper.url"),
                "http://localhost:9000");

        this.databaseUrl = firstNonBlank(
                System.getenv("DATABASE_URL"),
                props.getProperty("database.url"),
                "jdbc:postgresql://localhost:5432/translatorbot");

        this.databaseUser = firstNonBlank(
                System.getenv("DATABASE_USER"),
                props.getProperty("database.user"),
                "translatorbot");

        this.databasePassword = firstNonBlank(
                System.getenv("DATABASE_PASSWORD"),
                props.getProperty("database.password"),
                "translatorbot");
    }

    /**
     * Возвращает первое непустое (после trim) значение из переданных.
     *
     * @param values значения для проверки в порядке приоритета
     * @return первое непустое значение или {@code null}, если таких нет
     */
    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    /** @return токен Telegram-бота (никогда {@code null}) */
    public String getBotToken() { return botToken; }

    /** @return токен Discord-бота или {@code null}, если Discord не настроен */
    public String getDiscordToken() { return discordToken; }

    /** @return базовый URL LibreTranslate (по умолчанию {@code http://localhost:5000}) */
    public String getLibreTranslateUrl() { return libreTranslateUrl; }

    /** @return API-ключ Groq (распознавание речи) или {@code null}, если не задан */
    public String getGroqApiKey() { return groqApiKey; }

    /** @return модель Whisper на Groq (по умолчанию {@code whisper-large-v3-turbo}) */
    public String getGroqModel() { return groqModel; }

    /** @return провайдер распознавания речи: {@code groq} или {@code whisper} */
    public String getSpeechProvider() { return speechProvider; }

    /** @return адрес локального Whisper-сервиса */
    public String getWhisperUrl() { return whisperUrl; }

    /** @return JDBC-адрес базы данных истории */
    public String getDatabaseUrl() { return databaseUrl; }

    /** @return имя пользователя базы данных */
    public String getDatabaseUser() { return databaseUser; }

    /** @return пароль пользователя базы данных */
    public String getDatabasePassword() { return databasePassword; }
}
