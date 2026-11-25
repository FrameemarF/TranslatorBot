package org.translatorBot.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.History.HistoryService;
import org.translatorBot.History.Platform;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Ui;
import org.translatorBot.User.UserService;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Показывает и очищает историю переводов пользователя.
 * <p>
 * История выводится страницами по 10 записей, каждая запись содержит дату
 * и время перевода в часовом поясе пользователя (или UTC).
 */
public class HistoryHandler {
    /** Сколько переводов показывать на одной странице. */
    static final int HISTORY_LIMIT = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageSender sender;
    private final HistoryService history;
    private final UserService users;
    private final Ui ui;
    private final Platform platform;

    /**
     * @param sender   отправитель сообщений
     * @param history  история переводов
     * @param users    профили пользователей (часовой пояс)
     * @param ui       тексты интерфейса
     * @param platform платформа пользователя
     */
    public HistoryHandler(MessageSender sender, HistoryService history,
                          UserService users, Ui ui, Platform platform) {
        this.sender = sender;
        this.history = history;
        this.users = users;
        this.ui = ui;
        this.platform = platform;
    }

    /**
     * Отправляет первую страницу истории.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     */
    public void show(long chatId, long accountId) {
        Optional<Long> anchor = history.latestId(platform, accountId);
        if (anchor.isEmpty()) {
            log.error("Failed to load history for {}:{}", platform, accountId);
            sender.send(chatId, ui.historyUnavailable);
            return;
        }
        showPage(chatId, accountId, 0, anchor.get());
    }

    /**
     * Отправляет страницу истории без фиксированной верхней границы.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param page      номер страницы (0 — самая новая)
     */
    public void showPage(long chatId, long accountId, int page) {
        showPage(chatId, accountId, page, Long.MAX_VALUE);
    }

    /**
     * Отправляет выбранную страницу истории.
     * <p>
     * Запрашивается на одну запись больше размера страницы, чтобы узнать,
     * есть ли ещё более старые переводы. Все страницы ограничены одним
     * и тем же {@code anchorId}, поэтому новые переводы не сдвигают
     * уже открытые страницы.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     * @param page      номер страницы (0 — самая новая)
     * @param anchorId  верхняя граница снимка истории
     */
    public void showPage(long chatId, long accountId, int page, long anchorId) {
        int safePage = Math.max(page, 0);
        Optional<List<TranslationRecord>> loaded = history.recent(
                platform, accountId, HISTORY_LIMIT + 1,
                safePage * HISTORY_LIMIT, anchorId);
        if (loaded.isEmpty()) {
            log.error("Failed to load history for {}:{}", platform, accountId);
            sender.send(chatId, ui.historyUnavailable);
            return;
        }
        List<TranslationRecord> records = loaded.get();
        boolean hasOlder = records.size() > HISTORY_LIMIT;
        if (hasOlder) {
            records = records.subList(0, HISTORY_LIMIT);
        }
        if (records.isEmpty()) {
            sender.send(chatId, ui.historyEmpty, ui.emptyHistoryMenu());
            return;
        }
        sender.send(chatId, format(records, safePage, zone(accountId)),
                ui.historyMenu(safePage, safePage > 0, hasOlder, anchorId));
    }

    /**
     * Удаляет историю пользователя и подтверждает действие.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта
     */
    public void clear(long chatId, long accountId) {
        if (history.clear(platform, accountId)) {
            sender.send(chatId, ui.historyCleared, ui.mainMenu());
        } else {
            log.error("Failed to clear history for {}:{}", platform, accountId);
            sender.send(chatId, ui.historyUnavailable);
        }
    }

    /**
     * Собирает текст со списком переводов.
     *
     * @param records переводы от новых к старым
     * @param page    номер страницы
     * @param zone    часовой пояс пользователя
     * @return готовый текст
     */
    private String format(List<TranslationRecord> records, int page, ZoneId zone) {
        StringBuilder text = new StringBuilder(ui.historyTitle(page));
        int firstNumber = page * HISTORY_LIMIT + 1;
        for (int i = 0; i < records.size(); i++) {
            text.append('\n').append(ui.historyRecord(firstNumber + i, records.get(i), zone));
        }
        return text.toString();
    }

    /**
     * Возвращает часовой пояс пользователя или UTC.
     *
     * @param accountId идентификатор аккаунта
     * @return часовой пояс
     */
    private ZoneId zone(long accountId) {
        return users.profile(platform, accountId)
                .map(profile -> profile.timeZone())
                .orElse(ZoneOffset.UTC);
    }
}
