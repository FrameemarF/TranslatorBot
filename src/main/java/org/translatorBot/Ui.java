package org.translatorBot;

import org.translatorBot.History.Platform;
import org.translatorBot.Model.AppButton;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Stats.LanguagePair;
import org.translatorBot.Stats.TranslationStatistics;
import org.translatorBot.User.ResponseMode;
import org.translatorBot.User.UserProfile;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит тексты сообщений и собирает клавиатуры (независимые от платформы)
 * для всех шагов диалога с пользователем. Позволяет держать весь UI-контент
 * бота в одном месте и не дублировать строки и разметку кнопок в обработчиках.
 */
public class Ui {
    /** Префикс callback-данных для перехода на страницу истории. */
    public static final String HISTORY_PAGE_PREFIX = "HISTORY_PAGE_";
    /** Префикс callback-данных для выбора языка-цели в настройках. */
    public static final String SET_TARGET_PREFIX = "SET_TARGET_";
    /** Значение «язык-цель не задан». */
    public static final String SET_TARGET_NONE = SET_TARGET_PREFIX + "NONE";
    /** Callback-данные режима «только текст». */
    public static final String SET_MODE_TEXT = "SET_MODE_TEXT";
    /** Callback-данные режима «текст + голос». */
    public static final String SET_MODE_VOICE = "SET_MODE_VOICE";

    /** Формат даты и времени в истории и профиле. */
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    /** Формат даты регистрации. */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Приветственное сообщение. */
    public final String start =
            "👋 Привет! Это бот TranslateBot.\n" +
                    "Я помогу перевести текст, файл .txt или голосовое сообщение\n" +
                    "между разными языками.\n" +
                    "Выбери действие:";

    /** Справка по командам. */
    public final String help =
            "ℹ️ Список команд:\n" +
                    "/start          — начало работы с ботом и вывод главного меню\n" +
                    "/translate      — начать перевод\n" +
                    "/profile        — профиль и личная статистика\n" +
                    "/settings       — язык-цель и режим ответа по умолчанию\n" +
                    "/stats          — общая статистика использования\n" +
                    "/history        — показать последние переводы\n" +
                    "/clear_history  — удалить историю переводов\n" +
                    "/help           — помощь\n" +
                    "/back           — вернуться на предыдущий шаг";

    /** Заголовок списка истории переводов. */
    public final String historyHeader = "🕘 Последние переводы";
    /** Сообщение о пустой истории. */
    public final String historyEmpty = "📭 История пока пуста. Переведи что-нибудь, и оно появится здесь.";
    /** Сообщение об успешной очистке истории. */
    public final String historyCleared = "🗑 История переводов удалена.";
    /** Ошибка доступа к истории. */
    public final String historyUnavailable = "⚠️ Сейчас не удаётся показать историю. Попробуй позже.";
    /** Предупреждение о том, что перевод не сохранился в историю. */
    public final String historySaveFailed = "⚠️ Не удалось сохранить перевод в историю.";

    /** Заголовок профиля. */
    public final String profileHeader = "👤 Профиль";
    /** Ошибка загрузки профиля. */
    public final String profileUnavailable = "⚠️ Сейчас не удаётся показать профиль. Попробуй позже.";
    /** Заголовок настроек. */
    public final String settingsHeader = "⚙️ Настройки";
    /** Ошибка загрузки настроек. */
    public final String settingsUnavailable = "⚠️ Сейчас не удаётся открыть настройки. Попробуй позже.";
    /** Подтверждение сохранения настроек. */
    public final String settingsSavedText = "✅ Настройки сохранены.";
    /** Заголовок общей статистики. */
    public final String statsHeader = "📊 Общая статистика";
    /** Сообщение о пустой статистике. */
    public final String statsEmpty = "📭 Статистика пока пуста. Переведи что-нибудь, и она появится здесь.";
    /** Ошибка загрузки статистики. */
    public final String statsUnavailable = "⚠️ Сейчас не удаётся показать статистику. Попробуй позже.";

