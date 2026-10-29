package org.translatorBot.Model;

import java.util.List;

/**
 * Универсальная клавиатура, состоящая из рядов кнопок, независимая от платформы.
 *
 * @param rows список рядов, где каждый ряд представляет собой список кнопок
 */
public record AppKeyboard(
        List<List<AppButton>> rows
) {}