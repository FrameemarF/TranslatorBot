package org.translatorBot.Service.Translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;
import org.translatorBot.LanguageCatalog;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Реализация {@link TranslationService} через HTTP API LibreTranslate.
 * <p>
 * Использует {@link HttpClient} с таймаутами: подключение — 5 секунд,
 * запрос — 30 секунд. Все ошибки транспорта и ответы с кодом, отличным
 * от 200, оборачиваются в {@link TranslationException}.
 */
public class LibreTranslateService implements TranslationService {
    private final Duration connectTimeout = Duration.ofSeconds(5);
    private final Duration requestTimeout = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** Каталог для сопоставления ISO-кодов языков. */
    private final LanguageCatalog languages = new LanguageCatalog();

    private final URI endpoint;

    /**
     * Создаёт сервис с указанным базовым URL.
     *
     * @param baseUrl базовый URL LibreTranslate (например, {@code http://localhost:5000});
     *                завершающий слэш необязателен
     */
    public LibreTranslateService(String baseUrl) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.endpoint = URI.create(base + "/translate");
    }

    /**
     * Переводит текст с одного языка на другой через LibreTranslate.
     *
     * @param text текст для перевода
     * @param from исходный язык
     * @param to   целевой язык
     * @return переведённый текст
     * @throws TranslationException при сетевых ошибках, ошибках HTTP или пустом ответе
     */
    @Override
    public String translate(String text, Language from, Language to) {
        ObjectNode body = json.createObjectNode();
        body.put("q", text);
        body.put("source", from.code);
        body.put("target", to.code);
        body.put("format", "text");

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new TranslationException("LibreTranslate HTTP "
                        + response.statusCode() + ": " + extractError(response.body()));
            }

            String result = json.readTree(response.body()).path("translatedText").asText(null);
            if (result == null || result.isBlank()) {
                throw new TranslationException("LibreTranslate returned empty translation");
            }

            log.debug("Translated [{}→{}], {} chars", from.code, to.code, text.length());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException("LibreTranslate request interrupted", e);
        } catch (IOException e) {
            throw new TranslationException("LibreTranslate request failed", e);
        }
    }

    /**
     * Извлекает сообщение об ошибке из тела ответа.
     * Если тело не является JSON или не содержит поля {@code error},
     * возвращает тело как есть.
     *
     * @param body тело ответа
     * @return сообщение об ошибке или исходное тело
     */
    private String extractError(String body) {
        try {
            JsonNode node = json.readTree(body);
            return node.path("error").asText(body);
        } catch (Exception e) {
            return body;
        }
    }

    /**
     * Определяет язык текста через эндпоинт /detect.
     *
     * @param text текст для анализа
     * @return язык или {@link Optional#empty()}, если язык не распознан
     *         или не поддерживается
     * @throws TranslationException при ошибке HTTP-запроса или парсинга JSON
     */
    @Override
    public Optional<Language> detect(String text) {
        ObjectNode body = json.createObjectNode();
        body.put("q", text);

        String detectEndpoint = endpoint.toString().replace("/translate", "/detect");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(detectEndpoint))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new TranslationException("LibreDetect HTTP " + response.statusCode());
            }

            JsonNode node = json.readTree(response.body());
            if (node.isArray() && node.size() > 0) {
                String langCode = node.get(0).path("language").asText();
                return languages.findByCode(langCode);
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException("LibreDetect request interrupted", e);
        } catch (IOException e) {
            throw new TranslationException("LibreDetect request failed", e);
        }
    }
}
