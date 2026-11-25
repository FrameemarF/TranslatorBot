package org.translatorBot.History;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Тестовая заглушка {@link HistoryService}: запоминает вызовы, хранит записи
 * в памяти и позволяет имитировать недоступность базы данных.
 */
public class FakeHistoryService implements HistoryService {
    /** Количество вызовов {@link #save}. */
    public int saveCalls;
    /** Количество вызовов {@link #recent}. */
    public int recentCalls;
    /** Количество вызовов {@link #clear}. */
    public int clearCalls;
    /** Платформа, переданная в последний вызов. */
    public Platform lastPlatform;
    /** Идентификатор аккаунта, переданный в последний вызов. */
    public long lastAccountId;
    /** Лимит, переданный в последний вызов {@link #recent}. */
    public int lastLimit;
    /** Смещение, переданное в последний вызов {@link #recent}. */
    public int lastOffset;
    /** Верхняя граница снимка, переданная в последний вызов {@link #recent}. */
    public long lastMaxId;
    /** Идентификатор, который возвращает {@link #latestId}. */
    public long newestId = Long.MAX_VALUE;
    /** Последняя сохранённая запись. */
    public TranslationRecord lastSaved;
    /** Если {@code true}, все операции завершаются ошибкой. */
    public boolean fail;

    /** Записи, которые возвращает {@link #recent} (от новых к старым). */
    public final List<TranslationRecord> records = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public boolean save(Platform platform, long accountId, TranslationRecord record) {
        saveCalls++;
        lastPlatform = platform;
        lastAccountId = accountId;
        if (fail) {
            return false;
        }
        lastSaved = record;
        records.add(0, record);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Long> latestId(Platform platform, long accountId) {
        lastPlatform = platform;
        lastAccountId = accountId;
        return fail ? Optional.empty() : Optional.of(newestId);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<TranslationRecord>> recent(Platform platform, long accountId,
                                                    int limit, int offset, long maxId) {
        recentCalls++;
        lastPlatform = platform;
        lastAccountId = accountId;
        lastLimit = limit;
        lastOffset = offset;
        lastMaxId = maxId;
        if (fail) {
            return Optional.empty();
        }
        int from = Math.max(0, offset);
        if (from >= records.size()) {
            return Optional.of(new ArrayList<>());
        }
        int to = Math.min(from + limit, records.size());
        return Optional.of(new ArrayList<>(records.subList(from, to)));
    }

    /** {@inheritDoc} */
    @Override
    public boolean clear(Platform platform, long accountId) {
        clearCalls++;
        lastPlatform = platform;
        lastAccountId = accountId;
        if (fail) {
            return false;
        }
        records.clear();
        return true;
    }
}
