package org.translatorBot.Model;

/**
 * Универсальная кнопка для любого мессенджера.
 *
 * @param text         текст, отображаемый на кнопке
 * @param callbackData данные, отправляемые при нажатии на кнопку
 */
public record AppButton(
        String text,
        String callbackData
) {}