package org.translatorBot.History;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;

import java.time.Instant;
import java.util.List;

/**
 * Тесты {@link JdbcHistoryService} на встроенной H2 в режиме PostgreSQL.
 * <p>
 * Проверяют сохранение, порядок выдачи, лимит, очистку и разделение
 * истории по аккаунтам и платформам.
 */
class JdbcHistoryServiceTest {

    /** Идентификатор тестового аккаунта. */
    private final long accountId = 1L;

    /** JDBC-адрес тестовой базы. */
    private String jdbcUrl;
    /** Проверяемый сервис истории. */
    private JdbcHistoryService history;

    /** Создаёт отдельную in-memory базу для каждого теста. */
    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:h2:mem:history-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        history = new JdbcHistoryService(jdbcUrl, "sa", "", Platform.TELEGRAM);
    }

    /** Сохранённые переводы возвращаются от новых к старым. */
    @Test
    void recentReturnsNewestFirst() {
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(accountId, record("two", "dos", "2026-01-01T10:01:00Z"));
        history.save(accountId, record("three", "tres", "2026-01-01T10:02:00Z"));

        List<TranslationRecord> records = history.recent(accountId, 10);

        Assertions.assertEquals(3, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
        Assertions.assertEquals("one", records.get(2).sourceText());
    }

    /** Лимит ограничивает количество возвращаемых записей. */
    @Test
    void recentRespectsLimit() {
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(accountId, record("two", "dos", "2026-01-01T10:01:00Z"));
        history.save(accountId, record("three", "tres", "2026-01-01T10:02:00Z"));

        List<TranslationRecord> records = history.recent(accountId, 2);

        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
    }

    /** Смещение позволяет получить следующую страницу истории. */
    @Test
    void recentSupportsOffset() {
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(accountId, record("two", "dos", "2026-01-01T10:01:00Z"));
        history.save(accountId, record("three", "tres", "2026-01-01T10:02:00Z"));

        List<TranslationRecord> records = history.recent(accountId, 1, 1);

        Assertions.assertEquals(1, records.size());
        Assertions.assertEquals("two", records.get(0).sourceText());
    }

    /** Смещение за пределами истории возвращает пустой список. */
    @Test
    void recentReturnsEmptyWhenOffsetIsTooLarge() {
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));

        Assertions.assertTrue(history.recent(accountId, 10, 10).isEmpty());
    }

    /** Граница снимка не пропускает записи, добавленные после открытия истории. */
    @Test
    void snapshotAnchorKeepsPageOrderStable() {
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(accountId, record("two", "dos", "2026-01-01T10:01:00Z"));
        history.save(accountId, record("three", "tres", "2026-01-01T10:02:00Z"));
        long anchorId = history.latestId(accountId);

        history.save(accountId, record("four", "cuatro", "2026-01-01T10:03:00Z"));
        List<TranslationRecord> records = history.recent(accountId, 10, 0, anchorId);

        Assertions.assertEquals(3, records.size());
        Assertions.assertEquals("three", records.get(0).sourceText());
        Assertions.assertEquals("two", records.get(1).sourceText());
        Assertions.assertEquals("one", records.get(2).sourceText());
    }

    /** Для пустой истории latestId возвращает ноль, затем растёт с новыми записями. */
    @Test
    void latestIdStartsAtZeroAndGrows() {
        Assertions.assertEquals(0, history.latestId(accountId));

        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        long first = history.latestId(accountId);
        history.save(accountId, record("two", "dos", "2026-01-01T10:01:00Z"));
        long second = history.latestId(accountId);

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

        history.save(accountId, original);
        TranslationRecord restored = history.recent(accountId, 1).get(0);

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
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(otherAccount, record("two", "dos", "2026-01-01T10:01:00Z"));

        Assertions.assertEquals(1, history.recent(accountId, 10).size());
        Assertions.assertEquals(1, history.recent(otherAccount, 10).size());
    }

    /** История разных платформ хранится раздельно. */
    @Test
    void historyIsSeparatePerPlatform() {
        JdbcHistoryService discord =
                new JdbcHistoryService(jdbcUrl, "sa", "", Platform.DISCORD);
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));

        Assertions.assertEquals(1, history.recent(accountId, 10).size());
        Assertions.assertTrue(discord.recent(accountId, 10).isEmpty());
    }

    /** Очистка удаляет историю только выбранного аккаунта. */
    @Test
    void clearRemovesOnlySelectedAccount() {
        long otherAccount = 2L;
        history.save(accountId, record("one", "uno", "2026-01-01T10:00:00Z"));
        history.save(otherAccount, record("two", "dos", "2026-01-01T10:01:00Z"));

        history.clear(accountId);

        Assertions.assertTrue(history.recent(accountId, 10).isEmpty());
        Assertions.assertEquals(1, history.recent(otherAccount, 10).size());
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
