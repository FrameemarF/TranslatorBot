package org.translatorBot.Handler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.History.FakeHistoryService;
import org.translatorBot.History.TranslationRecord;
import org.translatorBot.Language;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Sender.MessageSender;
import org.translatorBot.Ui;

import java.time.Instant;

/**
 * Модульные тесты для {@link HistoryHandler}.
 * <p>
 * Проверяют показ пустой и непустой истории, очистку и поведение при
 * недоступности хранилища.
 */
class HistoryHandlerTest {

    /** Идентификатор чата. */
    private final long chatId = 100L;
    /** Идентификатор аккаунта пользователя. */
    private final long accountId = 200L;

    /** Заглушка отправителя. */
    private RecordingSender sender;
    /** Заглушка хранилища истории. */
    private FakeHistoryService history;
    /** Источник текстов интерфейса. */
    private Ui ui;
    /** Проверяемый обработчик. */
    private HistoryHandler handler;

    @BeforeEach
    void setUp() {
        sender = new RecordingSender();
        history = new FakeHistoryService();
        ui = new Ui();
        handler = new HistoryHandler(sender, history, ui);
    }

    /** Пустая история сопровождается подсказкой и клавиатурой. */
    @Test
    void emptyHistoryShowsHint() {
        handler.show(chatId, accountId);

        Assertions.assertEquals(ui.historyEmpty, sender.lastText);
        Assertions.assertNotNull(sender.lastKeyboard);
        Assertions.assertEquals(accountId, history.lastAccountId);
        Assertions.assertEquals(HistoryHandler.HISTORY_LIMIT, history.lastLimit);
        Assertions.assertEquals(0, history.lastOffset);
    }

    /** Непустая история содержит записи от новых к старым. */
    @Test
    void nonEmptyHistoryShowsRecords() {
        history.records.add(record(Language.RU, Language.EN, "Привет", "Hello"));
        history.records.add(record(Language.DE, Language.FR, "Hallo", "Bonjour"));

        handler.show(chatId, accountId);

        Assertions.assertTrue(sender.lastText.startsWith(ui.historyHeader));
        Assertions.assertTrue(sender.lastText.contains("Привет"));
        Assertions.assertTrue(sender.lastText.contains("Hello"));
        Assertions.assertTrue(sender.lastText.contains("Hallo"));
        Assertions.assertTrue(sender.lastText.contains("Bonjour"));
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** Показывается не больше одной страницы истории. */
    @Test
    void historyShowsOnlyOnePage() {
        for (int i = 1; i <= 12; i++) {
            history.records.add(record(Language.RU, Language.EN,
                    "source-" + i, "translated-" + i));
        }

        handler.show(chatId, accountId);

        Assertions.assertTrue(sender.lastText.contains("10. "));
        Assertions.assertFalse(sender.lastText.contains("11. "));
    }

    /** При недоступности хранилища показывается предупреждение. */
    @Test
    void unavailableHistoryShowsWarning() {
        history.fail = true;

        handler.show(chatId, accountId);

        Assertions.assertEquals(ui.historyUnavailable, sender.lastText);
        Assertions.assertNull(sender.lastKeyboard);
    }

    /** Успешная очистка удаляет записи и подтверждается пользователю. */
    @Test
    void clearRemovesHistoryAndConfirms() {
        history.records.add(record(Language.RU, Language.EN, "Привет", "Hello"));

        handler.clear(chatId, accountId);

        Assertions.assertEquals(ui.historyCleared, sender.lastText);
        Assertions.assertTrue(history.records.isEmpty());
        Assertions.assertEquals(accountId, history.lastAccountId);
        Assertions.assertNotNull(sender.lastKeyboard);
    }

    /** При недоступности хранилища очистка не выполняется. */
    @Test
    void unavailableClearShowsWarning() {
        history.fail = true;

        handler.clear(chatId, accountId);

        Assertions.assertEquals(ui.historyUnavailable, sender.lastText);
        Assertions.assertNull(sender.lastKeyboard);
    }

    /**
     * Формирует тестовую запись истории.
     *
     * @param source     язык-источник
     * @param target     язык-цель
     * @param sourceText исходный текст
     * @param translated переведённый текст
     * @return запись истории
     */
    private TranslationRecord record(Language source, Language target,
                                     String sourceText, String translated) {
        return new TranslationRecord(
                MessageType.TEXT, source, target, sourceText, translated,
                null, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** Заглушка отправителя, запоминающая последнее сообщение. */
    private class RecordingSender implements MessageSender {
        /** Текст последнего отправленного сообщения. */
        String lastText;
        /** Клавиатура последнего отправленного сообщения. */
        AppKeyboard lastKeyboard;

        @Override
        public void send(long chatId, String text) {
            send(chatId, text, null);
        }

        @Override
        public void send(long chatId, String text, AppKeyboard keyboard) {
            lastText = text;
            lastKeyboard = keyboard;
        }

        @Override public void ackCallback(String callbackId) {}
        @Override public java.nio.file.Path download(String fileId) { return null; }
        @Override public void sendDocument(long chatId, String fileName, java.nio.file.Path content, String caption, AppKeyboard keyboard) {}
    }
}
