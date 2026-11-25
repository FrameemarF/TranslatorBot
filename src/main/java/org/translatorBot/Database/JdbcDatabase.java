package org.translatorBot.Database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Точка доступа к базе данных.
 * <p>
 * Открывает соединения и однократно применяет миграции. Если база недоступна,
 * исключение получает вызывающий сервис и превращает его в «пустой» результат,
 * поэтому бот продолжает работать без истории, профилей и статистики.
 */
public class JdbcDatabase {
    /** Логгер. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Настройки подключения. */
    private final DatabaseConfig config;

    /** Признак того, что миграции уже применены. */
    private volatile boolean migrated;

    /**
     * @param config настройки подключения
     */
    public JdbcDatabase(DatabaseConfig config) {
        this.config = config;
    }

    /**
     * Открывает новое соединение.
     *
     * @return соединение с базой
     * @throws SQLException если подключиться не удалось
     */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    /**
     * Применяет миграции при первом обращении. Повторные вызовы бесплатны.
     *
     * @throws SQLException если база недоступна или миграция не выполнена
     */
    public void migrate() throws SQLException {
        if (migrated) {
            return;
        }
        synchronized (this) {
            if (migrated) {
                return;
            }
            try (Connection connection = open()) {
                new DatabaseMigrator().migrate(connection);
                migrated = true;
                log.info("Database migrations applied");
            }
        }
    }
}