    /** Значение «язык-цель не задан». */
    public final String notSet = "не задан";
    /** Подпись кнопки сброса языка-цели. */
    public final String noTarget = "Не задан";

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
    public String translation(String result) {
        return "✅ Перевод: " + result;
    }

    /**
     * Формирует строку с распознанным текстом голосового сообщения.
     *
     * @param text распознанный текст
     * @return строка с распознанным текстом
     */
    public String recognized(String text) {
        return "🎤 Распознано: " + text;
    }

    /**
     * Формирует заголовок страницы истории.
     *
     * @param page номер страницы, начиная с нуля
     * @return заголовок вида «🕘 Последние переводы (Страница 1):»
     */
    public String historyTitle(int page) {
        return historyHeader + " (Страница " + (page + 1) + "):";
    }

    /**
     * Форматирует одну запись истории.
     *
     * @param number порядковый номер
     * @param record запись истории
     * @param zone   часовой пояс пользователя
     * @return строка истории
     */
    public String historyRecord(int number, org.translatorBot.History.TranslationRecord record,
                                ZoneId zone) {
        String direction = record.source().flag + "→" + record.target().flag;
        String body = record.materialType() == org.translatorBot.Model.MessageType.DOCUMENT
                ? safe(record.materialName())
                : preview(record.sourceText()) + " → " + preview(record.translatedText());
        return number + ". " + icon(record.materialType()) + " " + direction + " "
                + DATE_TIME.format(record.createdAt().atZone(zone)) + " " + body;
    }

    /**
     * Собирает профиль пользователя вместе с личной статистикой.
     *
     * @param profile  профиль
     * @param platform платформа пользователя
     * @param stats    личная статистика
     * @return готовый текст профиля
     */
    public String profile(UserProfile profile, Platform platform, TranslationStatistics stats) {
        return profileHeader + "\n"
                + "Имя: " + (profile.userName() == null ? "—" : profile.userName()) + "\n"
                + "Платформа: " + platformName(platform) + "\n"
                + "Дата регистрации: " + DATE.format(profile.registeredAt().atZone(profile.timeZone())) + "\n"
                + "Язык-цель по умолчанию: " + languageName(profile.target()) + "\n"
                + "📊 Личная статистика\n"
                + statisticsBody(stats);
    }

    /**
     * Собирает экран настроек.
     *
     * @param profile текущий профиль
     * @return текст настроек
     */
    public String settings(UserProfile profile) {
        return settingsHeader + "\n"
                + "Язык-цель по умолчанию: " + languageName(profile.target()) + "\n"
                + "Режим ответа: " + profile.responseMode().label();
    }

    /**
     * Собирает подтверждение сохранения настроек.
     *
     * @param profile обновлённый профиль
     * @return текст подтверждения
     */
    public String settingsSaved(UserProfile profile) {
        return settingsSavedText + "\n"
                + "Язык-цель по умолчанию: " + languageName(profile.target()) + "\n"
                + "Режим ответа: " + profile.responseMode().label();
    }

    /**
     * Собирает текст общей статистики.
     *
     * @param stats статистика
     * @return готовый текст
     */
    public String statistics(TranslationStatistics stats) {
        return statsHeader + "\n" + statisticsBody(stats);
    }

    /**
     * Формирует общую часть статистики для профиля и экрана статистики.
     *
     * @param stats статистика
     * @return строки статистики
     */
    private String statisticsBody(TranslationStatistics stats) {
        StringBuilder text = new StringBuilder()
                .append("Всего переводов: ").append(stats.total()).append('\n')
                .append("✏️ Текст: ").append(stats.text()).append('\n')
                .append("📎 Файл: ").append(stats.document()).append('\n')
                .append("🎤 Голосовое: ").append(stats.voice()).append('\n')
                .append("Топ-3 пары языков:\n");
        if (stats.topPairs().isEmpty()) {
            text.append("—\n");
        } else {
            for (LanguagePair pair : stats.topPairs()) {
                text.append(pair.source().flag).append('→').append(pair.target().flag)
                        .append(" — ").append(pair.count()).append('\n');
            }
        }
        return text.append("За последние 7 дней: ").append(stats.lastWeek()).append('\n')
                .append("Средняя длина: ").append(Math.round(stats.averageLength()))
                .append(" символов")
                .toString();
    }

