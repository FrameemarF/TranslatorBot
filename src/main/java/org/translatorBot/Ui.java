package org.translatorBot;

import org.translatorBot.Model.AppButton;
import org.translatorBot.Model.AppKeyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит тексты сообщений и собирает клавиатуры (независимые от платформы)
 * для всех шагов диалога с пользователем. Позволяет держать весь UI-контент
 * бота в одном месте и не дублировать строки и разметку кнопок в обработчиках.
 */
public class Ui {

    /** Приветственное сообщение. */
    public final String start =
            "👋 Привет! Это бот TranslateBot.\n" +
                    "Я помогу перевести текст, файл .txt или голосовое сообщение\n" +
                    "между русским, английским, немецким, французским и испанским языками.\n" +
                    "Выбери действие:";

    /** Справка по командам. */
    public final String help =
            "ℹ️ Список команд:\n" +
                    "/start      — начало работы с ботом и вывод главного меню\n" +
                    "/translate  — начать перевод: выбор языка, на который нужно перевести\n" +
                    "/help       — помощь\n" +
                    "/back       — вернуться на предыдущий шаг";

    /** Запрос на отправку материала. */
    public final String askText = "✏️ Отправь текст, файл .txt или голосовое сообщение для перевода.\n\n" +
            "⚠️ Важно: Отправляй текст не более 4000 символов за раз. " +
            "Если текст длиннее, Telegram автоматически разобьёт его на части, " +
            "и бот сможет перевести только первую из них.";

    /** Сообщение о пустом тексте. */
    public final String emptyText    = "⚠\uFE0F Текст пустой. Отправь, пожалуйста, непустой текст.";

    /** Сообщение о превышении длины текста. */
    public final String tooLong      = "⚠\uFE0F  Текст слишком длинный (макс. 4000 символов).";

    /** Сообщение о неизвестной команде. */
    public final String unknownCmd   = "⚠\uFE0F  Команда не распознана.";

    /** Сообщение о возврате в меню. */
    public final String backToMenu   = "↩\uFE0F Возвращаю в главное меню.";

    /** Подсказка для начала работы. */
    public final String useStart     = "ℹ️ Чтобы начать, нажми /start или /translate.";

    /** Ошибка перевода. */
    public final String translateErr = "❌ Не удалось перевести. Попробуй позже.";

    /** Ошибка совпадения языков. */
    public final String sameLanguages      = "⚠\uFE0F Языки совпадают. Выбери другой целевой язык.";

    /** Сообщение о неподдерживаемом материале. */
    public final String unsupportedMaterial = "⚠\uFE0F Отправь текст, файл .txt или голосовое сообщение.";

    /** Ошибка формата файла. */
    public final String fileNotTxt = "⚠\uFE0F Поддерживаются только файлы .txt. Отправь файл в этом формате.";

    /** Ошибка размера файла. */
    public final String fileTooBig = "⚠\uFE0F Файл слишком большой. Максимальный размер — 5 МБ.\nОтправь файл поменьше.";

    /** Ошибка пустого файла. */
    public final String fileEmpty  = "⚠\uFE0F В файле нет текста для перевода.";

    /** Ошибка чтения файла. */
    public final String fileReadErr = "❌ Не удалось прочитать файл. Попробуй позже.";

    /** Успешная обработка файла. */
    public final String fileReady  = "✅ Перевод готов.";

    /** Ошибка длительности голосового. */
    public final String voiceTooLong       = "⚠\uFE0F Голосовое слишком длинное. Максимальная длительность — 30 секунд.\nОтправь сообщение покороче.";

    /** Ошибка распознавания речи. */
    public final String speechUnrecognized = "⚠\uFE0F Не удалось разобрать голосовое сообщение.\nПопробуй отправить текст.";

    /** Ошибка недоступности сервиса распознавания. */
    public final String speechUnavailable  = "⚠\uFE0F Сервис распознавания речи временно недоступен.\nПопробуй позже.";

    /** Запрос выбора языка-источника. */
    public final String chooseSourceLang = "🌐 Выбери язык, С КОТОРОГО переводить:";

    /** Запрос выбора языка-цели. */
    public final String chooseTargetLang = "🌐 Выбери язык, НА КОТОРЫЙ переводить:";

    /**
     * Формирует сообщение с результатом перевода.
     *
     * @param result переведенный текст
     * @return сообщение с результатом
     */
    public String translation(String result) { return "✅ Перевод: " + result; }

    /**
     * Формирует строку с распознанным текстом голосового сообщения.
     *
     * @param text распознанный текст
     * @return строка с распознанным текстом
     */
    public String recognized(String text) { return "🎤 Распознано: " + text; }

    /**
     * Собирает клавиатуру главного меню.
     *
     * @return клавиатура с кнопками «Перевести текст» и «Помощь»
     */
    public AppKeyboard mainMenu() {
        List<List<AppButton>> rows = new ArrayList<>();
        List<AppButton> row = new ArrayList<>();
        row.add(button("\uD83C\uDF10 Перевести текст", "TRANSLATE"));
        row.add(button("❓ Помощь", "HELP"));
        rows.add(row);
        return new AppKeyboard(rows);
    }

    /**
     * Собирает клавиатуру, показываемую после выдачи перевода.
     *
     * @return клавиатура с кнопками «Перевести ещё», «Сменить язык» и «В меню»
     */
    public AppKeyboard afterTranslation() {
        List<List<AppButton>> rows = new ArrayList<>();

        List<AppButton> firstRow = new ArrayList<>();
        firstRow.add(button("\uD83D\uDD01 Перевести ещё", "MORE"));
        firstRow.add(button("\uD83C\uDF10 Сменить язык", "CHANGE_LANG"));
        rows.add(firstRow);

        List<AppButton> secondRow = new ArrayList<>();
        secondRow.add(button("\uD83C\uDFE0 В меню", "MENU"));
        rows.add(secondRow);

        return new AppKeyboard(rows);
    }

    /**
     * Создаёт универсальную кнопку с заданным текстом и callback-данными.
     *
     * @param text отображаемый текст кнопки
     * @param data данные, приходящие в callback при нажатии
     * @return готовая кнопка
     */
    private AppButton button(String text, String data) {
        return new AppButton(text, data);
    }

    /**
     * Собирает клавиатуру выбора языка-источника.
     *
     * @return клавиатура выбора языка-источника (кнопки SRC_<код>)
     */
    public AppKeyboard sourceChoice() { return languageChoice("SRC_"); }

    /**
     * Собирает клавиатуру выбора языка-цели.
     *
     * @return клавиатура выбора языка-цели (кнопки TGT_<код>)
     */
    public AppKeyboard targetChoice() { return languageChoice("TGT_"); }

    /**
     * Строит клавиатуру из всех доступных языков по 3 кнопки в ряд.
     *
     * @param prefix префикс для callback-данных
     * @return готовая клавиатура
     */
    private AppKeyboard languageChoice(String prefix) {
        List<List<AppButton>> rows = new ArrayList<>();
        List<AppButton> row = new ArrayList<>();

        for (Language l : Language.values()) {
            if (row.size() == 3) {
                rows.add(row);
                row = new ArrayList<>();
            }
            row.add(button(l.label, prefix + l.name()));
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }

        return new AppKeyboard(rows);
    }
}