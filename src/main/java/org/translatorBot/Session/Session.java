package org.translatorBot.Session;

import org.translatorBot.Language;

/**
 * Хранит состояние диалога одного пользователя в сценарии перевода.
 * <p>
 * Каждый экземпляр соответствует одному пользователю Telegram и содержит
 * текущий шаг сценария ({@link State}) и выбранные языки перевода
 * ({@link #source} и {@link #target}).
 */
public class Session {
    /**
     * Возможные шаги сценария перевода, в которых может находиться пользователь.
     */
    public enum State { IDLE, CHOOSING_SOURCE, CHOOSING_TARGET, WAITING_TEXT }

    private State state = State.IDLE;
    private Language source;
    private Language target;

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public Language getSource() { return source; }
    public void setSource(Language source) { this.source = source; }

    public Language getTarget() { return target; }
    public void setTarget(Language target) { this.target = target; }

    /**
     * Сбрасывает сессию в исходное состояние: переводит её в {@link State#IDLE}
     * и очищает выбранные языки.
     */
    public void reset() {
        this.state = State.IDLE;
        this.source = null;
        this.target = null;
    }
}