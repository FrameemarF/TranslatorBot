package org.translatorBot.Session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Модульные тесты для {@link InMemorySessionStore}.
 * <p>
 * Проверяют, что хранилище возвращает одну и ту же сессию для одного
 * {@code chatId}, разные — для разных, сохраняет состояние между вызовами
 * и корректно работает при конкурентном доступе из нескольких потоков.
 */
class InMemorySessionStoreTest {

    /**
     * Проверяет, что повторный вызов {@link InMemorySessionStore#get(long)}
     * с одним и тем же {@code chatId} возвращает тот же самый экземпляр сессии.
     */
    @Test
    void returnsSameSessionForSameChat() {
        InMemorySessionStore store = new InMemorySessionStore();

        Session first = store.get(1L);
        Session second = store.get(1L);

        Assertions.assertSame(first, second);
    }

    /**
     * Проверяет, что для разных {@code chatId} хранилище возвращает
     * разные экземпляры {@link Session}.
     */
    @Test
    void returnsDifferentSessionsForDifferentChats() {
        InMemorySessionStore store = new InMemorySessionStore();

        Session first = store.get(1L);
        Session second = store.get(2L);

        Assertions.assertNotSame(first, second);
    }

    /**
     * Проверяет, что изменения сессии, сделанные через один вызов
     * {@link InMemorySessionStore#get(long)}, видны при последующих вызовах
     * с тем же {@code chatId} и не затрагивают сессию другого пользователя.
     */
    @Test
    void sessionStateIsKeptBetweenCalls() {
        InMemorySessionStore store = new InMemorySessionStore();

        store.get(1L).setSource(Language.RU);

        Assertions.assertEquals(Language.RU, store.get(1L).getSource());
        Assertions.assertNull(store.get(2L).getSource(),
                "Сессия другого чата не должна изменяться");
    }

    /**
     * Проверяет, что при одновременном обращении из нескольких потоков
     * к одному {@code chatId} создаётся только одна сессия, и все потоки
     * получают ссылку на один и тот же экземпляр.
     *
     * @throws Exception если ожидание потоков было прервано
     */
    @Test
    void concurrentAccessCreatesOnlyOneSession() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        int threadCount = 16;
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Session> seen = new ConcurrentLinkedQueue<>();
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                seen.add(store.get(42L));
            });
            threads[i].start();
        }

        start.countDown();

        for (Thread t : threads) {
            t.join(5000);
        }

        Assertions.assertEquals(threadCount, seen.size());

        Session first = seen.peek();
        for (Session s : seen) {
            Assertions.assertSame(first, s, "Все потоки должны получить один объект");
        }
    }
}