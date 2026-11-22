package org.translatorBot.Discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Model.AppButton;
import org.translatorBot.Model.AppKeyboard;
import org.translatorBot.Sender.FileDownloadException;
import org.translatorBot.Sender.MessageSender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация {@link MessageSender} для Discord на основе JDA.
 * <p>
 * Отправляет сообщения в канал, поддерживает кнопки, файлы и скачивание
 * вложений по ссылке CDN. Длинные сообщения автоматически разбиваются
 * на части, потому что Discord ограничивает длину одного сообщения.
 */
public class SenderDiscord implements MessageSender {
    /** Максимальная длина одного сообщения Discord. */
    static final int MAX_MESSAGE_LENGTH = 2000;

    /** Логгер отправителя. */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** HTTP-клиент для скачивания вложений по ссылке. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Клиент Discord. */
    private final JDA jda;

    /**
     * @param jda клиент Discord, через который отправляются сообщения
     */
    public SenderDiscord(JDA jda) {
        this.jda = jda;
    }

    /** {@inheritDoc} */
    @Override
    public void send(long chatId, String text) {
        send(chatId, text, null);
    }

    /** {@inheritDoc} */
    @Override
    public void send(long chatId, String text, AppKeyboard keyboard) {
        MessageChannel channel = channel(chatId);
        if (channel == null) {
            return;
        }
        List<String> chunks = split(text);
        for (int i = 0; i < chunks.size(); i++) {
            MessageCreateAction action = channel.sendMessage(chunks.get(i));
            if (keyboard != null && i == chunks.size() - 1) {
                action.addComponents(toComponents(keyboard));
            }
            action.queue(null, error ->
                    log.error("Failed to send message to Discord channel {}", chatId, error));
        }
    }

    /**
     * Discord подтверждает нажатия кнопок в адаптере до вызова маршрутизатора,
     * поэтому отдельное подтверждение здесь не требуется.
     *
     * @param callbackId идентификатор callback-запроса
     */
    @Override
    public void ackCallback(String callbackId) {
        log.debug("Discord callback {} already acknowledged by adapter", callbackId);
    }

    /** {@inheritDoc} */
    @Override
    public Path download(String fileId) {
        Path target = null;
        try {
            target = Files.createTempFile("discord-", ".bin");
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileId))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<Path> response = http.send(request,
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new FileDownloadException(
                        "Discord attachment HTTP " + response.statusCode(), null);
            }
            return target;
        } catch (IOException e) {
            deleteQuietly(target);
            throw new FileDownloadException("Failed to download Discord attachment", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(target);
            throw new FileDownloadException("Discord attachment download interrupted", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sendDocument(long chatId, String fileName, Path content,
                             String caption, AppKeyboard keyboard) {
        MessageChannel channel = channel(chatId);
        if (channel == null) {
            return;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(content);
        } catch (IOException e) {
            throw new FileDownloadException("Failed to read document for Discord", e);
        }
        MessageCreateAction action = channel
                .sendMessage(caption == null ? "" : caption)
                .addFiles(FileUpload.fromData(data, fileName));
        if (keyboard != null) {
            action.addComponents(toComponents(keyboard));
        }
        action.queue(null, error ->
                log.error("Failed to send document to Discord channel {}", chatId, error));
    }

    /**
     * Находит текстовый канал по идентификатору.
     *
     * @param chatId идентификатор канала
     * @return канал или {@code null}, если он недоступен
     */
    private MessageChannel channel(long chatId) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, chatId);
        if (channel == null) {
            log.warn("Discord channel {} is not available", chatId);
        }
        return channel;
    }

    /**
     * Преобразует универсальную клавиатуру в ряды кнопок Discord.
     *
     * @param keyboard универсальная клавиатура
     * @return компоненты сообщения Discord
     */
    private List<LayoutComponent> toComponents(AppKeyboard keyboard) {
        List<LayoutComponent> rows = new ArrayList<>();
        for (List<AppButton> appRow : keyboard.rows()) {
            List<Button> buttons = new ArrayList<>();
            for (AppButton appButton : appRow) {
                buttons.add(Button.secondary(appButton.callbackData(), appButton.text()));
            }
            rows.add(ActionRow.of(buttons));
        }
        return rows;
    }

    /**
     * Разбивает текст на части, помещающиеся в одно сообщение Discord.
     * Не разрывает суррогатную пару.
     *
     * @param text исходный текст
     * @return непустой список частей
     */
    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            chunks.add("");
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_MESSAGE_LENGTH);
            if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    /**
     * Удаляет временный файл, не мешая основному сценарию.
     *
     * @param path путь к файлу или {@code null}
     */
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temporary file {}", path, e);
        }
    }
}
