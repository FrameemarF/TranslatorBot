package org.translatorBot.User;

import org.translatorBot.Language;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Профиль пользователя и его настройки по умолчанию.
 *
 * @param userName    имя пользователя на платформе или {@code null}
 * @param registeredAt дата первого обращения к боту
 * @param target       язык-цель по умолчанию или {@code null}, если не задан
 * @param responseMode режим ответа
 * @param timeZone    часовой пояс пользователя (UTC, если неизвестен)
 */
public record UserProfile(
        String userName,
        Instant registeredAt,
        Language target,
        ResponseMode responseMode,
        ZoneId timeZone
) {
}
