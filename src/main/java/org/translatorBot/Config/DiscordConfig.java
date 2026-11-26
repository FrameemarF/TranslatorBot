package org.translatorBot.Config;

/**
 * Настройки Discord-бота.
 *
 * @param token токен бота или {@code null}, если Discord не настроен
 */
public record DiscordConfig(String token) {

    /**
     * @return {@code true}, если Discord можно запускать
     */
    public boolean enabled() {
        return token != null;
    }
}
