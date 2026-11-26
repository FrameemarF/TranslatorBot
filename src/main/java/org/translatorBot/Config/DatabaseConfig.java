package org.translatorBot.Config;

/**
 * Настройки подключения к базе данных.
 *
 * @param url      JDBC-адрес
 * @param user     имя пользователя
 * @param password пароль
 */
public record DatabaseConfig(String url, String user, String password) {
}
