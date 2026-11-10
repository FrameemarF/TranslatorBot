package org.translatorBot.History;

/**
 * Платформа, к которой относится аккаунт пользователя. История переводов
 * разных платформ хранится раздельно, поэтому платформа входит в ключ
 * выборки.
 */
public enum Platform {
    /** Telegram. */
    TELEGRAM,
    /** Discord. */
    DISCORD
}
