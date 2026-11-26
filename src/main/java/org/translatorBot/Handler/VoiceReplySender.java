package org.translatorBot.Handler;

import org.translatorBot.History.Platform;
import org.translatorBot.Language;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Service.Speech.SpeechSynthesisService;
import org.translatorBot.Ui;
import org.translatorBot.User.ResponseMode;
import org.translatorBot.User.UserProfile;
import org.translatorBot.User.UserService;

import java.util.List;

/**
 * Озвучивание перевода в соответствии с настройками пользователя.
 * <p>
 * Сначала проверяет режим ответа, затем синтезирует речь и отправляет её
 * отдельными сообщениями. Если синтез не удался, возвращается пустой список —
 * обработчик добавляет к тексту предупреждение.
 */
public class VoiceReplySender {
    /** Отправитель сообщений. */
    private final MessageSender sender;
    /** Сервис синтеза речи. */
    private final SpeechSynthesisService tts;
    /** Профили пользователей. */
    private final UserService users;
    /** Платформа пользователя. */
    private final Platform platform;

    /**
     * @param sender   отправитель сообщений
     * @param tts      сервис синтеза речи
     * @param users    профили пользователей
     * @param platform платформа пользователя
     */
    public VoiceReplySender(MessageSender sender, SpeechSynthesisService tts,
                            UserService users, Platform platform) {
        this.sender = sender;
        this.tts = tts;
        this.users = users;
        this.platform = platform;
    }

    /**
     * Готовит аудио, если у пользователя включён режим «текст + голос».
     *
     * @param accountId идентификатор аккаунта
     * @param text      переведённый текст
     * @param target    язык перевода
     * @return {@code null}, если озвучка выключена; пустой список при ошибке
     */
    public List<byte[]> prepare(long accountId, String text, Language target) {
        UserProfile profile = users.profile(platform, accountId).orElse(null);
        if (profile == null || profile.responseMode() != ResponseMode.TEXT_AND_VOICE) {
            return null;
        }
        return tts.synthesize(text, target);
    }

    /**
     * Отправляет подготовленные части аудио.
     *
     * @param chatId идентификатор чата
     * @param parts  части аудио или {@code null}
     */
    public void send(long chatId, List<byte[]> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        for (byte[] part : parts) {
            sender.sendVoice(chatId, part, "voice.ogg");
        }
    }
}
