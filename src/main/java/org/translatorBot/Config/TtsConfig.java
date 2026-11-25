package org.translatorBot.Config;

/**
 * Настройки синтеза речи (edge-tts, OpenAI-совместимый HTTP API).
 *
 * @param url базовый адрес сервиса озвучки
 */
public record TtsConfig(String url) {
}
