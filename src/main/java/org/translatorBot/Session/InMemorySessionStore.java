package org.translatorBot.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Потокобезопасное хранилище сессий в памяти. */
public class InMemorySessionStore implements SessionStore {
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session get(long chatId) {
        return sessions.computeIfAbsent(chatId, id -> new Session());
    }
}
