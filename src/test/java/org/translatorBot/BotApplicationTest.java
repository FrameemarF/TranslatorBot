package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.translatorBot.Handler.Router;

/**
 * Тесты для {@link BotApplication}, проверяющие корректность
 * создания графа зависимостей (сборки {@link Router}).
 */
class BotApplicationTest {

    /**
     * Проверяет, что {@code buildRouter} возвращает не {@code null}.
     */
    @Test
    void buildRouterReturnsNonNullRouter() {
        BotApplication app = new BotApplication();
        BotConfig config = new BotConfig();

        Router router = app.buildRouter(config);

        Assertions.assertNotNull(router);
    }

    /**
     * Проверяет, что каждый вызов {@code buildRouter} создаёт новый
     * независимый экземпляр {@link Router}, а не возвращает один и тот же.
     */
    @Test
    void buildRouterCreatesIndependentInstances() {
        BotApplication app = new BotApplication();
        BotConfig config = new BotConfig();

        Router first  = app.buildRouter(config);
        Router second = app.buildRouter(config);

        Assertions.assertNotSame(first, second,
                "Каждый вызов buildRouter должен создавать новый Router");
    }

    /**
     * Проверяет, что конфигурация по умолчанию содержит токен бота.
     * Служит защитой от запуска с некорректной конфигурацией.
     */
    @Test
    void configContainsBotToken() {
        BotConfig config = new BotConfig();

        Assertions.assertNotNull(config.getBotToken(),
                "Токен бота должен быть задан в конфигурации");
    }
}