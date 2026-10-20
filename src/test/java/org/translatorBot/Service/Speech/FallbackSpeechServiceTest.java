package org.translatorBot.Service.Speech;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Модульные тесты для {@link FallbackSpeechService}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов: в них нет
 * статических полей, методов, вложенных классов и статических импортов.
 * Основной и запасной сервисы заменены собственной реализацией {@link FakeSpeech},
 * которая запоминает вызовы и умеет возвращать текст или бросать заданную ошибку.
 */
class FallbackSpeechServiceTest {
    /** Заглушка {@link SpeechRecognitionService} с управляемым поведением. */
    private class FakeSpeech implements SpeechRecognitionService {
        /** Текст, который возвращает сервис. */
        final String reply;
        /** Если не {@code null}, {@link #recognize} бросает эту ошибку. */
        Exception error;
        /** Сколько раз сервис вызывали. */
        int calls = 0;
        /** Последние переданные аудиоданные. */
        byte[] lastAudio;
        /** Последнее переданное имя файла. */
        String lastFileName;
        /** Последний переданный язык. */
        Language lastLanguage;

        /**
         * @param reply текст, который вернёт сервис при успехе
         */
        FakeSpeech(String reply) {
            this.reply = reply;
        }

        /** {@inheritDoc} */
        @Override
        public String recognize(byte[] audio, String fileName, Language language) throws Exception {
            calls++;
            lastAudio = audio;
            lastFileName = fileName;
            lastLanguage = language;
            if (error != null) throw error;
            return reply;
        }
    }

    /** Аудио, которое «распознаётся» в тестах. */
    private final byte[] audio = new byte[]{1, 2, 3};

    private FakeSpeech primary;
    private FakeSpeech fallback;
    private FallbackSpeechService service;

    /** Создаёт свежие основной и запасной сервисы перед каждым тестом. */
    @BeforeEach
    void setUp() {
        primary = new FakeSpeech("from primary");
        fallback = new FakeSpeech("from fallback");
        service = new FallbackSpeechService(primary, fallback);
    }

    /** Если основной сервис работает, возвращается его результат, запасной не вызывается. */
    @Test
    void usesPrimaryWhenItWorks() throws Exception {
        String text = service.recognize(audio, "voice.ogg", Language.RU);

        Assertions.assertEquals("from primary", text);
        Assertions.assertEquals(0, fallback.calls);
    }

    /** Если основной сервис недоступен, результат берётся у запасного с теми же аргументами. */
    @Test
    void switchesToFallbackWhenPrimaryUnavailable() throws Exception {
        primary.error = new SpeechUnavailableException("rate limit");

        String text = service.recognize(audio, "voice.ogg", Language.DE);

        Assertions.assertEquals("from fallback", text);
        Assertions.assertEquals(1, fallback.calls);
        Assertions.assertArrayEquals(audio, fallback.lastAudio);
        Assertions.assertEquals("voice.ogg", fallback.lastFileName);
        Assertions.assertEquals(Language.DE, fallback.lastLanguage);
    }

    /** Сетевая ошибка основного сервиса тоже приводит к переключению на запасной. */
    @Test
    void switchesToFallbackOnNetworkError() throws Exception {
        primary.error = new IOException("connection reset");

        Assertions.assertEquals("from fallback", service.recognize(audio, "voice.ogg", Language.RU));
    }

    /** Если в записи нет речи, ошибка пробрасывается, а запасной сервис не вызывается. */
    @Test
    void unrecognizedSpeechIsNotRetriedOnFallback() {
        primary.error = new SpeechUnrecognizedException();

        Assertions.assertThrows(SpeechUnrecognizedException.class,
                () -> service.recognize(audio, "voice.ogg", Language.RU));
        Assertions.assertEquals(0, fallback.calls);
    }

    /** Если не работает и запасной сервис, наружу уходит его ошибка. */
    @Test
    void failsWithFallbackErrorWhenBothFail() {
        primary.error = new SpeechUnavailableException("primary down");
        fallback.error = new SpeechUnavailableException("fallback down");

        SpeechUnavailableException e = Assertions.assertThrows(SpeechUnavailableException.class,
                () -> service.recognize(audio, "voice.ogg", Language.RU));
        Assertions.assertEquals("fallback down", e.getMessage());
    }

    /** При прерывании потока ошибка пробрасывается, флаг прерывания восстанавливается, запасной не вызывается. */
    @Test
    void interruptionIsPropagatedWithoutFallback() {
        primary.error = new InterruptedException("stop");

        Assertions.assertThrows(InterruptedException.class,
                () -> service.recognize(audio, "voice.ogg", Language.RU));
        Assertions.assertTrue(Thread.interrupted()); // проверяет и сбрасывает флаг, чтобы не влиять на другие тесты
        Assertions.assertEquals(0, fallback.calls);
    }
}