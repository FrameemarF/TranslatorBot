package org.translatorBot.Session;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.translatorBot.Language;

/**
 * Модульные тесты для {@link Session}, проверяющие начальное состояние,
 * работу сеттеров и сброс сессии через {@link Session#reset()}.
 */
class SessionTest {

    /**
     * Проверяет, что новая сессия создаётся в состоянии {@link Session.State#IDLE}
     * и с пустыми (не выбранными) языками-источником и языком-целью.
     */
    @Test
    void newSessionIsIdleWithoutLanguages() {
        Session s = new Session();

        Assertions.assertEquals(Session.State.IDLE, s.getState());
        Assertions.assertNull(s.getSource());
        Assertions.assertNull(s.getTarget());
    }

    /**
     * Проверяет, что сеттеры корректно сохраняют переданные значения,
     * а геттеры возвращают именно их.
     */
    @Test
    void settersStoreValues() {
        Session s = new Session();

        s.setState(Session.State.WAITING_TEXT);
        s.setSource(Language.RU);
        s.setTarget(Language.EN);

        Assertions.assertEquals(Session.State.WAITING_TEXT, s.getState());
        Assertions.assertEquals(Language.RU, s.getSource());
        Assertions.assertEquals(Language.EN, s.getTarget());
    }

    /**
     * Проверяет, что {@link Session#reset()} возвращает сессию в исходное
     * состояние: {@link Session.State#IDLE} и {@code null} вместо языков.
     */
    @Test
    void resetRestoresInitialValues() {
        Session s = new Session();
        s.setState(Session.State.CHOOSING_TARGET);
        s.setSource(Language.EN);
        s.setTarget(Language.RU);

        s.reset();

        Assertions.assertEquals(Session.State.IDLE, s.getState());
        Assertions.assertNull(s.getSource());
        Assertions.assertNull(s.getTarget());
    }
}