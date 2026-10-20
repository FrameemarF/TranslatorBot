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
 * формирование запроса, разбор ответа и обработка ошибок.
 * <p>
 * Сервер поднимается на свободном порту loopback-адреса; поведение эндпоинта
 * {@code /translate} управляется полями {@link #status} и {@link #responseBody},
 * а тело запроса сохраняется в {@link #receivedBody}.
 */
class LibreTranslateServiceTest {
    private HttpServer server;
    /** HTTP-статус, который вернёт заглушка (по умолчанию 200). */
    private volatile int status = 200;
    /** Тело ответа заглушки в формате JSON. */
    private volatile String responseBody = "{\"translatedText\":\"Hello\"}";
    /** Тело последнего полученного запроса (JSON). */
    private volatile String receivedBody;

    /**
     * Запускает HTTP-сервер на случайном порту с обработчиком {@code /translate},
     * который записывает тело запроса и отдаёт заданные {@link #status} и {@link #responseBody}.
     */
    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/translate", ex -> {
            receivedBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    /** Останавливает сервер после каждого теста. */
    @AfterEach
    void stop() {
        server.stop(0);
    }

    /**
     * Создаёт сервис, указывающий на локальную заглушку.
     *
     * @return экземпляр {@link LibreTranslateService} с URL запущенного сервера
     */
    private LibreTranslateService service() {
        return new LibreTranslateService("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Успешный перевод: корректный результат и правильные поля запроса (source/target/текст). */
    @Test
    void returnsTranslationAndSendsCorrectRequest() throws Exception {
        String result = service().translate("Привет", Language.RU, Language.EN);

        Assertions.assertEquals("Hello", result);
        Assertions.assertTrue(receivedBody.contains("\"source\":\"ru\""));
        Assertions.assertTrue(receivedBody.contains("\"target\":\"en\""));
        Assertions.assertTrue(receivedBody.contains("Привет"));
    }

    /** При HTTP-ошибке выбрасывается {@link IllegalStateException} с текстом ошибки из ответа. */
    @Test
    void throwsOnHttpError() {
        status = 400;
        responseBody = "{\"error\":\"Invalid request\"}";

        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> service().translate("x", Language.RU, Language.EN));
        Assertions.assertTrue(e.getMessage().contains("Invalid request"));
    }

    /** Пустой {@code translatedText} считается ошибкой и приводит к {@link IllegalStateException}. */
    @Test
    void throwsOnEmptyTranslation() {
        responseBody = "{\"translatedText\":\"\"}";

        Assertions.assertThrows(IllegalStateException.class,
                () -> service().translate("x", Language.RU, Language.EN));
    }
}