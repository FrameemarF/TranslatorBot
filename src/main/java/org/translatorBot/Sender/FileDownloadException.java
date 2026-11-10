package org.translatorBot.Sender;

/**
 * Ошибка скачивания файла из мессенджера. Наследуется от
 * {@link RuntimeException}: вызывающий код не обязан объявлять её в сигнатуре.
 */
public class FileDownloadException extends RuntimeException {

    /**
     * @param message описание ошибки
     */
    public FileDownloadException(String message) {
        super(message);
    }

    /**
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public FileDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
