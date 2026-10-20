package org.translatorBot.Service.Speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.translatorBot.Language;

/**
 * Распознавание речи с автоматическим запасным вариантом: сначала основной
 * сервис (облачный Groq), при его недоступности — запасной (локальный Whisper).
 * Пользователь получает результат независимо от лимитов Groq.
 */
public class FallbackSpeechService implements SpeechRecognitionService {

    /** Логгер для фиксации переключения на запасной сервис. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Основной сервис распознавания (например, облачный Groq). */
    private final SpeechRecognitionService primary;

    /** Запасной сервис распознавания (например, локальный Whisper). */
    private final SpeechRecognitionService fallback;

    /**
     * Создаёт сервис с указанными основным и запасным обработчиками.
     *
     * @param primary  основной сервис, используется первым
     * @param fallback запасной сервис, используется при ошибке основного
     */
    public FallbackSpeechService(SpeechRecognitionService primary,
                                 SpeechRecognitionService fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    /**
     * Пытается распознать речь основным сервисом, при необходимости переключаясь
     * на запасной.
     * <p>
     * Если основной сервис сообщает, что речи нет ({@link SpeechUnrecognizedException}),
     * исключение пробрасывается дальше — запасной сервис не вызывается, так как
     * проблема в самом аудио, а не в доступности сервиса.
     * <p>
     * При прерывании потока ({@link InterruptedException}) флаг прерывания
     * восстанавливается, исключение пробрасывается.
     * <p>
     * При любой другой ошибке основного сервиса (недоступность, таймаут, HTTP-ошибка
     * и т.п.) в лог пишется предупреждение, и запрос повторяется через запасной сервис.
     *
     * @param audio    байты аудиофайла
     * @param fileName имя файла для multipart-запроса
     * @param language ожидаемый язык речи
     * @return распознанный текст
     * @throws SpeechUnrecognizedException если в аудио нет речи (от основного сервиса)
     * @throws InterruptedException        если поток был прерван во время запроса
     * @throws Exception                   при прочих ошибках основного и запасного сервисов
     */
    @Override
    public String recognize(byte[] audio, String fileName, Language language) throws Exception {
        try {
            return primary.recognize(audio, fileName, language);
        } catch (SpeechUnrecognizedException e) {
            throw e; // в записи нет речи — запасной сервис не поможет
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.warn("Primary speech service failed ({}), switching to fallback", e.getMessage());
            return fallback.recognize(audio, fileName, language);
        }
    }
}
