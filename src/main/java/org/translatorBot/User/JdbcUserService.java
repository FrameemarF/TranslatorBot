package org.translatorBot.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Database.JdbcDatabase;
import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Хранилище профилей пользователей на JDBC.
 * <p>
 * При первом обращении создаётся запись с датой регистрации и режимом
 * «только текст». Настройки обновляются отдельными запросами.
 */
public class JdbcUserService implements UserService {
    /** Часовой пояс по умолчанию. */
    private static final ZoneId UTC = ZoneId.of("UTC");

    /** Логгер сервиса. */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Доступ к базе данных. */
    private final JdbcDatabase database;
    /** Каталог языков для разбора ISO-кодов. */
    private final LanguageCatalog languages = new LanguageCatalog();

    /**
     * @param database доступ к базе данных
     */
    public JdbcUserService(JdbcDatabase database) {
        this.database = database;
    }

    /** {@inheritDoc} */
    @Override
    public void touch(Platform platform, long accountId, String userName, Instant now) {
        try {
            database.migrate();
            if (!exists(platform, accountId)) {
                insert(platform, accountId, userName, now);
            } else if (userName != null && !userName.isBlank()) {
                updateName(platform, accountId, userName);
            }
        } catch (SQLException e) {
            log.error("Failed to register user {}:{}", platform, accountId, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserProfile> profile(Platform platform, long accountId) {
        String sql = "SELECT user_name, registered_at, target_lang, response_mode, time_zone "
                + "FROM users WHERE platform = ? AND account_id = ?";
        try {
            database.migrate();
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, platform.name());
                statement.setLong(2, accountId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readProfile(result)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load profile {}:{}", platform, accountId, e);
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean saveTarget(Platform platform, long accountId, Language target) {
        return update("UPDATE users SET target_lang = ? WHERE platform = ? AND account_id = ?",
                target == null ? null : target.code, platform, accountId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean saveMode(Platform platform, long accountId, ResponseMode mode) {
        return update("UPDATE users SET response_mode = ? WHERE platform = ? AND account_id = ?",
                mode.name(), platform, accountId);
    }

    /**
     * Выполняет обновление настройки, при необходимости создавая профиль.
     *
     * @param sql       SQL-запрос обновления
     * @param value     новое значение
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @return {@code true}, если настройка сохранена
     */
    private boolean update(String sql, String value, Platform platform, long accountId) {
        try {
            database.migrate();
            if (!exists(platform, accountId)) {
                insert(platform, accountId, null, Instant.now());
            }
            try (Connection connection = database.open();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                statement.setString(2, platform.name());
                statement.setLong(3, accountId);
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            log.error("Failed to save settings for {}:{}", platform, accountId, e);
            return false;
        }
    }

    /**
     * Проверяет, существует ли профиль.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @return {@code true}, если профиль уже создан
     * @throws SQLException при ошибке запроса
     */
    private boolean exists(Platform platform, long accountId) throws SQLException {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM users WHERE platform = ? AND account_id = ?")) {
            statement.setString(1, platform.name());
            statement.setLong(2, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /**
     * Создаёт профиль с настройками по умолчанию.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @param userName  имя пользователя или {@code null}
     * @param now       дата регистрации
     * @throws SQLException при ошибке вставки
     */
    private void insert(Platform platform, long accountId, String userName, Instant now)
            throws SQLException {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users (platform, account_id, user_name, registered_at, "
                             + "response_mode) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, platform.name());
            statement.setLong(2, accountId);
            statement.setString(3, userName);
            statement.setTimestamp(4, java.sql.Timestamp.from(now));
            statement.setString(5, ResponseMode.TEXT.name());
            statement.executeUpdate();
        }
    }

    /**
     * Обновляет имя пользователя.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @param userName  новое имя
     * @throws SQLException при ошибке обновления
     */
    private void updateName(Platform platform, long accountId, String userName) throws SQLException {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET user_name = ? WHERE platform = ? AND account_id = ?")) {
            statement.setString(1, userName);
            statement.setString(2, platform.name());
            statement.setLong(3, accountId);
            statement.executeUpdate();
        }
    }

    /**
     * Преобразует строку результата в профиль.
     *
     * @param result курсор на строке таблицы
     * @return профиль пользователя
     * @throws SQLException если чтение столбца не удалось
     */
    private UserProfile readProfile(ResultSet result) throws SQLException {
        String targetCode = result.getString("target_lang");
        Language target = targetCode == null
                ? null
                : languages.findByCode(targetCode).orElse(null);
        ResponseMode mode = ResponseMode.valueOf(result.getString("response_mode"));
        return new UserProfile(
                result.getString("user_name"),
                result.getTimestamp("registered_at").toInstant(),
                target,
                mode,
                zone(result.getString("time_zone")));
    }

    /**
     * Разбирает часовой пояс, подставляя UTC при отсутствии или ошибке.
     *
     * @param value идентификатор пояса
     * @return часовой пояс
     */
    private ZoneId zone(String value) {
        if (value == null || value.isBlank()) {
            return UTC;
        }
        try {
            return ZoneId.of(value);
        } catch (RuntimeException e) {
            log.warn("Unknown time zone {}, using UTC", value);
            return UTC;
        }
    }
}
