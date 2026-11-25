package org.translatorBot.Handler;

import org.translatorBot.Language;
import org.translatorBot.Platforms.MessageSender;
import org.translatorBot.Session.Session;
import org.translatorBot.Ui;

/**
 * Шаги диалога бота: показ меню и справки, выбор языков, запрос текста
 * и возврат на предыдущий шаг.
 * <p>
 * Класс общий для {@link CommandHandler}, {@link CallbackHandler} и {@link Router}:
 * Каждый шаг меняет состояние {@link Session} и отправляет пользователю
 * соответствующее сообщение.
 */
public class DialogFlow {
    private final MessageSender sender;
    private final Ui ui;

    /**
     * Создаёт сценарий диалога с заданными зависимостями.
     *
     * @param sender отправитель сообщений
     * @param ui     источник текстов и клавиатур
     */
    public DialogFlow(MessageSender sender, Ui ui) {
        this.sender = sender;
        this.ui = ui;
    }

    /**
     * Сбрасывает сессию и показывает приветствие с главным меню.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    public void showMenu(long chatId, Session s) {
        s.reset();
        sender.send(chatId, ui.start, ui.mainMenu());
    }

    /**
     * Показывает справку.
     *
     * @param chatId идентификатор чата
     */
    public void showHelp(long chatId) {
        sender.send(chatId, ui.help);
    }

    /**
     * Сообщает, что команда или кнопка не распознаны.
     *
     * @param chatId идентификатор чата
     */
    public void showUnknown(long chatId) {
        sender.send(chatId, ui.unknownCmd);
    }

    /**
     * Подсказывает, как начать работу, если сообщение пришло вне сценария перевода.
     *
     * @param chatId идентификатор чата
     */
    public void showUseStart(long chatId) {
        sender.send(chatId, ui.useStart, ui.mainMenu());
    }

    /**
     * Сообщает, что присланный тип материала не поддерживается.
     *
     * @param chatId идентификатор чата
     */
    public void showUnsupportedMaterial(long chatId) {
        sender.send(chatId, ui.unsupportedMaterial);
    }

    /**
     * Начинает перевод как обычно: пользователь выбирает оба языка.
     * Используется кнопкой «Сменить язык», чтобы настройка языка-цели
     * по умолчанию не менялась.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    public void startSourceChoice(long chatId, Session s) {
        s.reset();
        s.setState(Session.State.CHOOSING_SOURCE);
        sender.send(chatId, ui.chooseSourceLang, ui.sourceChoice());
    }

    /**
     * Начинает перевод с языком-целью из настроек: если он задан, пользователь
     * выбирает только язык-источник.
     *
     * @param chatId        идентификатор чата
     * @param s             сессия пользователя
     * @param defaultTarget язык-цель по умолчанию или {@code null}
     */
    public void startTranslation(long chatId, Session s, Language defaultTarget) {
        s.reset();
        s.setTarget(defaultTarget);
        s.setState(Session.State.CHOOSING_SOURCE);
        sender.send(chatId, ui.chooseSourceLang, ui.sourceChoice());
    }

    /**
     * Сохраняет выбранный язык-источник. Если язык-цель уже задан настройками,
     * шаг выбора цели пропускается.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     * @param source выбранный язык-источник
     */
    public void pickSource(long chatId, Session s, Language source) {
        s.setSource(source);
        if (s.getTarget() != null) {
            if (s.getTarget() == source) {
                s.setTarget(null);
                s.setState(Session.State.CHOOSING_TARGET);
                sender.send(chatId, ui.sameLanguages, ui.targetChoice());
            } else {
                askForText(chatId, s);
            }
            return;
        }
        s.setState(Session.State.CHOOSING_TARGET);
        sender.send(chatId, ui.chooseTargetLang, ui.targetChoice());
    }

    /**
     * Сохраняет выбранный язык-цель. Если он совпадает с языком-источником,
     * просит выбрать другой. Иначе переводит сессию к ожиданию текста.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     * @param target выбранный язык-цель
     */
    public void pickTarget(long chatId, Session s, Language target) {
        if (target == s.getSource()) {
            sender.send(chatId, ui.sameLanguages, ui.targetChoice());
            return;
        }
        s.setTarget(target);
        askForText(chatId, s);
    }

    /**
     * Переводит сессию в состояние ожидания текста и просит пользователя
     * отправить материал для перевода.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    public void askForText(long chatId, Session s) {
        s.setState(Session.State.WAITING_TEXT);
        sender.send(chatId, ui.askText);
    }

    /**
     * Возвращает пользователя на предыдущий шаг сценария в зависимости от
     * текущего состояния сессии. Если шаг назад невозможен — сбрасывает
     * сессию и показывает главное меню.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    public void goBack(long chatId, Session s) {
        switch (s.getState()) {
            case WAITING_TEXT -> {
                s.setState(Session.State.CHOOSING_TARGET);
                sender.send(chatId, ui.chooseTargetLang, ui.targetChoice());
            }
            case CHOOSING_TARGET -> startSourceChoice(chatId, s);
            default -> {
                s.reset();
                sender.send(chatId, ui.backToMenu, ui.mainMenu());
            }
        }
    }
}