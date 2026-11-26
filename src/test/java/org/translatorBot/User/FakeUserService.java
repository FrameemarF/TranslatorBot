package org.translatorBot.User;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;

import java.time.Instant;
import java.util.Optional;

/**
 * Тестовая заглушка {@link UserService}: хранит профиль в памяти и
 * позволяет имитировать недоступность базы данных.
 */
public class FakeUserService implements UserService {
    /** Профиль, который возвращает {@link #profile}. */
    public UserProfile profile;
    /** Количество вызовов {@link #touch}. */
    public int touchCalls;
    /** Последний сохранённый язык-цель. */
    public Language savedTarget;
    /** Последний сохранённый режим ответа. */
    public ResponseMode savedMode;
    /** Если {@code true}, операции завершаются ошибкой. */
    public boolean fail;

    /** {@inheritDoc} */
    @Override
    public void touch(Platform platform, long accountId, String userName, Instant now) {
        touchCalls++;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserProfile> profile(Platform platform, long accountId) {
        return fail ? Optional.empty() : Optional.ofNullable(profile);
    }

    /** {@inheritDoc} */
    @Override
    public boolean saveTarget(Platform platform, long accountId, Language target) {
        savedTarget = target;
        return !fail;
    }

    /** {@inheritDoc} */
    @Override
    public boolean saveMode(Platform platform, long accountId, ResponseMode mode) {
        savedMode = mode;
        return !fail;
    }
}
