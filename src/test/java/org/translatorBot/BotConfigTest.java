package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

/**
 * Модульные тесты для {@link BotConfig}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов.
 * {@link BotConfig} читает {@code application.properties} из classpath, поэтому
 * для каждого сценария создаётся {@link IsolatedLoader}: он подсовывает свой
 * текст файла (или его отсутствие) и заново загружает класс {@link BotConfig}.
 * Экземпляр, созданный таким загрузчиком, нельзя привести к {@link BotConfig}
 * в тесте, поэтому значения читаются через рефлексию.
 * <p>
 * Переменные окружения имеют приоритет над файлом и из теста не изменяются,
 * поэтому, если любая из них задана, все тесты пропускаются.
 */
class BotConfigTest {
    /** Полное имя тестируемого класса. */
    private final String configClass = "org.translatorBot.BotConfig";

    /**
     * Загрузчик классов, который отдаёт заданный текст как {@code application.properties}
     * и сам определяет класс {@link BotConfig}, чтобы тот использовал именно этот загрузчик.
     */
    private class IsolatedLoader extends ClassLoader {
        /** Содержимое {@code application.properties} или {@code null}, если файла «нет». */
        private final String properties;

        /**
         * @param properties текст файла настроек или {@code null}, если файл отсутствует
         */
        IsolatedLoader(String properties) {
            super(BotConfigTest.this.getClass().getClassLoader());
            this.properties = properties;
        }

        /** {@inheritDoc} */
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(configClass)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (Exception e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.equals("application.properties")) {
                return properties == null
                        ? null
                        : new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }

    /** Пропускает тесты, если заданы переменные окружения, перекрывающие настройки из файла. */
    @BeforeEach
    void requireCleanEnvironment() {
        String[] names = {"BOT_TOKEN", "LIBRETRANSLATE_URL", "GROQ_API_KEY",
                "GROQ_MODEL", "SPEECH_PROVIDER", "WHISPER_URL"};
        for (String name : names) {
            Assumptions.assumeTrue(System.getenv(name) == null, name + " is set, skipping");
        }
    }

    // ---------- помощники ----------

    /**
     * Создаёт {@link BotConfig} при заданном содержимом {@code application.properties}.
     *
     * @param properties текст файла настроек или {@code null}, если файла нет
     * @return созданный объект конфигурации
     * @throws Exception исходная ошибка конструктора, например {@link IllegalStateException}
     */
    private Object load(String properties) throws Exception {
        Class<?> type = new IsolatedLoader(properties).loadClass(configClass);
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    /**
     * Читает значение геттера конфигурации.
     *
     * @param config объект, созданный {@link #load(String)}
     * @param getter имя метода, например {@code getBotToken}
     * @return значение геттера
     * @throws Exception если вызвать метод не удалось
     */
    private String get(Object config, String getter) throws Exception {
        return (String) config.getClass().getMethod(getter).invoke(config);
    }

    // ---------- тесты ----------

    /** Все значения читаются из файла настроек. */
    @Test
    void readsAllValuesFromProperties() throws Exception {
        Object config = load("""
                bot.token=my-token
                libretranslate.url=http://libre:5000
                groq.api-key=my-key
                groq.model=my-model
                speech.provider=whisper
                whisper.url=http://whisper:9000
                """);

        Assertions.assertEquals("my-token", get(config, "getBotToken"));
        Assertions.assertEquals("http://libre:5000", get(config, "getLibreTranslateUrl"));
        Assertions.assertEquals("my-key", get(config, "getGroqApiKey"));
        Assertions.assertEquals("my-model", get(config, "getGroqModel"));
        Assertions.assertEquals("whisper", get(config, "getSpeechProvider"));
        Assertions.assertEquals("http://whisper:9000", get(config, "getWhisperUrl"));
    }

    /** Если задан только токен, остальные значения берутся по умолчанию, а ключ Groq остаётся пустым. */
    @Test
    void usesDefaultsWhenOptionalValuesAreMissing() throws Exception {
        Object config = load("bot.token=my-token");

        Assertions.assertEquals("http://localhost:5000", get(config, "getLibreTranslateUrl"));
        Assertions.assertNull(get(config, "getGroqApiKey"));
        Assertions.assertEquals("whisper-large-v3-turbo", get(config, "getGroqModel"));
        Assertions.assertEquals("groq", get(config, "getSpeechProvider"));
        Assertions.assertEquals("http://localhost:9000", get(config, "getWhisperUrl"));
    }

    /** Пустые значения в файле считаются незаданными: подставляются значения по умолчанию. */
    @Test
    void blankValuesFallBackToDefaults() throws Exception {
        Object config = load("""
                bot.token=my-token
                libretranslate.url=
                groq.api-key=
                groq.model=
                speech.provider=
                """);

        Assertions.assertEquals("http://localhost:5000", get(config, "getLibreTranslateUrl"));
        Assertions.assertNull(get(config, "getGroqApiKey"));
        Assertions.assertEquals("whisper-large-v3-turbo", get(config, "getGroqModel"));
        Assertions.assertEquals("groq", get(config, "getSpeechProvider"));
    }

    /** Пробелы вокруг значений обрезаются. */
    @Test
    void valuesAreTrimmed() throws Exception {
        Object config = load("bot.token=   my-token   \nspeech.provider=  whisper  ");

        Assertions.assertEquals("my-token", get(config, "getBotToken"));
        Assertions.assertEquals("whisper", get(config, "getSpeechProvider"));
    }

    /** Без токена бота конфигурация не создаётся: токен не задан, пуст или файла настроек нет вовсе. */
    @Test
    void missingBotTokenIsCriticalError() {
        IllegalStateException noToken = Assertions.assertThrows(IllegalStateException.class,
                () -> load("libretranslate.url=http://libre:5000"));
        Assertions.assertTrue(noToken.getMessage().contains("BOT_TOKEN"));

        Assertions.assertThrows(IllegalStateException.class, () -> load("bot.token=   "));
        Assertions.assertThrows(IllegalStateException.class, () -> load(null));
    }
}