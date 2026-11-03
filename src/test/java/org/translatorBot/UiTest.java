package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.translatorBot.Model.AppKeyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Модульные тесты для {@link Ui}.
 * Проверяют только логику сборки клавиатур, не проверяя текстовые константы.
 */
class UiTest {
    private final Ui ui = new Ui();

    /**
     * Извлекает {@code callbackData} всех кнопок клавиатуры в порядке следования.
     *
     * @param keyboard универсальная клавиатура
     * @return плоский список callback-данных
     */
    private List<String> callbackData(AppKeyboard keyboard) {
        List<String> result = new ArrayList<>();
        keyboard.rows().forEach(row -> row.forEach(b -> result.add(b.callbackData())));
        return result;
    }

    /**
     * Извлекает подписи всех кнопок клавиатуры в порядке следования.
     *
     * @param keyboard универсальная клавиатура
     * @return плоский список текстов кнопок
     */
    private List<String> texts(AppKeyboard keyboard) {
        List<String> result = new ArrayList<>();
        keyboard.rows().forEach(row -> row.forEach(b -> result.add(b.text())));
        return result;
    }

    /** Главное меню содержит кнопки «Перевести» и «Помощь». */
    @Test
    void mainMenuHasTranslateAndHelp() {
        Assertions.assertEquals(List.of("TRANSLATE", "HELP"), callbackData(ui.mainMenu()));
    }

    /** После перевода показываются кнопки «Ещё», «Сменить язык» и «В меню». */
    @Test
    void afterTranslationHasThreeActions() {
        Assertions.assertEquals(List.of("MORE", "CHANGE_LANG", "MENU"), callbackData(ui.afterTranslation()));
    }

    /** Выбор языка: по кнопке на каждый язык, с нужным префиксом. */
    @Test
    void languageChoiceHasButtonPerLanguage() {
        List<String> source = Arrays.stream(Language.values()).map(l -> "SRC_" + l.name()).toList();
        List<String> target = Arrays.stream(Language.values()).map(l -> "TGT_" + l.name()).toList();

        Assertions.assertEquals(source, callbackData(ui.sourceChoice()));
        Assertions.assertEquals(target, callbackData(ui.targetChoice()));
    }

    /** Кнопок выбора языка не больше трёх в ряду. */
    @Test
    void languageButtonsAreSplitIntoRowsOfThree() {
        for (AppKeyboard k : List.of(ui.sourceChoice(), ui.targetChoice())) {
            k.rows().forEach(row -> Assertions.assertTrue(row.size() <= 3));
        }
    }

    /** У всех кнопок есть текст и непустые callback-данные. */
    @Test
    void everyButtonIsValid() {
        List<AppKeyboard> keyboards = List.of(
                ui.mainMenu(), ui.afterTranslation(), ui.sourceChoice(), ui.targetChoice());

        for (AppKeyboard k : keyboards) {
            for (String t : texts(k)) {
                Assertions.assertFalse(t.isBlank());
            }
            for (String c : callbackData(k)) {
                Assertions.assertFalse(c.isBlank());
            }
        }
    }
}