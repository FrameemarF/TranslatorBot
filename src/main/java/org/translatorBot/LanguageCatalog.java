package org.translatorBot;

import java.util.Optional;

/**
 * Поиск поддерживаемых языков по имени константы и по ISO-коду.
 * <p>
 * Вынесен из {@link Language}: перечисление содержит только данные языка,
 * а операции поиска выполняются обычным (не статическим) объектом. Это
 * упрощает тестирование и соответствует требованиям к коду проекта.
 */
public class LanguageCatalog {

    /**
     * Ищет язык по имени константы ({@code "RU"}, {@code "DE"}...).
     *
     * @param name имя константы
     * @return язык или {@link Optional#empty()}, если такого языка нет
     */
    public Optional<Language> findByName(String name) {
        for (Language language : Language.values()) {
            if (language.name().equals(name)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * Ищет язык по ISO-коду ({@code "ru"}, {@code "de"}...).
     *
     * @param code ISO-код языка
     * @return язык или {@link Optional#empty()}, если код не поддерживается
     */
    public Optional<Language> findByCode(String code) {
        for (Language language : Language.values()) {
            if (language.code.equals(code)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
