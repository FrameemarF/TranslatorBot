package org.translatorBot.Service.Translation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Тесты {@link LibreTranslateService} на встроенном {@link HttpServer}:
 * формирование запроса, разбор ответа и обработка ошибок для translate и detect.
 */
class LibreTranslateServiceTest {
    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody;
    private volatile String receivedBody;
    private volatile String requestedPath;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            requestedPath = ex.getRequestURI().getPath();
            receivedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LibreTranslateService service() {
        return new LibreTranslateService("http://127.0.0.1:" + server.getAddress().getPort());
    }

    // ================= ТЕСТЫ ДЛЯ TRANSLATE =================

    @Test
    void returnsTranslationAndSendsCorrectRequest() throws Exception {
        responseBody = "{\"translatedText\":\"Hello\"}";

        String result = service().translate("Привет", Language.RU, Language.EN);

        Assertions.assertEquals("Hello", result);
        Assertions.assertEquals("/translate", requestedPath);
        Assertions.assertTrue(receivedBody.contains("\"source\":\"ru\""));
        Assertions.assertTrue(receivedBody.contains("\"target\":\"en\""));
        Assertions.assertTrue(receivedBody.contains("Привет"));
    }

    @Test
    void throwsOnHttpError() {
        status = 400;
        responseBody = "{\"error\":\"Invalid request\"}";

        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> service().translate("x", Language.RU, Language.EN));
        Assertions.assertTrue(e.getMessage().contains("Invalid request"));
    }

    @Test
    void throwsOnEmptyTranslation() {
        responseBody = "{\"translatedText\":\"\"}";

        Assertions.assertThrows(IllegalStateException.class,
                () -> service().translate("x", Language.RU, Language.EN));
    }

    // ================= ТЕСТЫ ДЛЯ DETECT (НОВАЯ ЛОГИКА) =================

    @Test
    void detectReturnsCorrectLanguage() throws Exception {
        // LibreTranslate detect возвращает массив объектов
        responseBody = "[{\"language\":\"fr\",\"confidence\":0.99}]";

        Language detected = service().detect("Bonjour le monde");

        Assertions.assertEquals(Language.FR, detected);
        Assertions.assertEquals("/detect", requestedPath);
        Assertions.assertTrue(receivedBody.contains("Bonjour le monde"));
    }

    @Test
    void detectReturnsNullForUnknownLanguage() throws Exception {
        responseBody = "[{\"language\":\"zh\",\"confidence\":0.99}]"; // Китайский не поддерживается нашим enum

        Language detected = service().detect("你好");

        Assertions.assertNull(detected);
    }

    @Test
    void detectThrowsOnHttpError() {
        status = 500;
        responseBody = "{\"error\":\"Internal Server Error\"}";

        Assertions.assertThrows(IllegalStateException.class,
                () -> service().detect("Some text"));
    }
}