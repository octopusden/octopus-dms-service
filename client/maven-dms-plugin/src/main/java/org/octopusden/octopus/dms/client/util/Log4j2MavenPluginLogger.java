package org.octopusden.octopus.dms.client.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wrap log4j2 logger as apache maven plugin logger
 */
public class Log4j2MavenPluginLogger implements org.apache.maven.plugin.logging.Log {
    private final Logger logger;

    public Log4j2MavenPluginLogger(final Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }

    public static Log4j2MavenPluginLogger getLogger(final Class<?> clazz) {
        return new Log4j2MavenPluginLogger(clazz);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence content) {
        logger.debug(content);
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        logger.debug(content, error);
    }

    @Override
    public void debug(Throwable error) {
        logger.debug(error);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(CharSequence content) {
        logger.info(content);
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        logger.info(content, error);
    }

    @Override
    public void info(Throwable error) {
        logger.info(error);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(CharSequence content) {
        logger.warn(content);
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        logger.warn(content, error);
    }

    @Override
    public void warn(Throwable error) {
        logger.warn(error);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(CharSequence content) {
        logger.error(content);
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        logger.error(content, error);
    }

    @Override
    public void error(Throwable error) {
        logger.error(error);
    }
}
