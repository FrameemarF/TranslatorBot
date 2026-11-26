package org.translatorBot.User;

/** Режим ответа бота по умолчанию. */
public enum ResponseMode {
    /** Отправлять только текст (или файл). */
    TEXT("только текст"),
    /** Дополнительно озвучивать перевод. */
    TEXT_AND_VOICE("текст + голос");

    /** Человекочитаемое название режима. */
    private final String label;

    /**
     * @param label название для интерфейса
     */
    ResponseMode(String label) {
        this.label = label;
    }

    /** @return название режима для интерфейса */
    public String label() {
        return label;
    }
}
