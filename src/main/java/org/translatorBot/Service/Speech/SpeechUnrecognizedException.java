package org.translatorBot.Service.Speech;

/** В аудио не удалось найти речь (тишина или шум). */
public class SpeechUnrecognizedException extends Exception {
    public SpeechUnrecognizedException() { super("No speech recognized"); }
}
