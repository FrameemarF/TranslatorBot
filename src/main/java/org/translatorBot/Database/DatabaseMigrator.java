package org.translatorBot.Database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Простой мигратор схемы базы данных.
 * <p>
 * Хранит применённые версии в таблице {@code schema_migrations} и выполняет
 * SQL-файлы из {@code resources/db/migration} по порядку. Версии описаны
 * в коде явно, поэтому миграции работают и из classpath, и из jar-файла.
 */
public class DatabaseMigrator {
    /** Порядок миграций: номер версии и файл в classpath. */
    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "db/migration/V1__translations.sql"),
            new Migration(2, "db/migration/V2__users.sql"));

    /** SQL создания таблицы учёта миграций. */
    private static final String CREATE_VERSIONS =
            "CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "version INT PRIMARY KEY, "
                    + "applied_at TIMESTAMP WITH TIME ZONE NOT NULL)";

    /**
     * Применяет все ещё не применённые миграции.
     *
     * @param connection открытое соединение
     * @throws SQLException если миграцию не удалось выполнить
     */
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_VERSIONS);
        }
        for (Migration migration : MIGRATIONS) {
            if (!isApplied(connection, migration.version())) {
                apply(connection, migration);
            }
        }
    }

    /**
     * Выполняет один файл миграции и фиксирует его версию.
     *
     * @param connection соединение
     * @param migration  миграция
     * @throws SQLException если выполнение не удалось
     */
    private void apply(Connection connection, Migration migration) throws SQLException {
        for (String sql : readStatements(migration.resource())) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setTimestamp(2,
                    java.sql.Timestamp.from(java.time.Instant.now()));
            statement.executeUpdate();
        }
    }

    /**
     * Проверяет, применена ли версия.
     *
     * @param connection соединение
     * @param version    номер версии
     * @return {@code true}, если миграция уже выполнена
     * @throws SQLException при ошибке запроса
     */
    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    /**
     * Читает SQL-файл и разбивает его на отдельные команды.
     *
     * @param resource путь к файлу в classpath
     * @return список SQL-команд
     * @throws SQLException если файл не найден
     */
    private List<String> readStatements(String resource) throws SQLException {
        InputStream in = DatabaseMigrator.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new SQLException("Migration resource not found: " + resource);
        }
        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read migration " + resource, e);
        }
        return java.util.Arrays.stream(sql.toString().split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    /**
     * Описание одной миграции.
     *
     * @param version  номер версии
     * @param resource файл с SQL
     */
    private record Migration(int version, String resource) {
    }
}
