package org.translatorBot.Discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Handler.Router;
import org.translatorBot.Model.UserCallback;
import org.translatorBot.Model.UserMessage;

/**
 * Слушатель событий Discord: slash-команды, кнопки и обычные сообщения.
 * <p>
 * Приводит события JDA к универсальным моделям и передаёт их в
 * {@link Router}, чтобы Discord и Telegram использовали один сценарий.
 */
public class DiscordBotListener extends ListenerAdapter {
    /** Логгер слушателя. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Маршрутизатор входящих сообщений. */
    private final Router router;
    /** Преобразователь объектов Discord. */
    private final DiscordMapper mapper;

    /**
     * @param router маршрутизатор входящих сообщений
     * @param mapper преобразователь объектов Discord
     */
    public DiscordBotListener(Router router, DiscordMapper mapper) {
        this.router = router;
        this.mapper = mapper;
    }

    /**
     * Обрабатывает slash-команду как обычную текстовую команду.
     * Сначала подтверждает взаимодействие, затем удаляет служебное
     * сообщение «бот думает».
     *
     * @param event событие slash-команды
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = "/" + event.getName();
        long chatId = event.getChannel().getIdLong();
        long accountId = event.getUser().getIdLong();

        event.deferReply().queue(null, error ->
                log.error("Failed to acknowledge Discord command {}", command, error));
        try {
            router.handleMessage(new UserMessage(chatId, accountId, command));
        } finally {
            event.getHook().deleteOriginal().queue(null, error ->
                    log.debug("Failed to delete Discord deferred reply for {}", command));
        }
    }

    /**
     * Обрабатывает нажатие кнопки Discord. Нажатие подтверждается сразу,
     * чтобы Discord не показывал ошибку взаимодействия.
     *
     * @param event событие нажатия кнопки
     */
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        long chatId = event.getChannel().getIdLong();
        long accountId = event.getUser().getIdLong();
        String callbackId = event.getId();
        String data = event.getComponentId();

        event.deferEdit().queue(null, error ->
                log.error("Failed to acknowledge Discord button {}", data, error));
        router.handleCallback(new UserCallback(chatId, accountId, callbackId, data));
    }

    /**
     * Обрабатывает обычное сообщение Discord. Сообщения ботов пропускаются.
     *
     * @param event событие сообщения
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        mapper.toUserMessage(event.getMessage()).ifPresent(router::handleMessage);
    }
}
