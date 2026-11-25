package org.translatorBot.Config;

/**
 * Настройки распознавания речи.
 *
 * @param provider   предпочитаемый провайдер ({@code groq} или {@code whisper})
 * @param groqApiKey API-ключ Groq или {@code null}
 * @param groqModel  модель Whisper на Groq
 * @param whisperUrl адрес локального Whisper
 */
public record SpeechConfig(String provider, String groqApiKey, String groqModel, String whisperUrl) {
}
