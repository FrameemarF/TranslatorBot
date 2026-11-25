package org.translatorBot.History;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Config.DatabaseConfig;
import org.translatorBot.Database.JdbcDatabase;
import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

/**
 * Тесты {@link JdbcHistoryService} на встроенной H2 в режиме PostgreSQL.
 * <p>
 * Проверяют сохранение, порядок выдачи, лимит, очистку и разделение
 * истории по аккаунтам и платформам. После каждого теста база удаляется.
 */
class JdbcHistoryServiceTest {

    /** Идентификатор тестового аккаунта. */
    private final long accountId = 1L;
    /** Платформа тестового аккаунта. */
    private final Platform platform = Platform.TELEGRAM;

    /** JDBC-адрес тестовой базы. */
    private String jdbcUrl;
    /** Проверяемый сервис истории. */
    private JdbcHistoryService history;

    /** Создаёт отдельную in-memory базу для каждого теста. */
    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:history-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        JdbcDatabase database = new JdbcDatabase(new DatabaseConfig(jdbcUrl, "sa", ""));
        history = new JdbcHistoryService(database);
    }

    /** Полностью удаляет тестовую базу, а не только закрывает соединение. */
    @AfterEach
    void tearDown() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("SHUTDOWN");
        }
    }

    /** Сохранённые переводы возвращаются от новых к старым. */
    @Test
    void recentReturnsNewestFirst() {
        save("one", "uno", "2026-01-01T10:00:00Z");
        save("two", "dos", "2026-01-01T10:01:00Z");
        save("three", "tres", "2026-01-01T10:02:00Z");

        List<TranslationRecord> records = recent(10);

        Assertions.assertEquals(3, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
        Assertions.assertEquals("one", records.get(2).sourceText());
    }

    /** Лимит ограничивает количество возвращаемых записей. */
    @Test
    void recentRespectsLimit() {
        save("one", "uno", "2026-01-01T10:00:00Z");
        save("two", "dos", "2026-01-01T10:01:00Z");
        save("three", "tres", "2026-01-01T10:02:00Z");

        List<TranslationRecord> records = recent(2);

        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
    }

    /** Смещение позволяет получить следующую страницу истории. */
    @Test
    void recentSupportsOffset() {
        save("one", "uno", "2026-01-01T10:00:00Z");
        save("two", "dos", "2026-01-01T10:01:00Z");
        save("three", "tres", "2026-01-01T10:02:00Z");

        List<TranslationRecord> records = history
                .recent(platform, accountId, 1, 1)
                .orElseThrow();

        Assertions.assertEquals(1, records.size());
        Assertions.assertEquals("two", records.get(0).sourceText());
    }

    /** Смещение за пределами истории возвращает пустой список. */
    @Test
    void recentReturnsEmptyWhenOffsetIsTooLarge() {
        save("one", "uno", "2026-01-01T10:00:00Z");

        Assertions.assertTrue(history.recent(platform, accountId, 10, 10)
                .orElseThrow().isEmpty());
    }

    /** Граница снимка не пропускает записи, добавленные после открытия истории. */
    @Test
    void snapshotAnchorKeepsPageOrderStable() {
        save("one", "uno", "2026-01-01T10:00:00Z");
        save("two", "dos", "2026-01-01T10:01:00Z");
        save("three", "tres", "2026-01-01T10:02:00Z");
        long anchorId = history.latestId(platform, accountId).orElseThrow();

        save("four", "cuatro", "2026-01-01T10:03:00Z");
        List<TranslationRecord> records = history
                .recent(platform, accountId, 10, 0, anchorId)
                .orElseThrow();

        Assertions.assertEquals(3, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
        Assertions.assertEquals("one", records.get(2).sourceText());
    }

    /** Для пустой истории latestId возвращает ноль, затем растёт с новыми записями. */
    @Test
    void latestIdStartsAtZeroAndGrows() {
        Assertions.assertEquals(0L, history.latestId(platform, accountId).orElseThrow());

        save("one", "uno", "2026-01-01T10:00:00Z");
        long first = history.latestId(platform, accountId).orElseThrow();
        save("two", "dos", "2026-01-01T10:01:00Z");
        long second = history.latestId(platform, accountId).orElseThrow();

        Assertions.assertTrue(first > 0);
        Assertions.assertTrue(second > first);
    }

    /** Сохранённая запись сохраняет все поля. */
    @Test
    void savedRecordRoundTrips() {
        Instant createdAt = Instant.parse("2026-02-03T04:05:06Z");
        TranslationRecord original = new TranslationRecord(
                MessageType.DOCUMENT, Language.DE, Language.FR,
                "Hallo", "Bonjour", "notes.txt", createdAt);

        Assertions.assertTrue(history.save(platform, accountId, original));
        TranslationRecord restored = recent(1).get(0);

        Assertions.assertEquals(MessageType.DOCUMENT, restored.materialType());
        Assertions.assertEquals(Language.DE, restored.source());
        Assertions.assertEquals(Language.FR, restored.target());
        Assertions.assertEquals("Hallo", restored.sourceText());
        Assertions.assertEquals("Bonjour", restored.translatedText());
        Assertions.assertEquals("notes.txt", restored.materialName());
        Assertions.assertEquals(createdAt.toEpochMilli(), restored.createdAt().toEpochMilli());
    }

    /** История разных аккаунтов хранится раздельно. */
    @Test
    void historyIsSeparatePerAccount() {
        long otherAccount = 2L;
        save("one", "uno", "2026-01-01T10:00:00Z");
        history.save(platform, otherAccount, record("two", "dos", "2026-01-01T10:01:00Z"));

        Assertions.assertEquals(1, recent(10).size());
        Assertions.assertEquals(1, history.recent(platform, otherAccount, 10)
                .orElseThrow().size());
    }

    /** История разных платформ хранится раздельно. */
    @Test
    void historyIsSeparatePerPlatform() {
        save("one", "uno", "2026-01-01T10:00:00Z");

        Assertions.assertEquals(1, recent(10).size());
        Assertions.assertTrue(history.recent(Platform.DISCORD, accountId, 10)
                .orElseThrow().isEmpty());
    }

    /** Очистка удаляет историю только выбранного аккаунта. */
    @Test
    void clearRemovesOnlySelectedAccount() {
        long otherAccount = 2L;
        save("one", "uno", "2026-01-01T10:00:00Z");
        history.save(platform, otherAccount, record("two", "dos", "2026-01-01T10:01:00Z"));

        Assertions.assertTrue(history.clear(platform, accountId));

        Assertions.assertTrue(recent(10).isEmpty());
        Assertions.assertEquals(1, history.recent(platform, otherAccount, 10)
                .orElseThrow().size());
    }

    /** Сохраняет перевод текущего тестового аккаунта. */
    private void save(String sourceText, String translated, String createdAt) {
        history.save(platform, accountId, record(sourceText, translated, createdAt));
    }

    /** Возвращает последние переводы текущего тестового аккаунта. */
    private List<TranslationRecord> recent(int limit) {
        return history.recent(platform, accountId, limit).orElseThrow();
    }

    /**
     * Создаёт текстовую запись истории.
     *
     * @param sourceText исходный текст
     * @param translated переведённый текст
     * @param createdAt  момент перевода в формате ISO-8601
     * @return запись истории
     */
    private TranslationRecord record(String sourceText, String translated, String createdAt) {
        return new TranslationRecord(
                MessageType.TEXT, Language.RU, Language.EN,
                sourceText, translated, null, Instant.parse(createdAt));
    }
}
