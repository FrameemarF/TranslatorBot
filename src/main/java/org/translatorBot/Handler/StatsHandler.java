package org.translatorBot.Handler;

import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Stats.StatisticsService;
import org.translatorBot.Stats.TranslationStatistics;
import org.translatorBot.Ui;

import java.util.Optional;

/**
 * Показывает глобальную статистику использования бота.
 */
public class StatsHandler {
    private final MessageSender sender;
    private final StatisticsService statistics;
    private final Ui ui;

    /**
     * @param sender     отправитель сообщений
     * @param statistics статистика
     * @param ui         тексты интерфейса
     */
    public StatsHandler(MessageSender sender, StatisticsService statistics, Ui ui) {
        this.sender = sender;
        this.statistics = statistics;
        this.ui = ui;
    }

    /**
     * Отправляет общую статистику.
     *
     * @param chatId идентификатор чата
     */
    public void show(long chatId) {
        Optional<TranslationStatistics> stats = statistics.global();
        if (stats.isEmpty()) {
            sender.send(chatId, ui.statsUnavailable);
            return;
        }
        if (stats.get().isEmpty()) {
            sender.send(chatId, ui.statsEmpty, ui.menuOnly());
            return;
        }
        sender.send(chatId, ui.statistics(stats.get()), ui.menuOnly());
    }
}
