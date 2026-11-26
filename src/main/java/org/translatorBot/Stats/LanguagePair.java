package org.translatorBot.Stats;

import org.translatorBot.Language;

/**
 * Пара языков и количество переводов по ней.
 *
 * @param source язык-источник
 * @param target язык-цель
 * @param count  количество переводов
 */
public record LanguagePair(Language source, Language target, long count) {
}
