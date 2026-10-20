package org.translatorBot.Service.Speech;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Модульные тесты для {@link GroqSpeechService}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов. Сервис
 * работает с настоящим HTTP-клиентом, но вместо Groq обращается к локальному
 * {@link HttpServer}, который запоминает входящий запрос и отдаёт заранее
 * заданные код и тело ответа. Так проверяется и сформированный запрос,
 * и разбор ответа.
 */
class GroqSpeechServiceTest {
    /** Локальный сервер, имитирующий Groq. */
    private HttpServer server;
    /** HTTP-код, который вернёт сервер. */
    private volatile int status;
    /** Тело ответа, которое вернёт сервер. */
    private volatile String responseBody;
    /** Метод последнего запроса. */
    private final AtomicReference<String> method = new AtomicReference<>();
    /** Заголовок {@code Authorization} последнего запроса. */
    private final AtomicReference<String> authHeader = new AtomicReference<>();
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
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
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
     * Создаёт сервис, направленный на локальный сервер.
     *
     * @param key API-ключ (может быть {@code null} или пустым)
     * @return сервис распознавания
     */
    private GroqSpeechService service(String key) {
        return new GroqSpeechService(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                key, "whisper-large-v3-turbo");
    }

    /** Успешный ответ: текст возвращается без пробелов по краям. */
    @Test
    void returnsTrimmedRecognizedText() throws Exception {
        responseBody = "{\"text\":\"  Привет, мир!  \",\"segments\":[{\"no_speech_prob\":0.01}]}";

        String text = service("k").recognize("audio".getBytes(StandardCharsets.UTF_8), "voice.ogg", Language.RU);

        Assertions.assertEquals("Привет, мир!", text);
    }

    /** Запрос содержит ключ, модель, язык, имя файла и сами аудиоданные. */
    @Test
    void sendsKeyModelLanguageFileAndAudio() throws Exception {
        responseBody = "{\"text\":\"ok\"}";

        service("secret-key").recognize("AUDIO-BYTES".getBytes(StandardCharsets.UTF_8), "voice.ogg", Language.DE);

        Assertions.assertEquals("POST", method.get());
        Assertions.assertEquals("Bearer secret-key", authHeader.get());
        Assertions.assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
        String body = requestBody.get();
        Assertions.assertTrue(body.contains("name=\"model\"\r\n\r\nwhisper-large-v3-turbo"));
        Assertions.assertTrue(body.contains("name=\"language\"\r\n\r\nde"));
        Assertions.assertTrue(body.contains("filename=\"voice.ogg\""));
        Assertions.assertTrue(body.contains("AUDIO-BYTES"));
    }

    /** Если у всех сегментов высокая вероятность тишины, речь считается нераспознанной. */
    @Test
    void silenceIsUnrecognized() {
        responseBody = "{\"text\":\"Thanks for watching\",\"segments\":[{\"no_speech_prob\":0.95},{\"no_speech_prob\":0.9}]}";

        Assertions.assertThrows(SpeechUnrecognizedException.class,
                () -> service("k").recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Если речь есть хотя бы в одном сегменте, текст возвращается. */
    @Test
    void speechInAtLeastOneSegmentIsAccepted() throws Exception {
        responseBody = "{\"text\":\"Hello\",\"segments\":[{\"no_speech_prob\":0.95},{\"no_speech_prob\":0.1}]}";

        Assertions.assertEquals("Hello", service("k").recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Пустой или состоящий из пробелов текст означает, что речь не распознана. */
    @Test
    void blankTextIsUnrecognized() {
        responseBody = "{\"text\":\"   \"}";

        Assertions.assertThrows(SpeechUnrecognizedException.class,
                () -> service("k").recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }

    /** Любой HTTP-код, кроме 200, означает недоступность сервиса, и код попадает в сообщение. */
    @Test
    void httpErrorMeansUnavailable() {
        status = 429;
        responseBody = "{\"error\":\"rate limit\"}";

        SpeechUnavailableException e = Assertions.assertThrows(SpeechUnavailableException.class,
                () -> service("k").recognize(new byte[]{1}, "voice.ogg", Language.EN));
        Assertions.assertTrue(e.getMessage().contains("429"));
    }

    /** Без ключа (отсутствует или пустой) сервис недоступен, и запрос даже не отправляется. */
    @Test
    void missingOrBlankKeyMeansUnavailableWithoutRequest() {
        Assertions.assertThrows(SpeechUnavailableException.class,
                () -> service(null).recognize(new byte[]{1}, "voice.ogg", Language.EN));
        Assertions.assertThrows(SpeechUnavailableException.class,
                () -> service("  ").recognize(new byte[]{1}, "voice.ogg", Language.EN));
        Assertions.assertNull(method.get());
    }

    /** Если сервер недоступен по сети, ошибка соединения пробрасывается наружу. */
    @Test
    void unreachableServerThrowsIoError() {
        GroqSpeechService offline = service("k");
        server.stop(0);

        Assertions.assertThrows(IOException.class,
                () -> offline.recognize(new byte[]{1}, "voice.ogg", Language.EN));
    }
}