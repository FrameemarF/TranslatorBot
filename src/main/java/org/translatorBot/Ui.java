package org.translatorBot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит тексты сообщений и собирает inline-клавиатуры для всех шагов
 * диалога с пользователем. Позволяет держать весь UI-контент бота в одном
 * месте и не дублировать строки и разметку кнопок в обработчиках.
 */
public class Ui {
    public final String start =
            "👋 Привет! Это бот TranslateBot.\n" +
                    "Я помогу перевести текст, файл .txt или голосовое сообщение\n" +
                    "между русским, английским, немецким, французским и испанским языками.\n" +
                    "Выбери действие:";

    public final String help =
            "ℹ️ Список команд:\n" +
                    "/start      — начало работы с ботом и вывод главного меню\n" +
                    "/translate  — начать перевод: выбор языка, на который нужно перевести\n" +
                    "/help       — помощь\n" +
                    "/back       — вернуться на предыдущий шаг";

    public final String askText      = "✏️ Отправь текст, файл .txt или голосовое сообщение для перевода.";
    public final String emptyText    = "⚠\uFE0F Текст пустой. Отправь, пожалуйста, непустой текст.";
    public final String tooLong      = "⚠\uFE0F  Текст слишком длинный (макс. 4000 символов).";
    public final String unknownCmd   = "⚠\uFE0F  Команда не распознана.";
    public final String backToMenu   = "↩\uFE0F Возвращаю в главное меню.";
    public final String useStart     = "ℹ️ Чтобы начать, нажми /start или /translate.";
    public final String translateErr = "❌ Не удалось перевести. Попробуй позже.";

    public final String sameLanguages      = "⚠\uFE0F Языки совпадают. Выбери другой целевой язык.";
    public final String unsupportedMaterial = "⚠\uFE0F Отправь текст, файл .txt или голосовое сообщение.";

    public final String fileNotTxt = "⚠\uFE0F Поддерживаются только файлы .txt. Отправь файл в этом формате.";
    public final String fileTooBig = "⚠\uFE0F Файл слишком большой. Максимальный размер — 5 МБ.\nОтправь файл поменьше.";
    public final String fileEmpty  = "⚠\uFE0F В файле нет текста для перевода.";
    public final String fileReadErr = "❌ Не удалось прочитать файл. Попробуй позже.";
    public final String fileReady  = "✅ Перевод готов.";

    public final String voiceTooLong       = "⚠\uFE0F Голосовое слишком длинное. Максимальная длительность — 30 секунд.\nОтправь сообщение покороче.";
    public final String speechUnrecognized = "⚠\uFE0F Не удалось разобрать голосовое сообщение.\nПопробуй отправить текст.";
    public final String speechUnavailable  = "⚠\uFE0F Сервис распознавания речи временно недоступен.\nПопробуй позже.";

    /** @return сообщение с результатом перевода */
    public String translation(String result) { return "✅ Перевод: " + result; }

    /** @return строка с распознанным текстом голосового */
    public String recognized(String text) { return "🎤 Распознано: " + text; }

    /**
     * Собирает клавиатуру главного меню.
     *
     * @return разметка с кнопками «Перевести текст» и «Помощь»
     */
    public InlineKeyboardMarkup mainMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("\uD83C\uDF10 Перевести текст", "TRANSLATE"),
                        button("❓ Помощь", "HELP")))
                .build();
    }

    /**
     * Собирает клавиатуру, показываемую после выдачи перевода.
     *
     * @return разметка с кнопками «Перевести ещё», «Сменить язык» и «В меню»
     */
    public InlineKeyboardMarkup afterTranslation() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("\uD83D\uDD01 Перевести ещё", "MORE"),
                        button("\uD83C\uDF10 Сменить язык", "CHANGE_LANG")))
                .keyboardRow(new InlineKeyboardRow(
                        button("\uD83C\uDFE0 В меню", "MENU")))
                .build();
    }

    /**
     * Создаёт inline-кнопку с заданным текстом и callback-данными.
     *
     * @param text отображаемый текст кнопки
     * @param data данные, приходящие в callback при нажатии
     * @return готовая inline-кнопка
     */
    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    public String chooseSourceLang = "🌐 Выбери язык, С КОТОРОГО переводить:";
    public String chooseTargetLang = "🌐 Выбери язык, НА КОТОРЫЙ переводить:";

    /** @return клавиатура выбора языка-источника (кнопки {@code SRC_<код>}) */
    public InlineKeyboardMarkup sourceChoice() { return languageChoice("SRC_"); }

    /** @return клавиатура выбора языка-цели (кнопки {@code TGT_<код>}) */
    public InlineKeyboardMarkup targetChoice() { return languageChoice("TGT_"); }

    /** Строит клавиатуру из всех {@link Language} по 3 кнопки в ряд. */
    private InlineKeyboardMarkup languageChoice(String prefix) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (Language l : Language.values()) {
            if (row.size() == 3) {
                rows.add(row);
                row = new InlineKeyboardRow();
            }
            row.add(button(l.label, prefix + l.name()));
        }
        rows.add(row);
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
