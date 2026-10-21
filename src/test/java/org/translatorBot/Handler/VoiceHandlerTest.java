package org.translatorBot.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.translatorBot.Language;
import org.translatorBot.Sender;
import org.translatorBot.Service.Speech.SpeechRecognitionService;
import org.translatorBot.Service.Speech.SpeechUnavailableException;
import org.translatorBot.Service.Speech.SpeechUnrecognizedException;
import org.translatorBot.Service.Translation.TranslationService;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Модульные тесты для {@link VoiceHandler}: только основные сценарии.
 * <p>
 * Тесты написаны без библиотек мокирования и без статических членов: в них нет
 * статических полей, методов, вложенных классов и статических импортов. Все
 * зависимости обработчика заменены собственными реализациями-«шпионами»:
 * {@link RecordingSender}, {@link FakeSpeech} и {@link FakeTranslator}.
 * Входящие {@link Message} создаются из JSON так, как их присылает Telegram.
 * Границы длительности (30 и 31 секунда) заданы числами напрямую.
 */
class VoiceHandlerTest {
    /** Идентификатор чата, в котором «происходит» диалог. */
    private final long chatId = 100L;
    /** Идентификатор голосового файла в Telegram. */
    private final String fileId = "voice-file-id";
    /** Преобразует JSON-узлы в объекты Telegram API. */
    private final ObjectMapper json = new ObjectMapper();

    /** Одно сообщение, отправленное через {@link RecordingSender}. */
    private class SentMessage {
        /** Текст сообщения. */
        final String text;
        /** Клавиатура сообщения или {@code null}. */
        final InlineKeyboardMarkup keyboard;

        /**
         * @param text     текст сообщения
         * @param keyboard клавиатура или {@code null}
         */
        SentMessage(String text, InlineKeyboardMarkup keyboard) {
            this.text = text;
            this.keyboard = keyboard;
        }
    }

    /**
     * Заглушка {@link Sender}: клиент Telegram не нужен, потому что
     * {@code send} и {@code download} переопределены и ничего не отправляют по сети.
     */
    private class RecordingSender extends Sender {
        /** Все отправленные сообщения в порядке отправки. */
        final List<SentMessage> sent = new ArrayList<>();
        /** Идентификаторы файлов, которые просили скачать. */
        final List<String> downloaded = new ArrayList<>();
        /** Содержимое, которое «скачивается» из Telegram. */
        byte[] content = new byte[]{1, 2, 3};
        /** Если не {@code null}, {@link #download(String)} бросает эту ошибку. */
        Exception downloadError;

        /** Создаёт отправителя без реального клиента Telegram. */
        RecordingSender() {
            super(null);
        }

        /** {@inheritDoc} */
        @Override
        public void send(long chatId, String text) {
            sent.add(new SentMessage(text, null));
        }

        /** {@inheritDoc} */
        @Override
        public void send(long chatId, String text, InlineKeyboardMarkup keyboard) {
            sent.add(new SentMessage(text, keyboard));
        }

        /** {@inheritDoc} */
        @Override
        public byte[] download(String fileId) throws Exception {
            downloaded.add(fileId);
            if (downloadError != null) throw downloadError;
            return content;
        }
    }

    /** Заглушка {@link SpeechRecognitionService} с управляемым поведением. */
    private class FakeSpeech implements SpeechRecognitionService {
        /** Текст, который «распознан» в аудио. */
        String reply = "Привет, мир";
        /** Если не {@code null}, {@link #recognize} бросает эту ошибку. */
        Exception error;
        /** Вызовы в формате {@code язык:имяФайла:байты}. */
        final List<String> calls = new ArrayList<>();
        /** Последние переданные аудиоданные. */
        byte[] lastAudio;

        /** {@inheritDoc} */
        @Override
        public String recognize(byte[] audio, String fileName, Language language) throws Exception {
            lastAudio = audio;
            calls.add(language + ":" + fileName);
            if (error != null) throw error;
            return reply;
        }
    }

    /** Заглушка {@link TranslationService} с управляемым поведением. */
    private class FakeTranslator implements TranslationService {
        /** Результат перевода. */
        String reply = "Hello, world";
        /** Если {@code true}, перевод завершается ошибкой. */
        boolean fail = false;
        /** Вызовы в формате {@code ИЗ>В:текст}. */
        final List<String> calls = new ArrayList<>();

        /** {@inheritDoc} */
        @Override
        public String translate(String text, Language from, Language to) {
            calls.add(from + ">" + to + ":" + text);
            if (fail) throw new IllegalStateException("translation failed");
            return reply;
        }
    }

    private RecordingSender sender;
    private FakeSpeech speech;
    private FakeTranslator translator;
    private Ui ui;
    private VoiceHandler handler;
    private Session session;

    /**
     * Создаёт свежие зависимости и обработчик перед каждым тестом. Сессия
     * находится в состоянии ожидания текста, язык RU → EN.
     */
    @BeforeEach
    void setUp() {
        sender = new RecordingSender();
        speech = new FakeSpeech();
        translator = new FakeTranslator();
        ui = new Ui();
        handler = new VoiceHandler(sender, speech, translator, ui);

        session = new Session();
        session.setState(Session.State.WAITING_TEXT);
        session.setSource(Language.RU);
        session.setTarget(Language.EN);
    }

    // ---------- помощники ----------

    /**
     * Создаёт JSON-узел сообщения Telegram без полезной нагрузки.
     *
     * @return узел с идентификатором, датой и чатом
     */
    private ObjectNode baseMessage() {
        ObjectNode chat = json.createObjectNode();
        chat.put("id", chatId);
        chat.put("type", "private");
        ObjectNode m = json.createObjectNode();
        m.put("message_id", 1);
        m.put("date", 1);
        m.set("chat", chat);
        return m;
    }

