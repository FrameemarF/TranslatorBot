package org.translatorBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.translatorBot.Service.Translation.TranslationService;

import java.util.List;

/**
 * Маршрутизатор входящих обновлений от Telegram.
 * <p>
 * Реализует {@link LongPollingUpdateConsumer}: принимает пачку {@link Update},
 * разбирает каждый апдейт и направляет его в соответствующий обработчик —
 * команды, нажатия inline-кнопок или текст для перевода. Управляет состоянием
 * диалога через {@link SessionStore} и использует {@link Ui} для формирования
 * ответов.
 */
public class Router implements LongPollingUpdateConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final int maxTextLength = 4000;

    private final Sender sender;
    private final SessionStore sessions;
    private final TranslationService translator;
    private final Ui ui;

    /**
     * Создаёт маршрутизатор с заданными зависимостями.
     *
     * @param sender     отправитель сообщений
     * @param sessions   хранилище сессий
     * @param translator сервис перевода
     * @param ui         источник текстов и клавиатур
     */
    public Router(Sender sender, SessionStore sessions,
                  TranslationService translator, Ui ui) {
        this.sender = sender;
        this.sessions = sessions;
        this.translator = translator;
        this.ui = ui;
    }

    /**
     * Обрабатывает пачку обновлений, полученных от Telegram.
     *
     * @param updates список входящих обновлений
     */
    @Override
    public void consume(List<Update> updates) {
        updates.forEach(this::handleUpdate);
    }

    /**
     * Разбирает одно обновление и направляет его в обработчик команд,
     * сообщений или нажатий inline-кнопок. Ошибки логируются и не
     * прерывают обработку остальных обновлений.
     *
     * @param update входящее обновление
     */
    private void handleUpdate(Update update) {
        try {
            if (update.hasCallbackQuery()) handleCallback(update.getCallbackQuery());
            else if (update.hasMessage() && update.getMessage().hasText()) handleMessage(update.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle update", e);
        }
    }

    /**
     * Обрабатывает текстовое сообщение пользователя: распознаёт команды,
     * текст в состоянии ожидания перевода, иначе показывает подсказку
     * с главным меню.
     *
     * @param msg сообщение от Telegram
     */
    private void handleMessage(Message msg) {
        long chatId = msg.getChatId();
        String text = msg.getText().trim();
        Session session = sessions.get(chatId);

        if (text.startsWith("/")) {
            handleCommand(chatId, text.split("\\s+")[0], session);
        } else if (session.getState() == Session.State.WAITING_TEXT) {
            handleTranslation(chatId, text, session);
        } else {
            sender.send(chatId, ui.useStart, ui.mainMenu());
        }
    }

    /**
     * Обрабатывает команду пользователя и выполняет соответствующее действие.
     *
     * @param chatId  идентификатор чата
     * @param cmd     распознанная команда (первое слово сообщения)
     * @param s       сессия пользователя
     */
    private void handleCommand(long chatId, String cmd, Session s) {
        switch (cmd) {
            case "/start"     -> { s.reset(); sender.send(chatId, ui.start, ui.mainMenu()); }
            case "/help"      -> sender.send(chatId, ui.help);
            case "/translate" -> startSourceChoice(chatId, s);
            case "/back"      -> goBack(chatId, s);
            default           -> {
                sender.send(chatId, ui.unknownCmd);
                sender.send(chatId, ui.help);
            }
        }
    }

    /**
     * Обрабатывает нажатие inline-кнопки: подтверждает callback у Telegram
     * и выполняет действие, соответствующее {@code callbackData}.
     *
     * @param q callback-запрос от Telegram
     */
    private void handleCallback(CallbackQuery q) {
        sender.ackCallback(q.getId());
        long chatId = q.getMessage().getChatId();
        Session s = sessions.get(chatId);
        String data = q.getData();

        switch (data) {
            case "TRANSLATE"   -> startSourceChoice(chatId, s);
            case "HELP"        -> sender.send(chatId, ui.help);
            case "SRC_RU"      -> pickSource(chatId, s, Language.RU);
            case "SRC_EN"      -> pickSource(chatId, s, Language.EN);
            case "TGT_RU"      -> pickTarget(chatId, s, Language.RU);
            case "TGT_EN"      -> pickTarget(chatId, s, Language.EN);
            case "MORE"        -> askForText(chatId, s);
            case "CHANGE_LANG" -> startSourceChoice(chatId, s);
            case "MENU"        -> { s.reset(); sender.send(chatId, ui.start, ui.mainMenu()); }
            default            -> sender.send(chatId, ui.unknownCmd);
        }
    }

    // ---------- Шаги сценария ----------

    /**
     * Переводит сессию в состояние выбора языка-источника, сбрасывает
     * ранее выбранные языки и предлагает пользователю выбор.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    private void startSourceChoice(long chatId, Session s) {
        s.setState(Session.State.CHOOSING_SOURCE);
        s.setSource(null);
        s.setTarget(null);
        sender.send(chatId, ui.chooseSourceLang, ui.sourceChoice());
    }

    /**
     * Сохраняет выбранный язык-источник и переводит сессию к выбору языка-цели.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     * @param source выбранный язык-источник
     */
    private void pickSource(long chatId, Session s, Language source) {
        s.setSource(source);
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
    private void pickTarget(long chatId, Session s, Language target) {
        if (target == s.getSource()) {
            sender.send(chatId, "⚠\uFE0F Языки совпадают. Выбери другой целевой язык.",
                    ui.targetChoice());
            return;
        }
        s.setTarget(target);
        askForText(chatId, s);
    }

    /**
     * Переводит сессию в состояние ожидания текста и просит пользователя
     * отправить текст для перевода.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    private void askForText(long chatId, Session s) {
        s.setState(Session.State.WAITING_TEXT);
        sender.send(chatId, ui.askText);
    }

    /**
     * Обрабатывает команду {@code /back}: возвращает пользователя на предыдущий
     * шаг сценария в зависимости от текущего состояния сессии. Если шаг назад
     * невозможен — сбрасывает сессию и показывает главное меню.
     *
     * @param chatId идентификатор чата
     * @param s      сессия пользователя
     */
    private void goBack(long chatId, Session s) {
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

    // ---------- Перевод ----------

    /**
     * Обрабатывает текст, отправленный пользователем в состоянии ожидания
     * перевода: проверяет текст на пустоту и длину, вызывает
     * {@link TranslationService} и отправляет пользователю результат или
     * сообщение об ошибке.
     *
     * @param chatId идентификатор чата
     * @param text   текст для перевода
     * @param s      сессия пользователя
     */
    private void handleTranslation(long chatId, String text, Session s) {
        if (text.isBlank()) {
            sender.send(chatId, ui.emptyText);
            return;
        }
        if (text.length() > maxTextLength) {
            sender.send(chatId, ui.tooLong);
            return;
        }

        if (s.getSource() == null || s.getTarget() == null) {
            startSourceChoice(chatId, s);
            return;
        }

        Language source = s.getSource();
        Language target = s.getTarget();

        try {
            String result = translator.translate(text, source, target);
            s.setState(Session.State.IDLE);
            sender.send(chatId, "✅ Перевод: " + result, ui.afterTranslation());
        } catch (Exception e) {
            log.error("Translation failed for chat {}", chatId, e);
            sender.send(chatId, ui.translateErr);
        }
    }
}