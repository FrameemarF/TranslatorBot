package org.translatorBot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Модульные тесты для {@link Ui}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов.
 * Проверяются тексты сообщений и состав inline-клавиатур, включая ограничение
 * Telegram на длину {@code callback_data}.
 */
class UiTest {
    private final Ui ui = new Ui();

    /**
     * Извлекает {@code callbackData} всех кнопок клавиатуры в порядке следования.
     *
     * @param markup разметка клавиатуры
     * @return плоский список callback-данных
     */
    private List<String> callbackData(InlineKeyboardMarkup markup) {
        List<String> result = new ArrayList<>();
        markup.getKeyboard().forEach(row -> row.forEach(b -> result.add(b.getCallbackData())));
        return result;
    }

    /**
     * Извлекает подписи всех кнопок клавиатуры в порядке следования.
     *
     * @param markup разметка клавиатуры
     * @return плоский список текстов кнопок
     */
    private List<String> texts(InlineKeyboardMarkup markup) {
        List<String> result = new ArrayList<>();
        markup.getKeyboard().forEach(row -> row.forEach(b -> result.add(b.getText())));
        return result;
    }

    /** Ни одно текстовое сообщение бота не пустое. */
    @Test
    void allMessagesAreNotBlank() {
        List<String> messages = List.of(
                ui.start, ui.help, ui.askText, ui.emptyText, ui.tooLong, ui.unknownCmd,
                ui.backToMenu, ui.useStart, ui.translateErr, ui.chooseSourceLang, ui.chooseTargetLang,
                ui.voiceTooLong, ui.speechUnrecognized, ui.speechUnavailable);

        for (String m : messages) {
            Assertions.assertFalse(m.isBlank());
        }
    }

    /** Справка упоминает все команды бота. */
    @Test
    void helpMentionsAllCommands() {
        Assertions.assertTrue(ui.help.contains("/start"));
        Assertions.assertTrue(ui.help.contains("/translate"));
        Assertions.assertTrue(ui.help.contains("/help"));
        Assertions.assertTrue(ui.help.contains("/back"));
    }

    /** Сообщения о лимитах называют актуальные значения: 4000 символов и 30 секунд. */
    @Test
    void limitMessagesMentionLimits() {
        Assertions.assertTrue(ui.tooLong.contains("4000"));
        Assertions.assertTrue(ui.voiceTooLong.contains("30"));
    }

    /** Сообщения с результатом содержат переданный текст. */
    @Test
    void resultMessagesContainGivenText() {
        Assertions.assertTrue(ui.translation("Hello").contains("Hello"));
        Assertions.assertTrue(ui.recognized("Привет").contains("Привет"));
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

    /** Выбор языка: по кнопке на каждый язык, с нужным префиксом и подписью языка. */
    @Test
    void languageChoiceHasButtonPerLanguage() {
        List<String> source = Arrays.stream(Language.values()).map(l -> "SRC_" + l.name()).toList();
        List<String> target = Arrays.stream(Language.values()).map(l -> "TGT_" + l.name()).toList();
        List<String> labels = Arrays.stream(Language.values()).map(l -> l.label).toList();

        Assertions.assertEquals(source, callbackData(ui.sourceChoice()));
        Assertions.assertEquals(target, callbackData(ui.targetChoice()));
        Assertions.assertEquals(labels, texts(ui.sourceChoice()));
    }

    /** Кнопок выбора языка не больше трёх в ряду. */
    @Test
    void languageButtonsAreSplitIntoRowsOfThree() {
        for (InlineKeyboardMarkup k : List.of(ui.sourceChoice(), ui.targetChoice())) {
            k.getKeyboard().forEach(row -> Assertions.assertTrue(row.size() <= 3));
        }
    }

    /** У всех кнопок есть текст и callback-данные, а данные укладываются в лимит Telegram (64 байта). */
    @Test
    void everyButtonIsValidForTelegram() {
        List<InlineKeyboardMarkup> keyboards = List.of(
                ui.mainMenu(), ui.afterTranslation(), ui.sourceChoice(), ui.targetChoice());

        for (InlineKeyboardMarkup k : keyboards) {
            for (String t : texts(k)) Assertions.assertFalse(t.isBlank());
            for (String c : callbackData(k)) {
                Assertions.assertFalse(c.isBlank());
                Assertions.assertTrue(c.getBytes(StandardCharsets.UTF_8).length <= 64);
            }
        }
    }
}