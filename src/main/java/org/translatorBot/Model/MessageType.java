package org.translatorBot.Model;

/**
 * Перечисление возможных типов входящих сообщений от пользователя.
 */
public enum MessageType {
    /** Текстовое сообщение. */
    TEXT,
    /** Документ (файл). */
    DOCUMENT,
    /** Голосовое сообщение. */
    VOICE
}