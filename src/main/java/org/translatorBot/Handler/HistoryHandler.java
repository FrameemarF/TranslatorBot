package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.HistoryException;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Ui;

import java.util.List;

/**
 * Показывает и очищает историю переводов пользователя.
 * <p>
 * Команды {@code /history} и {@code /clear_history} и кнопки
 * «История» / «Очистить историю» приводят сюда. Ошибки базы данных
 * не прерывают работу бота: пользователь получает сообщение о временной
 * недоступности истории.
 */
public class HistoryHandler {
    /** Сколько переводов показывать на одной странице. */
    static final int HISTORY_LIMIT = 10;
    /** Максимальная длина фрагмента текста в строке истории. */
    static final int PREVIEW_LENGTH = 40;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final HistoryService history;
    private final Ui ui;

    /**
     * @param sender  отправитель сообщений
     * @param history хранилище истории переводов
     * @param ui      источник текстов и клавиатур
     */
    public HistoryHandler(MessageSender sender, HistoryService history, Ui ui) {
        this.sender = sender;
        this.history = history;
        this.ui = ui;
    }

    /**
     * Отправляет пользователю первую страницу истории переводов. Если история
     * пуста — показывает подсказку, если база недоступна — сообщение об ошибке.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     */
    public void show(long chatId, long accountId) {
        long anchorId;
        try {
            anchorId = history.latestId(accountId);
        } catch (HistoryException e) {
            log.error("Failed to load history for account {}", accountId, e);
            sender.send(chatId, ui.historyUnavailable);
            return;
        }
        showPage(chatId, accountId, 0, anchorId);
    }

    /**
     * Отправляет страницу истории без фиксированной верхней границы.
     * Используется для обратной совместимости и в тестах.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     * @param page      номер страницы (0 — самая новая)
     */
    public void showPage(long chatId, long accountId, int page) {
        showPage(chatId, accountId, page, Long.MAX_VALUE);
    }

    /**
     * Отправляет пользователю выбранную страницу истории переводов.
     * <p>
     * Страницы нумеруются с нуля. Запрашивается на одну запись больше размера
     * страницы, чтобы определить, есть ли ещё более старые переводы. Все
     * страницы ограничены одним и тем же {@code anchorId}, поэтому новые
     * переводы не сдвигают уже открытые страницы.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     * @param page      номер страницы (0 — самая новая)
     * @param anchorId  максимальный id записи в снимке истории
     */
    public void showPage(long chatId, long accountId, int page, long anchorId) {
        int safePage = Math.max(page, 0);
        try {
            List<TranslationRecord> records = history.recent(
                    accountId, HISTORY_LIMIT + 1,
                    safePage * HISTORY_LIMIT, anchorId);
            boolean hasOlder = records.size() > HISTORY_LIMIT;
            if (hasOlder) {
                records = records.subList(0, HISTORY_LIMIT);
            }
            if (records.isEmpty()) {
                sender.send(chatId, ui.historyEmpty, ui.emptyHistoryMenu());
                return;
            }
            sender.send(chatId, format(records, safePage),
                    ui.historyMenu(safePage, safePage > 0, hasOlder, anchorId));
        } catch (HistoryException e) {
            log.error("Failed to load history for account {}", accountId, e);
            sender.send(chatId, ui.historyUnavailable);
        }
    }

    /**
     * Удаляет историю пользователя и подтверждает действие.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     */
    public void clear(long chatId, long accountId) {
        try {
            history.clear(accountId);
            sender.send(chatId, ui.historyCleared, ui.mainMenu());
        } catch (HistoryException e) {
            log.error("Failed to clear history for account {}", accountId, e);
            sender.send(chatId, ui.historyUnavailable);
        }
    }

    /**
     * Собирает текст сообщения со списком переводов.
     *
     * @param records переводы от новых к старым
     * @param page    номер страницы для сквозной нумерации
     * @return готовый текст для отправки
     */
    private String format(List<TranslationRecord> records, int page) {
        StringBuilder text = new StringBuilder(ui.historyHeader);
        int firstNumber = page * HISTORY_LIMIT + 1;
        for (int i = 0; i < records.size(); i++) {
            text.append('\n').append(formatRecord(firstNumber + i, records.get(i)));
        }
        return text.toString();
    }

    /**
     * Формирует одну строку истории.
     *
     * @param number порядковый номер строки
     * @param record запись истории
     * @return строка вида {@code 1. 🎤 🇷🇺→🇬🇧  Привет → Hello}
     */
    private String formatRecord(int number, TranslationRecord record) {
        String icon = icon(record.materialType());
        String direction = record.source().flag + "→" + record.target().flag;
        String body = record.materialType() == MessageType.DOCUMENT
                ? safe(record.materialName())
                : preview(record.sourceText()) + " → " + preview(record.translatedText());
        return number + ". " + icon + " " + direction + "  " + body;
    }

    /**
     * Возвращает значок типа материала.
     *
     * @param type тип материала
     * @return эмодзи типа материала
     */
    private String icon(MessageType type) {
        return switch (type) {
            case TEXT -> "✏️";
            case DOCUMENT -> "📎";
            case VOICE -> "🎤";
        };
    }

    /**
     * Сокращает текст до {@value #PREVIEW_LENGTH} символов и убирает переводы строк.
     *
     * @param value исходный текст или {@code null}
     * @return безопасный короткий фрагмент
     */
    private String preview(String value) {
        String text = safe(value).replaceAll("\\s+", " ").trim();
        if (text.length() <= PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, PREVIEW_LENGTH) + "…";
    }

    /**
     * Заменяет {@code null} пустой строкой.
     *
     * @param value значение или {@code null}
     * @return значение либо пустая строка
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
