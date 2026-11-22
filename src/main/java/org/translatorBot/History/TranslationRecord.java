package org.translatorBot.History;

import org.translatorBot.Language;
import org.translatorBot.Model.MessageType;

import java.time.Instant;

/**
 * Одна запись истории переводов. Хранит тип материала, направление перевода,
 * исходный и переведённый текст, имя файла (для документов) и момент запроса.
 *
 * @param materialType   тип исходного материала
 * @param source         язык-источник
 * @param target         язык-цель
 * @param sourceText     исходный текст
 * @param translatedText переведённый текст
 * @param materialName   имя файла для документов или {@code null}
 * @param createdAt      дата и время перевода
 */
public record TranslationRecord(
        MessageType materialType,
        Language source,
        Language target,
        String sourceText,
        String translatedText,
        String materialName,
        Instant createdAt
) {
}
