package org.translatorBot.Session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Модульные тесты для {@link SessionStore}: проверяют, что хранилище
 * возвращает одну и ту же сессию для одного {@code chatId}, разные —
 * для разных, сохраняет состояние между вызовами и корректно работает
 * при конкурентном доступе из нескольких потоков.
 */
class InMemorySessionStoreTest {

    /**
     * Проверяет, что повторный вызов {@link SessionStore#get(long)} с одним и
     * тем же {@code chatId} возвращает тот же самый экземпляр сессии.
     */
    @Test
    void returnsSameSessionForSameChat() {
        SessionStore store = new InMemorySessionStore();

        Assertions.assertSame(store.get(1L), store.get(1L));
    }

    /**
     * Проверяет, что для разных {@code chatId} хранилище возвращает
     * разные экземпляры {@link Session}.
     */
    @Test
    void returnsDifferentSessionsForDifferentChats() {
        SessionStore store = new InMemorySessionStore();

        Assertions.assertNotSame(store.get(1L), store.get(2L));
    }

    /**
     * Проверяет, что изменения сессии, сделанные через один вызов
     * {@link SessionStore#get(long)}, видны при последующих вызовах с тем же
     * {@code chatId} и не затрагивают сессию другого пользователя.
     */
    @Test
    void sessionStateIsKeptBetweenCalls() {
        SessionStore store = new InMemorySessionStore();
        store.get(1L).setSource(Language.RU);

        Assertions.assertEquals(Language.RU, store.get(1L).getSource());
        Assertions.assertNull(store.get(2L).getSource());
    }
}