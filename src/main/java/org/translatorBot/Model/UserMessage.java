package org.translatorBot.Model;

/**
 * Универсальное входящее сообщение пользователя, независимое от платформы.
 * Содержит только те данные, которые необходимы для работы хендлеров.
 *
 * @param chatId        идентификатор чата
 * @param type          тип сообщения (текст, документ, голос)
 * @param text          текст сообщения (используется, если type = TEXT)
 * @param fileId        идентификатор файла (используется, если type = DOCUMENT или VOICE)
 * @param fileName      имя файла (используется, если type = DOCUMENT)
 * @param fileSize      размер файла в байтах (используется, если type = DOCUMENT)
 * @param voiceDuration длительность голосового в секундах (используется, если type = VOICE)
 */
public record UserMessage(
        long chatId,
        MessageType type,
        String text,
        String fileId,
        String fileName,
        Long fileSize,
        Integer voiceDuration
) {
    /**
     * Создает текстовое сообщение.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    public UserMessage(long chatId, String text) {
        this(chatId, MessageType.TEXT, text, null, null, null, null);
    }

    /**
     * Создает сообщение с документом.
     *
     * @param chatId   идентификатор чата
     * @param fileId   идентификатор файла в Telegram
     * @param fileName имя файла
     * @param fileSize размер файла в байтах
     */
    public UserMessage(long chatId, String fileId, String fileName, Long fileSize) {
        this(chatId, MessageType.DOCUMENT, null, fileId, fileName, fileSize, null);
    }

    /**
     * Создает голосовое сообщение.
     *
     * @param chatId        идентификатор чата
     * @param fileId        идентификатор файла в Telegram
     * @param voiceDuration длительность голосового сообщения
     */
    public UserMessage(long chatId, String fileId, Integer voiceDuration) {
        this(chatId, MessageType.VOICE, null, fileId, null, null, voiceDuration);
    }
}