package org.translatorBot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Конфигурация бота: токен Telegram и параметры LibreTranslate.
 * <p>
 * Значения загружаются из переменных окружения (приоритет) или из
 * {@code application.properties} в classpath. Для токена бота отсутствие
 * значения — критическая ошибка; URL LibreTranslate по умолчанию
 * {@code http://localhost:5000}, API-ключ опционален.
 */
public class BotConfig {
    private final String botToken;
    private final String libreTranslateUrl;
    private final String groqApiKey;
    private final String groqModel;
    private final String speechProvider;
    private final String whisperUrl;

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

    /** @return токен Telegram-бота (никогда не {@code null}) */
    public String getBotToken() { return botToken; }

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
}