    /**
     * Создаёт голосовое сообщение.
     *
     * @param duration длительность в секундах
     * @return голосовое сообщение
     * @throws Exception если десериализация не удалась
     */
    private Message voiceMessage(int duration) throws Exception {
        ObjectNode voice = json.createObjectNode();
        voice.put("file_id", fileId);
        voice.put("file_unique_id", "unique-id");
        voice.put("duration", duration);
        ObjectNode m = baseMessage();
        m.set("voice", voice);
        return json.treeToValue(m, Message.class);
    }

    /**
     * Создаёт текстовое сообщение.
     *
     * @param text текст сообщения
     * @return текстовое сообщение
     * @throws Exception если десериализация не удалась
     */
    private Message textMessage(String text) throws Exception {
        ObjectNode m = baseMessage();
        m.put("text", text);
        return json.treeToValue(m, Message.class);
    }

    /**
     * Проверяет, что в чат ушло ровно одно сообщение, и возвращает его.
     *
     * @return единственное отправленное сообщение
     */
    private SentMessage onlySent() {
        Assertions.assertEquals(1, sender.sent.size());
        return sender.sent.get(0);
    }

    // ---------- supports ----------

    /** Обработчик поддерживает голосовое сообщение и не поддерживает текстовое. */
    @Test
    void supportsOnlyVoiceMessages() throws Exception {
        Assertions.assertTrue(handler.supports(voiceMessage(5)));
        Assertions.assertFalse(handler.supports(textMessage("Привет")));
    }

    // ---------- handle: успешный сценарий ----------

    /** Успешный сценарий: ответ с распознанным текстом, переводом и клавиатурой, сессия в IDLE. */
    @Test
    void successfulVoiceIsTranslatedAndAnswered() throws Exception {
        handler.handle(chatId, voiceMessage(10), session);

        SentMessage reply = onlySent();
        Assertions.assertEquals(
                ui.recognized("Привет, мир") + "\n" + ui.translation("Hello, world"),
                reply.text);
        Assertions.assertEquals(ui.afterTranslation(), reply.keyboard);
        Assertions.assertEquals(Session.State.IDLE, session.getState());
    }

    /** Файл скачивается по id голосового, а распознавателю и переводчику уходят правильные данные. */
    @Test
    void passesCorrectDataToDependencies() throws Exception {
        sender.content = new byte[]{9, 8, 7};
        session.setSource(Language.DE);
        session.setTarget(Language.FR);

        handler.handle(chatId, voiceMessage(10), session);

        Assertions.assertEquals(List.of(fileId), sender.downloaded);
        Assertions.assertArrayEquals(new byte[]{9, 8, 7}, speech.lastAudio);
        Assertions.assertEquals(List.of("DE:voice.ogg"), speech.calls);
        Assertions.assertEquals(List.of("DE>FR:Привет, мир"), translator.calls);
    }

    // ---------- handle: ограничение длительности ----------

    /** Голосовое длиннее 30 секунд отклоняется и никак не обрабатывается. */
    @Test
    void tooLongVoiceIsRejectedWithoutProcessing() throws Exception {
        handler.handle(chatId, voiceMessage(31), session);

        Assertions.assertEquals(ui.voiceTooLong, onlySent().text);
        Assertions.assertTrue(sender.downloaded.isEmpty());
        Assertions.assertTrue(speech.calls.isEmpty());
        Assertions.assertTrue(translator.calls.isEmpty());
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    /** Голосовое ровно в 30 секунд принимается: граница включается в допустимый диапазон. */
    @Test
    void voiceExactlyAtLimitIsAccepted() throws Exception {
        handler.handle(chatId, voiceMessage(30), session);

        Assertions.assertEquals(List.of(fileId), sender.downloaded);
        Assertions.assertEquals(Session.State.IDLE, session.getState());
    }

    // ---------- handle: ошибки ----------

    /** Если речь не найдена, пользователь получает подсказку, перевод не вызывается. */
    @Test
    void unrecognizedSpeechShowsHint() throws Exception {
        speech.error = new SpeechUnrecognizedException();

        handler.handle(chatId, voiceMessage(10), session);

        Assertions.assertEquals(ui.speechUnrecognized, onlySent().text);
        Assertions.assertTrue(translator.calls.isEmpty());
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    /** Недоступность сервиса распознавания сообщается отдельным текстом, перевод не вызывается. */
    @Test
    void unavailableSpeechServiceShowsUnavailableMessage() throws Exception {
        speech.error = new SpeechUnavailableException("api key missing");

        handler.handle(chatId, voiceMessage(10), session);

        Assertions.assertEquals(ui.speechUnavailable, onlySent().text);
        Assertions.assertTrue(translator.calls.isEmpty());
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }

    /** Ошибка скачивания файла сообщается как недоступность распознавания, распознавание не запускается. */
    @Test
    void downloadFailureShowsUnavailableMessage() throws Exception {
        sender.downloadError = new IOException("telegram is down");

        handler.handle(chatId, voiceMessage(10), session);

        Assertions.assertEquals(ui.speechUnavailable, onlySent().text);
        Assertions.assertTrue(speech.calls.isEmpty());
    }

    /** Если речь распознана, но перевод упал, пользователь получает ошибку перевода, сессия не меняется. */
    @Test
    void translationFailureShowsTranslateError() throws Exception {
        translator.fail = true;

        handler.handle(chatId, voiceMessage(10), session);

        Assertions.assertEquals(ui.translateErr, onlySent().text);
        Assertions.assertEquals(Session.State.WAITING_TEXT, session.getState());
    }
}