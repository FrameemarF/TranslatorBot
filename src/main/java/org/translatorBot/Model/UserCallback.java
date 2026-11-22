package org.translatorBot.Model;

/**
 * Универсальный объект нажатия на inline-кнопку, независимый от платформы.
 *
 * @param chatId     идентификатор чата
 * @param accountId  идентификатор аккаунта пользователя (сессия и история)
 * @param callbackId идентификатор колбэка (необходим для подтверждения получения нажатия)
 * @param data       данные кнопки (например, "SRC_RU" или "HELP")
 */
public record UserCallback(
        long chatId,
        long accountId,
        String callbackId,
        String data
) {
    /**
     * Создает callback, где аккаунт совпадает с чатом.
     *
     * @param chatId     идентификатор чата
     * @param callbackId идентификатор колбэка
     * @param data       данные кнопки
     */
    public UserCallback(long chatId, String callbackId, String data) {
        this(chatId, chatId, callbackId, data);
    }
}
