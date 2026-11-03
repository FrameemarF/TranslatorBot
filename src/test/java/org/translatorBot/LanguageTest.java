package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Тесты перечисления {@link Language}: состав, ISO-коды и заполненность названий.
 */
class LanguageTest {

    /** ISO-коды соответствуют ожидаемым значениям. */
    @Test
    void codesAreCorrect() {
        Assertions.assertEquals("ru", Language.RU.code);
        Assertions.assertEquals("en", Language.EN.code);
        Assertions.assertEquals("de", Language.DE.code);
        Assertions.assertEquals("fr", Language.FR.code);
        Assertions.assertEquals("es", Language.ES.code);
    }

    /** У каждого языка непустое отображаемое название. */
    @Test
    void labelsAreNotBlank() {
        for (Language l : Language.values()) {
            Assertions.assertFalse(l.label.isBlank());
        }
    }

    /** Поиск по имени возвращает язык или null. */
    @Test
    void findByName() {
        Assertions.assertEquals(Language.DE, Language.find("DE"));
        Assertions.assertNull(Language.find("XX"));
    }
}
