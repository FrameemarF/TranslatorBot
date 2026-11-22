package org.translatorBot;

/**
 * Точка входа приложения-переводчика.
 */
public class Main {

    /**
     * Запускает {@link BotApplication}.
     *
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        if (!new BotApplication().run()) {
            System.exit(1);
        }
    }
}
