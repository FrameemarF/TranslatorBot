package org.translatorBot.Discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.MessageType;
import org.translatorBot.Model.UserMessage;

import java.util.List;
import java.util.Optional;

/**
 * Преобразует объекты Discord (JDA) в универсальные доменные модели.
 * Изолирует зависимость от библиотеки JDA.
 */
public class DiscordMapper {
    /** Логгер маппера. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Преобразует сообщение Discord в универсальное входящее сообщение.
     * Вложения с ненулевой длительностью считаются голосовыми сообщениями,
     * остальные вложения — документами.
     *
     * @param message сообщение JDA
     * @return универсальное сообщение или {@link Optional#empty()},
     *         если в сообщении нет ни текста, ни вложений
     */
    public Optional<UserMessage> toUserMessage(
            net.dv8tion.jda.api.entities.Message message) {
        long chatId = message.getChannel().getIdLong();
        long accountId = message.getAuthor().getIdLong();

        List<net.dv8tion.jda.api.entities.Message.Attachment> attachments =
                message.getAttachments();
        if (!attachments.isEmpty()) {
            return Optional.of(fromAttachment(chatId, accountId, attachments.get(0)));
        }

        String text = message.getContentRaw();
        if (text == null || text.isBlank()) {
            log.debug("Skipping empty Discord message from account {}", accountId);
            return Optional.empty();
        }
        return Optional.of(new UserMessage(chatId, accountId, text));
    }

    /**
     * Создаёт универсальное сообщение по вложению Discord.
     *
     * @param chatId     идентификатор канала
     * @param accountId  идентификатор аккаунта пользователя
     * @param attachment вложение сообщения
     * @return сообщение с документом или голосовым материалом
     */
    private UserMessage fromAttachment(long chatId, long accountId,
                                       net.dv8tion.jda.api.entities.Message.Attachment attachment) {
        long size = attachment.getSize();
        double duration = attachment.getDuration();
        if (duration > 0) {
            int seconds = (int) Math.ceil(duration);
            return new UserMessage(chatId, accountId, MessageType.VOICE, null,
                    attachment.getUrl(), attachment.getFileName(), size, seconds);
        }
        return new UserMessage(chatId, accountId, attachment.getUrl(),
                attachment.getFileName(), size);
    }
}
