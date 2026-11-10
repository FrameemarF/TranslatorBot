package org.translatorBot.Service.Translation;

import org.translatorBot.Language;

import java.util.Optional;

/** Сервис перевода текста. */
public interface TranslationService {
    /** Максимальная длина текста, принимаемая сервисом перевода. */
    int MAX_TEXT_LENGTH = 4000;

    /**
     * Переводит текст.
     *
     * @param text текст для перевода
     * @param from исходный язык
     * @param to   целевой язык
     * @return переведённый текст
     * @throws TranslationException если сервис недоступен или вернул ошибку
     */
    String translate(String text, Language from, Language to);

    /**
     * Определяет язык текста.
     *
     * @param text текст для анализа
     * @return язык или {@link Optional#empty()}, если язык не распознан
     *         либо не поддерживается
     * @throws TranslationException если сервис недоступен или вернул ошибку
     */
    Optional<Language> detect(String text);
}
