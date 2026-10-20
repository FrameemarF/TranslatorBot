package org.translatorBot.Service.Speech;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Модульные тесты для {@link WhisperSpeechService}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов. Сервис
 * работает с настоящим HTTP-клиентом, но вместо Docker-контейнера Whisper
 * обращается к локальному {@link HttpServer}, который запоминает входящий
 * запрос и отдаёт заранее заданные код и тело ответа. Так проверяется и
 * сформированный запрос, и разбор ответа.
 */
class WhisperSpeechServiceTest {
    /** Локальный сервер, имитирующий Whisper. */
    private HttpServer server;
    /** HTTP-код, который вернёт сервер. */
    private volatile int status;
    /** Тело ответа, которое вернёт сервер. */
    private volatile String responseBody;
    /** Метод последнего запроса. */
    private final AtomicReference<String> method = new AtomicReference<>();
    /** Путь последнего запроса (без параметров). */
    private final AtomicReference<String> path = new AtomicReference<>();
    /** Параметры (query) последнего запроса. */
    private final AtomicReference<String> query = new AtomicReference<>();
    /** Заголовок {@code Content-Type} последнего запроса. */
    private final AtomicReference<String> contentType = new AtomicReference<>();
    /** Тело последнего запроса (байты прочитаны как ISO-8859-1, чтобы ничего не потерять). */
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    /** Запускает локальный сервер на свободном порту перед каждым тестом. */
    @BeforeEach
    void setUp() throws Exception {
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    /** Останавливает локальный сервер после каждого теста. */
    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * Возвращает адрес локального сервера без завершающего слэша.
     *
     * @return адрес вида {@code http://127.0.0.1:порт}
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Успешный ответ: текст возвращается без пробелов по краям. */
    @Test
    void returnsTrimmedRecognizedText() throws Exception {
        responseBody = "{\"text\":\"  Привет, мир!  \",\"segments\":[{\"no_speech_prob\":0.01}]}";

        String text = new WhisperSpeechService(baseUrl())
                .recognize("audio".getBytes(StandardCharsets.UTF_8), "voice.ogg", Language.RU);

        Assertions.assertEquals("Привет, мир!", text);
    }

    /** Запрос идёт методом POST на {@code /asr} с нужными параметрами и аудио в поле {@code audio_file}. */
    @Test
    void sendsAsrRequestWithLanguageFileAndAudio() throws Exception {
        responseBody = "{\"text\":\"ok\"}";

        new WhisperSpeechService(baseUrl())
                .recognize("AUDIO-BYTES".getBytes(StandardCharsets.UTF_8), "voice.ogg", Language.DE);

        Assertions.assertEquals("POST", method.get());
        Assertions.assertEquals("/asr", path.get());
        Assertions.assertTrue(query.get().contains("language=de"));
        Assertions.assertTrue(query.get().contains("task=transcribe"));
        Assertions.assertTrue(query.get().contains("output=json"));
        Assertions.assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
        String body = requestBody.get();
        Assertions.assertTrue(body.contains("name=\"audio_file\"; filename=\"voice.ogg\""));
        Assertions.assertTrue(body.contains("AUDIO-BYTES"));
    }

    /** Завершающий слэш в адресе сервиса не ломает путь: запрос всё равно уходит на {@code /asr}. */
    @Test
    void trailingSlashInBaseUrlIsIgnored() throws Exception {
        responseBody = "{\"text\":\"ok\"}";

        new WhisperSpeechService(baseUrl() + "/").recognize(new byte[]{1}, "voice.ogg", Language.EN);

        Assertions.assertEquals("/asr", path.get());
    }

    /** Если у всех сегментов высокая вероятность тишины, речь считается нераспознанной. */
    @Test
    void silenceIsUnrecognized() {
        responseBody = "{\"text\":\"Thanks for watching\",\"segments\":[{\"no_speech_prob\":0.95},{\"no_speech_prob\":0.9}]}";

        Assertions.assertThrows(SpeechUnrecognizedException.class,
                () -> new WhisperSpeechService(baseUrl()).recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Если речь есть хотя бы в одном сегменте, текст возвращается. */
    @Test
    void speechInAtLeastOneSegmentIsAccepted() throws Exception {
        responseBody = "{\"text\":\"Hello\",\"segments\":[{\"no_speech_prob\":0.95},{\"no_speech_prob\":0.1}]}";

        Assertions.assertEquals("Hello",
                new WhisperSpeechService(baseUrl()).recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Пустой или состоящий из пробелов текст означает, что речь не распознана. */
    @Test
    void blankTextIsUnrecognized() {
        responseBody = "{\"text\":\"   \"}";

        Assertions.assertThrows(SpeechUnrecognizedException.class,
                () -> new WhisperSpeechService(baseUrl()).recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Любой HTTP-код, кроме 200, означает недоступность сервиса, и код попадает в сообщение. */
    @Test
    void httpErrorMeansUnavailable() {
        status = 500;
        responseBody = "{\"error\":\"model failed\"}";

        SpeechUnavailableException e = Assertions.assertThrows(SpeechUnavailableException.class,
                () -> new WhisperSpeechService(baseUrl()).recognize(new byte[]{1}, "voice.ogg", Language.EN));
        Assertions.assertTrue(e.getMessage().contains("500"));
    }

    /** Если контейнер выключен, ошибка соединения пробрасывается наружу. */
    @Test
    void unreachableServerThrowsIoError() {
        WhisperSpeechService offline = new WhisperSpeechService(baseUrl());
        server.stop(0);

        Assertions.assertThrows(IOException.class,
                () -> offline.recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }
}