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

    /** Главное меню содержит кнопки «Перевести», «История» и «Помощь». */
    @Test
    void mainMenuHasTranslateHistoryAndHelp() {
        Assertions.assertEquals(List.of("TRANSLATE", "HISTORY", "HELP"), callbackData(ui.mainMenu()));
    }

    /** Меню истории содержит кнопки «Очистить историю» и «В меню». */
    @Test
    void historyMenuHasClearAndBack() {
        Assertions.assertEquals(List.of("CLEAR_HISTORY", "MENU"), callbackData(ui.historyMenu()));
    }

    /** На средней странице есть переходы и к более новым, и к более старым записям. */
    @Test
    void historyMenuHasBothNavigationButtons() {
        Assertions.assertEquals(
                List.of("HISTORY_PAGE_0_42", "HISTORY_PAGE_2_42", "CLEAR_HISTORY", "MENU"),
                callbackData(ui.historyMenu(1, true, true, 42)));
    }

    /** На первой странице нельзя перейти к более новым записям. */
    @Test
    void firstHistoryPageHasOnlyOlderButton() {
        Assertions.assertEquals(
                List.of("HISTORY_PAGE_1_42", "CLEAR_HISTORY", "MENU"),
                callbackData(ui.historyMenu(0, false, true, 42)));
    }

    /** На последней странице нельзя перейти к более старым записям. */
    @Test
    void lastHistoryPageHasOnlyNewerButton() {
        Assertions.assertEquals(
                List.of("HISTORY_PAGE_1_42", "CLEAR_HISTORY", "MENU"),
                callbackData(ui.historyMenu(2, true, false, 42)));
    }

    /** Пустая история предлагает перевод и возврат в меню. */
    @Test
    void emptyHistoryMenuHasTranslateAndBack() {
        Assertions.assertEquals(List.of("TRANSLATE", "MENU"), callbackData(ui.emptyHistoryMenu()));
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
                ui.mainMenu(), ui.afterTranslation(), ui.sourceChoice(), ui.targetChoice(),
                ui.historyMenu(), ui.emptyHistoryMenu());

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