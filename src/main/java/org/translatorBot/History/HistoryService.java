package org.translatorBot.History;

import java.util.List;
import java.util.Optional;

/**
 * Хранилище истории переводов пользователей.
 * <p>
 * Ошибки базы данных не выбрасываются наружу: методы возвращают
 * {@link Optional#empty()} или {@code false}, чтобы обработчики могли показать
 * пользователю понятное сообщение и не прерывать работу бота.
 */
public interface HistoryService {

    /**
     * Сохраняет перевод пользователя.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @param record    сохраняемая запись
     * @return {@code true}, если запись сохранена
     */
    boolean save(Platform platform, long accountId, TranslationRecord record);

    /**
     * Возвращает идентификатор самой новой записи пользователя. Служит верхней
     * границей снимка, чтобы уже открытые страницы истории не сдвигались.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @return идентификатор последней записи или {@link Optional#empty()} при ошибке
     */
    Optional<Long> latestId(Platform platform, long accountId);

    /**
     * Возвращает страницу переводов от новых к старым.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @param limit     размер страницы
     * @param offset    сколько записей пропустить
     * @param maxId     верхняя граница снимка
     * @return записи страницы или {@link Optional#empty()} при ошибке
     */
    Optional<List<TranslationRecord>> recent(Platform platform, long accountId,
                                             int limit, int offset, long maxId);

    /**
     * Возвращает страницу переводов без ограничения снимка.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @param limit     размер страницы
     * @param offset    сколько записей пропустить
     * @return записи страницы или {@link Optional#empty()} при ошибке
     */
    default Optional<List<TranslationRecord>> recent(Platform platform, long accountId,
                                                     int limit, int offset) {
        return recent(platform, accountId, limit, offset, Long.MAX_VALUE);
    }

    /**
     * Возвращает последние переводы пользователя.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @param limit     максимальное количество записей
     * @return записи или {@link Optional#empty()} при ошибке
     */
    default Optional<List<TranslationRecord>> recent(Platform platform, long accountId, int limit) {
        return recent(platform, accountId, limit, 0);
    }

    /**
     * Удаляет всю историю пользователя.
     *
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @return {@code true}, если история очищена
     */
    boolean clear(Platform platform, long accountId);
}
