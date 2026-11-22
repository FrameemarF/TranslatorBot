package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Тесты перечисления {@link Language} и каталога {@link LanguageCatalog}:
 * состав, ISO-коды, флаги и поиск языков.
 */
class LanguageTest {
    /** Каталог языков для поиска по имени и ISO-коду. */
    private final LanguageCatalog catalog = new LanguageCatalog();

    /** ISO-коды соответствуют ожидаемым значениям. */
    @Test
    void codesAreCorrect() {
        Assertions.assertEquals("ru", Language.RU.code);
        Assertions.assertEquals("en", Language.EN.code);
        Assertions.assertEquals("de", Language.DE.code);
        Assertions.assertEquals("fr", Language.FR.code);
        Assertions.assertEquals("es", Language.ES.code);
    }

    /** У каждого языка непустые название и флаг. */
    @Test
    void labelsAndFlagsAreNotBlank() {
        for (Language l : Language.values()) {
            Assertions.assertFalse(l.label.isBlank());
            Assertions.assertFalse(l.flag.isBlank());
        }
    }

    /** Поиск по имени возвращает язык или пустой Optional. */
    @Test
    void findByName() {
        Assertions.assertEquals(Language.DE, catalog.findByName("DE").orElseThrow());
        Assertions.assertTrue(catalog.findByName("XX").isEmpty());
    }

    /** Поиск по ISO-коду возвращает язык или пустой Optional. */
    @Test
    void findByCode() {
        Assertions.assertEquals(Language.FR, catalog.findByCode("fr").orElseThrow());
        Assertions.assertTrue(catalog.findByCode("zh").isEmpty());
    }
}
