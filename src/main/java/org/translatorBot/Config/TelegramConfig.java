package org.translatorBot.Config;

/**
 * Настройки Telegram-бота.
 *
 * @param token токен бота (никогда не {@code null})
 */
public record TelegramConfig(String token) {
}
