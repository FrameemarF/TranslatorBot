package org.translatorBot.Stats;

import org.translatorBot.History.Platform;

import java.util.Optional;

/**
 * Статистика использования бота.
 * <p>
 * {@link #global()} считает по всем пользователям и платформам,
 * {@link #personal(Platform, long)} — по одному аккаунту.
 */
public interface StatisticsService {

    /**
     * @return общая статистика или {@link Optional#empty()} при ошибке базы
     */
    Optional<TranslationStatistics> global();

    /**
     * @param platform  платформа пользователя
     * @param accountId идентификатор аккаунта
     * @return личная статистика или {@link Optional#empty()} при ошибке базы
     */
    Optional<TranslationStatistics> personal(Platform platform, long accountId);
}
