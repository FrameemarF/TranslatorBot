package org.translatorBot.User;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;

import java.time.Instant;
import java.util.Optional;

/**
 * Профили пользователей и их настройки по умолчанию.
 * <p>
 * Как и история, профиль принадлежит паре «платформа + аккаунт». Ошибки базы
 * данных возвращаются как {@link Optional#empty()} или {@code false}.
 */
public interface UserService {

    /**
     * Запоминает пользователя и обновляет его имя. Дата регистрации
     * сохраняется только при первом обращении.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @param userName  имя пользователя или {@code null}
     * @param now       текущий момент
     */
    void touch(Platform platform, long accountId, String userName, Instant now);

    /**
     * Возвращает профиль пользователя.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @return профиль или {@link Optional#empty()} при ошибке или отсутствии пользователя
     */
    Optional<UserProfile> profile(Platform platform, long accountId);

    /**
     * Сохраняет язык-цель по умолчанию.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @param target    язык или {@code null}, чтобы сбросить настройку
     * @return {@code true}, если настройка сохранена
     */
    boolean saveTarget(Platform platform, long accountId, Language target);

    /**
     * Сохраняет режим ответа по умолчанию.
     *
     * @param platform  платформа
     * @param accountId идентификатор аккаунта
     * @param mode      режим ответа
     * @return {@code true}, если настройка сохранена
     */
    boolean saveMode(Platform platform, long accountId, ResponseMode mode);
}
