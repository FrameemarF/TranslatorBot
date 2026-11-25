package org.translatorBot.History;

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
 * Каждый вызов открывает собственное соединение, поэтому объект можно
 * безопасно использовать из разных потоков Telegram и Discord. Схема
 * создаётся миграциями при первом обращении: если база недоступна, бот всё
 * равно запускается, а ошибка проявляется только при сохранении или чтении
 * истории.
 */
public class JdbcHistoryService implements HistoryService {
    /** SQL вставки новой записи. */
    private static final String INSERT =
            "INSERT INTO translations (platform, account_id, material_type, "
                    + "source_lang, target_lang, source_text, translated_text, "
                    + "material_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /** SQL выборки страницы переводов пользователя в пределах снимка. */
    private static final String SELECT_RECENT =
            "SELECT material_type, source_lang, target_lang, source_text, "
                    + "translated_text, material_name, created_at "
                    + "FROM translations WHERE platform = ? AND account_id = ? AND id <= ? "
                    + "ORDER BY id DESC LIMIT ? OFFSET ?";
    /** SQL выборки идентификатора самой новой записи пользователя. */
    private static final String SELECT_LATEST_ID =
            "SELECT COALESCE(MAX(id), 0) FROM translations "
                    + "WHERE platform = ? AND account_id = ?";
    /** SQL удаления истории пользователя. */
    private static final String DELETE_ALL =
            "DELETE FROM translations WHERE platform = ? AND account_id = ?";

    /** Доступ к базе данных. */
    private final JdbcDatabase database;
    /** Платформа, к которой относятся записи этого сервиса. */
    private final Platform platform;
    /** Каталог для разбора ISO-кодов языков из базы. */
    private final LanguageCatalog languages = new LanguageCatalog();

    /**
     * @param database доступ к базе данных
     * @param platform платформа, к которой относятся записи этого сервиса
     */
    public JdbcHistoryService(JdbcDatabase database, Platform platform) {
        this.database = database;
        this.platform = platform;
    }

    /** {@inheritDoc} */
    @Override
    public void save(long accountId, TranslationRecord record) {
        migrate();
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
        } catch (SQLException e) {
            throw new HistoryException("Failed to save translation history", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long latestId(long accountId) {
        migrate();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_LATEST_ID)) {
            statement.setString(1, platform.name());
            statement.setLong(2, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new HistoryException("Failed to read latest history id", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<TranslationRecord> recent(long accountId, int limit, int offset, long maxId) {
        migrate();
        List<TranslationRecord> records = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_RECENT)) {
            statement.setString(1, platform.name());
            statement.setLong(2, accountId);
            statement.setLong(3, maxId);
            statement.setInt(4, limit);
            statement.setInt(5, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(readRecord(resultSet));
                }
            }
            return records;
        } catch (SQLException e) {
            throw new HistoryException("Failed to read translation history", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void clear(long accountId) {
        migrate();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            statement.setString(1, platform.name());
            statement.setLong(2, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new HistoryException("Failed to clear translation history", e);
        }
    }

    /**
     * Применяет миграции схемы при первом обращении.
     *
     * @throws HistoryException если база недоступна или миграция не выполнена
     */
    private void migrate() {
        try {
            database.migrate();
        } catch (SQLException e) {
            throw new HistoryException("Failed to prepare translation history table", e);
        }
    }

    /**
     * Преобразует текущую строку результата в запись истории.
     *
     * @param resultSet курсор на строке таблицы
     * @return запись истории
     * @throws SQLException если чтение столбца не удалось
     */
    private TranslationRecord readRecord(ResultSet resultSet) throws SQLException {
        MessageType materialType = MessageType.valueOf(
                resultSet.getString("material_type"));
        Optional<Language> source = languages.findByCode(resultSet.getString("source_lang"));
        Optional<Language> target = languages.findByCode(resultSet.getString("target_lang"));
        if (source.isEmpty() || target.isEmpty()) {
            throw new HistoryException("History contains unsupported language", null);
        }
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        return new TranslationRecord(
                materialType,
                source.get(),
                target.get(),
                resultSet.getString("source_text"),
                resultSet.getString("translated_text"),
                resultSet.getString("material_name"),
                createdAt);
    }
}