    /**
     * Собирает клавиатуру главного меню.
     *
     * @return клавиатура основного меню
     */
    public AppKeyboard mainMenu() {
        List<List<AppButton>> rows = new ArrayList<>();
        rows.add(row(
                button("\uD83C\uDF10 Перевести текст", "TRANSLATE"),
                button("👤 Профиль", "PROFILE"),
                button("⚙️ Настройки", "SETTINGS")));
        rows.add(row(
                button("📊 Статистика", "STATS"),
                button("🕘 История", "HISTORY"),
                button("❓ Помощь", "HELP")));
        return new AppKeyboard(rows);
    }

    /**
     * Собирает клавиатуру с одной кнопкой «В меню».
     *
     * @return клавиатура возврата в меню
     */
    public AppKeyboard menuOnly() {
        return new AppKeyboard(List.of(row(button("\uD83C\uDFE0 В меню", "MENU"))));
    }

    /**
     * Собирает клавиатуру настроек.
     *
     * @param target текущий язык-цель (может быть {@code null})
     * @param mode   текущий режим ответа
     * @return клавиатура выбора языка-цели и режима
     */
    public AppKeyboard settingsMenu(Language target, ResponseMode mode) {
        List<List<AppButton>> rows = new ArrayList<>();
        List<AppButton> languages = new ArrayList<>();
        for (Language language : Language.values()) {
            if (languages.size() == 3) {
                rows.add(languages);
                languages = new ArrayList<>();
            }
            languages.add(button(language.label, SET_TARGET_PREFIX + language.name()));
        }
        languages.add(button(noTarget, SET_TARGET_NONE));
        rows.add(languages);
        rows.add(row(
                button("Только текст", SET_MODE_TEXT),
                button("Текст + голос", SET_MODE_VOICE)));
        rows.add(row(button("\uD83C\uDFE0 В меню", "MENU")));
        return new AppKeyboard(rows);
    }

    /**
     * Собирает клавиатуру истории переводов без навигации.
     *
     * @return клавиатура с кнопками «Очистить историю» и «В меню»
     */
    public AppKeyboard historyMenu() {
        return historyMenu(0, false, false, Long.MAX_VALUE);
    }

    /**
     * Собирает клавиатуру истории с навигацией по страницам.
     *
     * @param page     номер текущей страницы (0 — самая новая)
     * @param hasNewer есть ли более новые переводы
     * @param hasOlder есть ли более старые переводы
     * @param anchorId верхняя граница снимка истории
     * @return клавиатура истории
     */
    public AppKeyboard historyMenu(int page, boolean hasNewer, boolean hasOlder,
                                   long anchorId) {
        List<List<AppButton>> rows = new ArrayList<>();

        if (hasNewer || hasOlder) {
            List<AppButton> navigation = new ArrayList<>();
            if (hasNewer) {
                navigation.add(button("⬅️ Назад",
                        HISTORY_PAGE_PREFIX + (page - 1) + "_" + anchorId));
            }
            navigation.add(button("Страница " + (page + 1),
                    HISTORY_PAGE_PREFIX + page + "_" + anchorId));
            if (hasOlder) {
                navigation.add(button("Вперед ➡️",
                        HISTORY_PAGE_PREFIX + (page + 1) + "_" + anchorId));
            }
            rows.add(navigation);
        }

        rows.add(row(button("🗑 Очистить историю", "CLEAR_HISTORY")));
        rows.add(row(button("\uD83C\uDFE0 В меню", "MENU")));
        return new AppKeyboard(rows);
    }

