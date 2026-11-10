package org.translatorBot.Sender;

import org.translatorBot.Model.AppKeyboard;

import java.nio.file.Path;

/**
 * Универсальный интерфейс для отправки сообщений и работы с файлами.
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
     * Скачивает файл по идентификатору во временный файл.
     * <p>
     * Вызывающий код отвечает за удаление полученного файла.
     *
     * @param fileId идентификатор файла
     * @return путь к скачанному временному файлу
     * @throws FileDownloadException при ошибке скачивания
     */
    Path download(String fileId);

    /**
     * Отправляет документ с подписью и клавиатурой.
     *
     * @param chatId   идентификатор чата
     * @param fileName имя файла
     * @param content  путь к содержимому файла
     * @param caption  подпись
     * @param keyboard универсальная клавиатура или {@code null}
     */
    void sendDocument(long chatId, String fileName, Path content, String caption, AppKeyboard keyboard);
}
