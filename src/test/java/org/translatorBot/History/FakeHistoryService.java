package org.translatorBot.History;

import java.util.ArrayList;
import java.util.List;

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
    public void save(long accountId, TranslationRecord record) {
        saveCalls++;
        lastAccountId = accountId;
        if (fail) {
            throw new HistoryException("history unavailable", null);
        }
        lastSaved = record;
        records.add(0, record);
    }

    /** {@inheritDoc} */
    @Override
    public long latestId(long accountId) {
        lastAccountId = accountId;
        if (fail) {
            throw new HistoryException("history unavailable", null);
        }
        return newestId;
    }

    /** {@inheritDoc} */
    @Override
    public List<TranslationRecord> recent(long accountId, int limit, int offset, long maxId) {
        recentCalls++;
        lastAccountId = accountId;
        lastLimit = limit;
        lastOffset = offset;
        lastMaxId = maxId;
        if (fail) {
            throw new HistoryException("history unavailable", null);
        }
        int from = Math.max(0, offset);
        if (from >= records.size()) {
            return new ArrayList<>();
        }
        int to = Math.min(from + limit, records.size());
        return new ArrayList<>(records.subList(from, to));
    }

    /** {@inheritDoc} */
    @Override
    public void clear(long accountId) {
        clearCalls++;
        lastAccountId = accountId;
        if (fail) {
            throw new HistoryException("history unavailable", null);
        }
        records.clear();
    }
}