    /**
     * Собирает клавиатуру пустой истории.
     *
     * @return клавиатура с кнопками «Перевести текст» и «В меню»
     */
    public AppKeyboard emptyHistoryMenu() {
        return new AppKeyboard(List.of(row(
                button("\uD83C\uDF10 Перевести текст", "TRANSLATE"),
                button("\uD83C\uDFE0 В меню", "MENU"))));
    }

    /**
     * Собирает клавиатуру, показываемую после выдачи перевода.
     *
     * @return клавиатура с кнопками «Перевести ещё», «Сменить язык» и «В меню»
     */
    public AppKeyboard afterTranslation() {
        List<List<AppButton>> rows = new ArrayList<>();
        rows.add(row(
                button("\uD83D\uDD01 Перевести ещё", "MORE"),
                button("\uD83C\uDF10 Сменить язык", "CHANGE_LANG")));
        rows.add(row(button("\uD83C\uDFE0 В меню", "MENU")));
        return new AppKeyboard(rows);
    }

    /**
     * Собирает клавиатуру выбора языка-источника.
     *
     * @return клавиатура выбора языка-источника (кнопки SRC_&lt;код&gt;)
     */
    public AppKeyboard sourceChoice() {
        return languageChoice("SRC_");
    }

    /**
     * Собирает клавиатуру выбора языка-цели.
     *
     * @return клавиатура выбора языка-цели (кнопки TGT_&lt;код&gt;)
     */
    public AppKeyboard targetChoice() {
        return languageChoice("TGT_");
    }

    /**
     * Строит клавиатуру из всех доступных языков по 3 кнопки в ряд.
     *
     * @param prefix префикс для callback-данных
     * @return готовая клавиатура
     */
    private AppKeyboard languageChoice(String prefix) {
        List<List<AppButton>> rows = new ArrayList<>();
        List<AppButton> row = new ArrayList<>();
        for (Language language : Language.values()) {
            if (row.size() == 3) {
                rows.add(row);
                row = new ArrayList<>();
            }
            row.add(button(language.label, prefix + language.name()));
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return new AppKeyboard(rows);
    }

    /**
     * Создаёт ряд кнопок.
     *
     * @param buttons кнопки ряда
     * @return список кнопок
     */
    private List<AppButton> row(AppButton... buttons) {
        return new ArrayList<>(List.of(buttons));
    }

    /**
     * Создаёт универсальную кнопку.
     *
     * @param text отображаемый текст
     * @param data callback-данные
     * @return кнопка
     */
    private AppButton button(String text, String data) {
        return new AppButton(text, data);
    }

    /**
     * Возвращает название языка или «не задан».
     *
     * @param language язык или {@code null}
     * @return подпись для интерфейса
     */
    private String languageName(Language language) {
        return language == null ? notSet : language.label;
    }

    /**
     * Возвращает значок типа материала.
     *
     * @param type тип материала
     * @return эмодзи
     */
    private String icon(org.translatorBot.Model.MessageType type) {
        return switch (type) {
            case TEXT -> "✏️";
            case DOCUMENT -> "📎";
            case VOICE -> "🎤";
        };
    }

    /**
     * Сокращает текст до 40 символов и убирает переводы строк.
     *
     * @param value исходный текст или {@code null}
     * @return короткий фрагмент
     */
    private String preview(String value) {
        String text = safe(value).replaceAll("\\s+", " ").trim();
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }

    /**
     * Заменяет {@code null} пустой строкой.
     *
     * @param value значение
     * @return значение или пустая строка
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Возвращает название платформы.
     *
     * @param platform платформа
     * @return «Telegram» или «Discord»
     */
    private String platformName(Platform platform) {
        return platform == Platform.TELEGRAM ? "Telegram" : "Discord";
    }
}
