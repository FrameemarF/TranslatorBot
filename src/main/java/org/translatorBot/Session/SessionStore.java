package org.translatorBot.Session;

/**
 * Хранилище сессий пользователей. Интерфейс позволяет заменить хранение
 * в памяти на базу данных, не трогая остальной код.
 */
public interface SessionStore {

    /**
     * Возвращает сессию чата; если её нет — создаёт новую.
     *
     * @param chatId идентификатор чата Telegram
     */
    Session get(long chatId);
}
