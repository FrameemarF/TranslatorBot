package org.translatorBot.Service.Speech;

/** Сервис распознавания речи недоступен (ошибка сети, ключ, лимиты). */
public class SpeechUnavailableException extends Exception {
    public SpeechUnavailableException(String message) { super(message); }
}
