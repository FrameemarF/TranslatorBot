package org.translatorBot.Stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Database.JdbcDatabase;
import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;
import org.translatorBot.Model.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Статистика на JDBC, построенная по таблице переводов.
 */
public class JdbcStatisticsService implements StatisticsService {
    /** Глубина «свежей» статистики. */
    private static final Duration RECENT_PERIOD = Duration.ofDays(7);

    /** Логгер сервиса. */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Доступ к базе данных. */
    private final JdbcDatabase database;
    /** Каталог языков для разбора ISO-кодов. */
    private final LanguageCatalog languages = new LanguageCatalog();

    /**
     * @param database доступ к базе данных
     */
    public JdbcStatisticsService(JdbcDatabase database) {
        this.database = database;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TranslationStatistics> global() {
        return load(null, 0L);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TranslationStatistics> personal(Platform platform, long accountId) {
        return load(platform, accountId);
    }

    /**
     * Собирает статистику. Если {@code platform} равен {@code null}, фильтр
     * по пользователю не применяется.
     *
     * @param platform  платформа или {@code null}
     * @param accountId идентификатор аккаунта
     * @return статистика или {@link Optional#empty()} при ошибке базы
     */
    private Optional<TranslationStatistics> load(Platform platform, long accountId) {
        String where = platform == null
                ? ""
                : " WHERE platform = ? AND account_id = ?";
        try {
            database.migrate();
            try (Connection connection = database.open()) {
                long total = count(connection, where, platform, accountId);
                Map<MessageType, Long> byType = countsByType(connection, where, platform, accountId);
                List<LanguagePair> pairs = topPairs(connection, where, platform, accountId);
                long lastWeek = countRecent(connection, where, platform, accountId);
                double average = averageLength(connection, where, platform, accountId);
                return Optional.of(new TranslationStatistics(
                        total,
                        byType.getOrDefault(MessageType.TEXT, 0L),
                        byType.getOrDefault(MessageType.DOCUMENT, 0L),
                        byType.getOrDefault(MessageType.VOICE, 0L),
                        pairs,
                        lastWeek,
                        average));
            }
        } catch (SQLException e) {
            log.error("Failed to load statistics", e);
            return Optional.empty();
        }
    }

    /**
     * Считает общее количество переводов.
     *
     * @param connection соединение
     * @param where      условие выборки
     * @param platform   платформа или {@code null}
     * @param accountId  идентификатор аккаунта
     * @return количество переводов
     * @throws SQLException при ошибке запроса
     */
    private long count(Connection connection, String where, Platform platform, long accountId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM translations" + where)) {
            bindScope(statement, platform, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    /**
     * Считает переводы по типам материала.
     *
     * @param connection соединение
     * @param where      условие выборки
     * @param platform   платформа или {@code null}
     * @param accountId  идентификатор аккаунта
     * @return количество по каждому типу
     * @throws SQLException при ошибке запроса
     */
    private Map<MessageType, Long> countsByType(Connection connection, String where,
                                               Platform platform, long accountId)
            throws SQLException {
        Map<MessageType, Long> counts = new EnumMap<>(MessageType.class);
        String sql = "SELECT material_type, COUNT(*) FROM translations" + where
                + " GROUP BY material_type";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, platform, accountId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        counts.put(MessageType.valueOf(result.getString(1)), result.getLong(2));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown material type in statistics: {}", result.getString(1));
                    }
                }
            }
        }
        return counts;
    }

    /**
     * Возвращает до трёх самых частых пар языков.
     *
     * @param connection соединение
     * @param where      условие выборки
     * @param platform   платформа или {@code null}
     * @param accountId  идентификатор аккаунта
     * @return пары языков по убыванию количества
     * @throws SQLException при ошибке запроса
     */
    private List<LanguagePair> topPairs(Connection connection, String where,
                                        Platform platform, long accountId) throws SQLException {
        List<LanguagePair> pairs = new ArrayList<>();
        String sql = "SELECT source_lang, target_lang, COUNT(*) AS cnt FROM translations"
                + where + " GROUP BY source_lang, target_lang ORDER BY cnt DESC LIMIT 3";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, platform, accountId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Optional<Language> source = languages.findByCode(result.getString(1));
                    Optional<Language> target = languages.findByCode(result.getString(2));
                    if (source.isPresent() && target.isPresent()) {
                        pairs.add(new LanguagePair(source.get(), target.get(), result.getLong(3)));
                    }
                }
            }
        }
        return pairs;
    }

    /**
     * Считает переводы за последние 7 дней.
     *
     * @param connection соединение
     * @param where      условие выборки
     * @param platform   платформа или {@code null}
     * @param accountId  идентификатор аккаунта
     * @return количество свежих переводов
     * @throws SQLException при ошибке запроса
     */
    private long countRecent(Connection connection, String where, Platform platform, long accountId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM translations"
                + condition(where, "created_at >= ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindScope(statement, platform, accountId);
            statement.setTimestamp(index, Timestamp.from(Instant.now().minus(RECENT_PERIOD)));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    /**
     * Считает среднюю длину исходного текста.
     *
     * @param connection соединение
     * @param where      условие выборки
     * @param platform   платформа или {@code null}
     * @param accountId  идентификатор аккаунта
     * @return средняя длина в символах
     * @throws SQLException при ошибке запроса
     */
    private double averageLength(Connection connection, String where,
                                 Platform platform, long accountId) throws SQLException {
        String sql = "SELECT COALESCE(AVG(LENGTH(source_text)), 0) FROM translations" + where;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, platform, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getDouble(1) : 0d;
            }
        }
    }

    /**
     * Привязывает параметры фильтра по пользователю.
     *
     * @param statement запрос
     * @param platform  платформа или {@code null}
     * @param accountId идентификатор аккаунта
     * @return индекс следующего свободного параметра
     * @throws SQLException при ошибке привязки
     */
    private int bindScope(PreparedStatement statement, Platform platform, long accountId)
            throws SQLException {
        if (platform == null) {
            return 1;
        }
        statement.setString(1, platform.name());
        statement.setLong(2, accountId);
        return 3;
    }

    /**
     * Добавляет условие к уже существующему WHERE.
     *
     * @param where       существующее условие (может быть пустым)
     * @param extra       дополнительное условие
     * @return готовое условие
     */
    private String condition(String where, String extra) {
        return where.isEmpty() ? " WHERE " + extra : where + " AND " + extra;
    }
}
