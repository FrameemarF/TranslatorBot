package org.translatorBot.Stats;

import java.util.List;

/**
 * Статистика использования бота: общая или по одному пользователю.
 *
 * @param total         всего переводов
 * @param text          переводов текста
 * @param document      переводов файлов
 * @param voice         переводов голосовых
 * @param topPairs      до трёх самых частых пар языков
 * @param lastWeek      переводов за последние 7 дней
 * @param averageLength средняя длина исходного текста в символах
 */
public record TranslationStatistics(
        long total,
        long text,
        long document,
        long voice,
        List<LanguagePair> topPairs,
        long lastWeek,
        double averageLength
) {

    /** @return {@code true}, если переводов ещё нет */
    public boolean isEmpty() {
        return total == 0;
    }
}
