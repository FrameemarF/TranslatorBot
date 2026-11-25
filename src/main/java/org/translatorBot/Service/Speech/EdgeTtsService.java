package org.translatorBot.Service.Speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Синтез речи через edge-tts (OpenAI-совместимый HTTP API).
 * <p>
 * Голос выбирается по языку перевода. Если текст длиннее одного запроса,
 * он режется на части по предложениям, и каждая часть озвучивается отдельно.
 * <p>
 * Аудио запрашивается в формате OGG/Opus: именно его Telegram показывает
 * голосовым сообщением. Конвертацию MP3 → OGG выполняет контейнер edge-tts,
 * поэтому ему нужен ffmpeg.
 */
public class EdgeTtsService implements SpeechSynthesisService {
    /** Максимальная длина текста в одном запросе к сервису. */
    private static final int MAX_CHUNK = 900;

    /** Голоса edge-tts для поддерживаемых языков. */
    private static final Map<Language, String> VOICES = Map.of(
            Language.RU, "ru-RU-DmitryNeural",
            Language.EN, "en-US-AriaNeural",
            Language.DE, "de-DE-KatjaNeural",
            Language.FR, "fr-FR-DeniseNeural",
            Language.ES, "es-ES-ElviraNeural");

    /** Логгер сервиса. */
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** JSON-маппер для тела запроса. */
    private final ObjectMapper json = new ObjectMapper();
    /** HTTP-клиент. */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    /** Адрес эндпоинта синтеза. */
    private final URI endpoint;

    /**
     * @param baseUrl базовый адрес сервиса, например {@code http://localhost:5050}
     */
    public EdgeTtsService(String baseUrl) {
        String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.endpoint = URI.create(base + "/v1/audio/speech");
    }

    /** {@inheritDoc} */
    @Override
    public List<byte[]> synthesize(String text, Language language) {
        List<byte[]> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        String voice = VOICES.getOrDefault(language, VOICES.get(Language.EN));
        for (String chunk : split(text)) {
            byte[] audio = request(chunk, voice);
            if (audio == null) {
                return List.of();
            }
            parts.add(audio);
        }
        return parts;
    }

    /**
     * Отправляет один фрагмент в сервис озвучки.
     *
     * @param text  текст фрагмента
     * @param voice голос
     * @return OGG/Opus-аудио или {@code null} при ошибке
     */
    private byte[] request(String text, String voice) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", "tts-1",
                    "input", text,
                    "voice", voice,
                    "response_format", "opus"));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = http.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("TTS returned HTTP {}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (IOException e) {
            log.warn("TTS request failed: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("TTS request interrupted");
            return null;
        }
    }

    /**
     * Разбивает текст на части по предложениям.
     *
     * @param text исходный текст
     * @return непустой список фрагментов длиной не более {@value #MAX_CHUNK}
     */
    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (sentence.length() > MAX_CHUNK) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int start = 0; start < sentence.length(); start += MAX_CHUNK) {
                    chunks.add(sentence.substring(start,
                            Math.min(start + MAX_CHUNK, sentence.length())).trim());
                }
            } else if (current.length() + sentence.length() + 1 > MAX_CHUNK) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(sentence).append(' ');
            } else {
                current.append(sentence).append(' ');
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks.stream().filter(chunk -> !chunk.isEmpty()).toList();
    }
}
