package org.translatorBot.Model;

/**
 * Универсальный объект нажатия на inline-кнопку, независимый от платформы.
 *
 * @param chatId     идентификатор чата
 * @param callbackId идентификатор колбэка (необходим для подтверждения получения нажатия)
 * @param data       данные кнопки (например, "SRC_RU" или "HELP")
 */
public record UserCallback(
        long chatId,
        String callbackId,
        String data
) {}