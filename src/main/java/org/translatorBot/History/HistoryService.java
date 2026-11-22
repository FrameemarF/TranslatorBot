package org.translatorBot.History;

import java.util.List;

/**
 * Хранилище истории переводов пользователей. Реализация может быть основана
 * на базе данных; интерфейс скрывает от обработчиков детали хранения.
 */
public interface HistoryService {

    /**
     * Сохраняет перевод пользователя.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @param record    сохраняемая запись
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    void save(long accountId, TranslationRecord record);

    /**
     * Возвращает идентификатор самой новой записи пользователя.
     * Используется как «граница снимка»: страницы истории не сдвигаются,
     * если во время просмотра добавляются новые переводы.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @return идентификатор последней записи или {@code 0}, если история пуста
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    long latestId(long accountId);

    /**
     * Возвращает страницу переводов пользователя от новых к старым
     * в пределах снимка истории.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @param limit     максимальное количество записей на странице
     * @param offset    количество записей, которые нужно пропустить
     * @param maxId     верхняя граница снимка: записи с большим id не попадут на страницу
     * @return список переводов; пустой, если история ещё не накопилась
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    List<TranslationRecord> recent(long accountId, int limit, int offset, long maxId);

    /**
     * Возвращает страницу переводов пользователя от новых к старым.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @param limit     максимальное количество записей на странице
     * @param offset    количество записей, которые нужно пропустить
     * @return список переводов; пустой, если история ещё не накопилась
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    default List<TranslationRecord> recent(long accountId, int limit, int offset) {
        return recent(accountId, limit, offset, Long.MAX_VALUE);
    }

    /**
     * Возвращает последние переводы пользователя от новых к старым.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @param limit     максимальное количество записей
     * @return список переводов; пустой, если история ещё не накопилась
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    default List<TranslationRecord> recent(long accountId, int limit) {
        return recent(accountId, limit, 0);
    }

    /**
     * Удаляет всю историю переводов пользователя.
     *
     * @param accountId идентификатор аккаунта пользователя
     * @throws HistoryException если база данных недоступна или запрос не выполнен
     */
    void clear(long accountId);
}
