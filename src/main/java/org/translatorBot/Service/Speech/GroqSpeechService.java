package org.translatorBot.Service.Speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.translatorBot.Language;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Распознавание речи через облачный Whisper на Groq (OpenAI-совместимый API).
 * Принимает OGG напрямую, поэтому конвертация голосовых Telegram не нужна.
 */
public class GroqSpeechService implements SpeechRecognitionService {

    /** Адрес эндпоинта транскрипции по умолчанию (Groq, OpenAI-совместимый формат). */
    static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";

    /** Если у всех сегментов вероятность «тишины» выше порога — считаем, что речи нет. */
    static final double NO_SPEECH_THRESHOLD = 0.6;

    /** HTTP-клиент с коротким таймаутом установки соединения, переиспользуется между запросами. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Общий Jackson-маппер для разбора JSON-ответа API. */
    private final ObjectMapper json = new ObjectMapper();

    /** Адрес эндпоинта транскрипции. */
    private final URI endpoint;
    /** API-ключ Groq для заголовка Authorization. */
    private final String apiKey;
    /** Имя модели Whisper, например {@code whisper-large-v3}. */
    private final String model;

    /**
     * Создаёт сервис с эндпоинтом по умолчанию.
     *
     * @param apiKey ключ доступа к Groq API
     * @param model  идентификатор модели распознавания
     */
    public GroqSpeechService(String apiKey, String model) {
        this(URI.create(DEFAULT_ENDPOINT), apiKey, model);
    }

    /** Конструктор с произвольным адресом — нужен для тестов. */
    GroqSpeechService(URI endpoint, String apiKey, String model) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Отправляет аудио в Groq и возвращает распознанный текст.
     * <p>
     * Метод не выполняет конвертацию формата: ожидается, что {@code audio}
     * уже в контейнере OGG (как приходит из Telegram).
     *
     * @param audio    байты аудиофайла
     * @param fileName имя файла, передаваемое в multipart-запросе
     * @param language ожидаемый язык речи
     * @return распознанный текст (непустой, обрезанный по краям)
     * @throws SpeechUnavailableException если ключ не задан или сервис вернул ошибку
     * @throws SpeechUnrecognizedException если в аудио нет речи либо результат пуст
     * @throws Exception при прочих ошибках ввода-вывода или разбора ответа
     */
    @Override
    public String recognize(byte[] audio, String fileName, Language language) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SpeechUnavailableException("Groq API key is not configured");
        }

        String boundary = "----TranslatorBot" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipart(boundary, audio, fileName, language)))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new SpeechUnavailableException(
                    "Groq HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = json.readTree(response.body());
        String text = root.path("text").asText("").trim();
        if (text.isEmpty() || isSilence(root.path("segments"))) {
            throw new SpeechUnrecognizedException();
        }
        return text;
    }

    /** Whisper на тишине склонен «придумывать» текст, поэтому смотрим на no_speech_prob. */
    private boolean isSilence(JsonNode segments) {
        if (!segments.isArray() || segments.isEmpty()) return false;
        for (JsonNode s : segments) {
            if (s.path("no_speech_prob").asDouble(0) <= NO_SPEECH_THRESHOLD) return false;
        }
        return true;
    }

    /**
     * Формирует тело multipart/form-data: параметры модели, языка и сам аудиофайл.
     *
     * @param boundary  разделитель частей формы
     * @param audio     содержимое аудиофайла
     * @param fileName  имя файла для заголовка {@code Content-Disposition}
     * @param language  язык распознавания
     * @return готовое тело HTTP-запроса
     * @throws IOException при ошибке записи в буфер
     */
    private byte[] multipart(String boundary, byte[] audio, String fileName, Language language)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 1024);
        field(out, boundary, "model", model);
        field(out, boundary, "language", language.code);
        field(out, boundary, "response_format", "verbose_json");
        field(out, boundary, "temperature", "0");
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Дописывает в буфер одну текстовую часть multipart-формы.
     *
     * @param out      выходной поток
     * @param boundary разделитель частей формы
     * @param name     имя поля формы
     * @param value    значение поля
     * @throws IOException при ошибке записи в буфер
     */
    private void field(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}