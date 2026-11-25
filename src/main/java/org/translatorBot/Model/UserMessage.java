package org.translatorBot.Model;

/**
 * Универсальное входящее сообщение пользователя, независимое от платформы.
 *
 * @param chatId        идентификатор чата (куда отправлять ответ)
 * @param accountId     идентификатор аккаунта пользователя (сессия и история)
 * @param type          тип сообщения (текст, документ, голос)
 * @param text          текст сообщения (используется, если type = TEXT)
 * @param fileId        идентификатор файла или ссылка (для DOCUMENT и VOICE)
 * @param fileName      имя файла (используется, если type = DOCUMENT)
 * @param fileSize      размер файла в байтах (используется, если type = DOCUMENT)
 * @param voiceDuration длительность голосового в секундах (используется, если type = VOICE)
 * @param userName      имя пользователя на платформе или {@code null}
 */
public record UserMessage(
        long chatId,
        long accountId,
        MessageType type,
        String text,
        String fileId,
        String fileName,
        Long fileSize,
        Integer voiceDuration,
        String userName
) {
    /**
     * Создает текстовое сообщение, где аккаунт совпадает с чатом.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    public UserMessage(long chatId, String text) {
        this(chatId, chatId, text);
    }

    /**
     * Создает текстовое сообщение.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     * @param text      текст сообщения
     */
    public UserMessage(long chatId, long accountId, String text) {
        this(chatId, accountId, MessageType.TEXT, text, null, null, null, null, null);
    }

    /**
     * Создает сообщение с документом, где аккаунт совпадает с чатом.
     *
     * @param chatId   идентификатор чата
     * @param fileId   идентификатор файла
     * @param fileName имя файла
     * @param fileSize размер файла в байтах
     */
    public UserMessage(long chatId, String fileId, String fileName, Long fileSize) {
        this(chatId, chatId, fileId, fileName, fileSize);
    }

    /**
     * Создает сообщение с документом.
     *
     * @param chatId    идентификатор чата
     * @param accountId идентификатор аккаунта пользователя
     * @param fileId    идентификатор файла или ссылка
     * @param fileName  имя файла
     * @param fileSize  размер файла в байтах
     */
    public UserMessage(long chatId, long accountId, String fileId,
                       String fileName, Long fileSize) {
        this(chatId, accountId, MessageType.DOCUMENT, null, fileId, fileName, fileSize, null, null);
    }

    /**
     * Создает голосовое сообщение, где аккаунт совпадает с чатом.
     *
     * @param chatId        идентификатор чата
     * @param fileId        идентификатор файла
     * @param voiceDuration длительность голосового сообщения
     */
    public UserMessage(long chatId, String fileId, Integer voiceDuration) {
        this(chatId, chatId, fileId, voiceDuration);
    }

    /**
     * Создает голосовое сообщение.
     *
     * @param chatId        идентификатор чата
     * @param accountId     идентификатор аккаунта пользователя
     * @param fileId        идентификатор файла или ссылка
     * @param voiceDuration длительность голосового сообщения
     */
    public UserMessage(long chatId, long accountId, String fileId, Integer voiceDuration) {
        this(chatId, accountId, MessageType.VOICE, null, fileId, null, null, voiceDuration, null);
    }

    /**
     * Возвращает копию сообщения с именем пользователя.
     *
     * @param name имя пользователя
     * @return сообщение с заполненным именем
     */
    public UserMessage withUserName(String name) {
        return new UserMessage(chatId, accountId, type, text, fileId, fileName,
                fileSize, voiceDuration, name);
    }
}
