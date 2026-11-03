package org.translatorBot.Sender;

import org.translatorBot.Model.AppKeyboard;

/**
 * Универсальный интерфейс для отправки сообщений и скачивания файлов.
 * Позволяет абстрагировать бизнес-логику от конкретной платформы (Telegram, VK и т.д.).
 */
public interface MessageSender {

    /**
     * Отправляет текстовое сообщение без клавиатуры.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    void send(long chatId, String text);

    /**
     * Отправляет текстовое сообщение с опциональной клавиатурой.
     *
     * @param chatId   идентификатор чата
     * @param text     текст сообщения
     * @param keyboard универсальная клавиатура или {@code null}
     */
    void send(long chatId, String text, AppKeyboard keyboard);

    /**
     * Подтверждает callback-запрос.
     *
     * @param callbackId идентификатор callback-запроса
     */
    void ackCallback(String callbackId);

    /**
     * Скачивает файл по идентификатору.
     *
     * @param fileId идентификатор файла
     * @return содержимое файла
     * @throws Exception при ошибке скачивания
     */
    byte[] download(String fileId) throws Exception;

    /**
     * Отправляет документ с подписью и клавиатурой.
     *
     * @param chatId   идентификатор чата
     * @param fileName имя файла
     * @param content  содержимое файла
     * @param caption  подпись
     * @param keyboard универсальная клавиатура или {@code null}
     */
    void sendDocument(long chatId, String fileName, byte[] content, String caption, AppKeyboard keyboard);
}