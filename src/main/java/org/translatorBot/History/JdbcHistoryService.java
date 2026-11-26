package org.translatorBot.History;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Database.JdbcDatabase;
import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;
import org.translatorBot.Model.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище истории переводов на JDBC.
 * <p>
 * Каждый вызов открывает собственное соединение, поэтому сервис безопасно
 * использовать из потоков Telegram и Discord. Объект общий для всех платформ:
 * платформа передаётся параметром и входит в ключ выборки.
 */
public class JdbcHistoryService implements HistoryService {
    /** SQL вставки новой записи. */
    private static final String INSERT =
            "INSERT INTO translations (platform, account_id, material_type, "
                    + "source_lang, target_lang, source_text, translated_text, "
                    + "material_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /** SQL выборки страницы переводов в пределах снимка. */
    private static final String SELECT_RECENT =
            "SELECT material_type, source_lang, target_lang, source_text, "
                    + "translated_text, material_name, created_at "
                    + "FROM translations WHERE platform = ? AND account_id = ? AND id <= ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?";
    /** SQL выборки идентификатора самой новой записи. */
    private static final String SELECT_LATEST_ID =
            "SELECT COALESCE(MAX(id), 0) FROM translations "
                    + "WHERE platform = ? AND account_id = ?";
    /** SQL удаления истории пользователя. */
    private static final String DELETE_ALL =
            "DELETE FROM translations WHERE platform = ? AND account_id = ?";

    /** Логгер сервиса. */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Доступ к базе данных. */
    private final JdbcDatabase database;
    /** Каталог языков для разбора ISO-кодов. */
    private final LanguageCatalog languages = new LanguageCatalog();

    /**
     * @param database доступ к базе данных
     */
    public JdbcHistoryService(JdbcDatabase database) {
        this.database = database;
    }

    /** {@inheritDoc} */
    @Override
    public boolean save(Platform platform, long accountId, TranslationRecord record) {
        try {
            database.migrate();
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(INSERT)) {
                statement.setString(1, platform.name());
                statement.setLong(2, accountId);
                statement.setString(3, record.materialType().name());
                statement.setString(4, record.source().code);
                statement.setString(5, record.target().code);
                statement.setString(6, record.sourceText());
                statement.setString(7, record.translatedText());
                statement.setString(8, record.materialName());
                statement.setTimestamp(9, Timestamp.from(record.createdAt()));
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            log.error("Failed to save translation for {}:{}", platform, accountId, e);
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Long> latestId(Platform platform, long accountId) {
        try {
            database.migrate();
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(SELECT_LATEST_ID)) {
                statement.setString(1, platform.name());
                statement.setLong(2, accountId);
                try (ResultSet result = statement.executeQuery()) {
                    return Optional.of(result.next() ? result.getLong(1) : 0L);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to read latest history id for {}:{}", platform, accountId, e);
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<TranslationRecord>> recent(Platform platform, long accountId,
                                                    int limit, int offset, long maxId) {
        try {
            database.migrate();
            List<TranslationRecord> records = new ArrayList<>();
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(SELECT_RECENT)) {
                statement.setString(1, platform.name());
                statement.setLong(2, accountId);
                statement.setLong(3, maxId);
                statement.setInt(4, limit);
                statement.setInt(5, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        readRecord(result).ifPresent(records::add);
                    }
                }
            }
            return Optional.of(records);
        } catch (SQLException e) {
            log.error("Failed to read history for {}:{}", platform, accountId, e);
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean clear(Platform platform, long accountId) {
        try {
            database.migrate();
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
                statement.setString(1, platform.name());
                statement.setLong(2, accountId);
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            log.error("Failed to clear history for {}:{}", platform, accountId, e);
            return false;
        }
    }

    /**
     * Преобразует строку результата в запись истории.
     *
     * @param result курсор на строке таблицы
     * @return запись или {@link Optional#empty()}, если язык не поддерживается
     * @throws SQLException если чтение столбца не удалось
     */
    private Optional<TranslationRecord> readRecord(ResultSet result) throws SQLException {
        Optional<Language> source = languages.findByCode(result.getString("source_lang"));
        Optional<Language> target = languages.findByCode(result.getString("target_lang"));
        if (source.isEmpty() || target.isEmpty()) {
            log.warn("Skipping history row with unsupported language");
            return Optional.empty();
        }
        Instant createdAt = result.getTimestamp("created_at").toInstant();
        return Optional.of(new TranslationRecord(
                MessageType.valueOf(result.getString("material_type")),
                source.get(),
                target.get(),
                result.getString("source_text"),
                result.getString("translated_text"),
                result.getString("material_name"),
                createdAt));
    }
}
