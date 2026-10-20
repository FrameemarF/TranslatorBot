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
 * Распознавание речи через ЛОКАЛЬНЫЙ Whisper — Docker-сервис
 * {@code onerahmet/openai-whisper-asr-webservice} (эндпоинт {@code POST /asr}).
 * Работает без интернета и без API-ключа; медленнее облачного Groq, т.к. считает на CPU.
 */
public class WhisperSpeechService implements SpeechRecognitionService {

    /** Если у всех сегментов вероятность «тишины» выше порога — считаем, что речи нет. */
    static final double NO_SPEECH_THRESHOLD = 0.6;

    /** HTTP-клиент с коротким таймаутом установки соединения, переиспользуется между запросами. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Общий Jackson-маппер для разбора JSON-ответа сервиса. */
    private final ObjectMapper json = new ObjectMapper();

    /** Полный адрес эндпоинта распознавания, например {@code http://localhost:9000/asr}. */
    private final String asrUrl;

    /**
     * Создаёт сервис для указанного базового адреса контейнера Whisper.
     *
     * @param baseUrl адрес сервиса, например {@code http://localhost:9000}
     *                (завершающий слэш необязателен)
     */
    public WhisperSpeechService(String baseUrl) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.asrUrl = base + "/asr";
    }

    /**
     * Отправляет аудио в локальный Whisper и возвращает распознанный текст.
     * <p>
     * Формат аудио не конвертируется: ожидается OGG (как приходит из Telegram).
     * Язык передаётся параметром запроса {@code language}.
     *
     * @param audio    байты аудиофайла
     * @param fileName имя файла, передаваемое в multipart-запросе
     * @param language ожидаемый язык речи
     * @return распознанный текст (непустой, обрезанный по краям)
     * @throws SpeechUnavailableException  если сервис вернул ошибку или недоступен
     * @throws SpeechUnrecognizedException если в аудио нет речи либо результат пуст
     * @throws Exception при прочих ошибках ввода-вывода или разбора ответа
     */
    @Override
    public String recognize(byte[] audio, String fileName, Language language) throws Exception {
        URI uri = URI.create(asrUrl + "?task=transcribe&output=json&encode=true&language="
                + language.code);
        String boundary = "----TranslatorBot" + UUID.randomUUID();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120)) // на CPU распознавание может занять время
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, audio, fileName)))
                .build();

        // Если контейнер выключен, здесь вылетит IOException — VoiceHandler покажет
        // «сервис распознавания недоступен».
        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new SpeechUnavailableException(
                    "Whisper HTTP " + response.statusCode() + ": " + response.body());
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
     * Формирует тело multipart/form-data с единственным полем {@code audio_file}.
     *
     * @param boundary разделитель частей формы
     * @param audio    содержимое аудиофайла
     * @param fileName имя файла для заголовка {@code Content-Disposition}
     * @return готовое тело HTTP-запроса
     * @throws IOException при ошибке записи в буфер
     */
    private byte[] multipart(String boundary, byte[] audio, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 512);
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio_file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}