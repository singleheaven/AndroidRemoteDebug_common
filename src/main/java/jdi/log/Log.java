package jdi.log;

import javax.inject.Inject;
import javax.inject.Named;

import ioc.Injector;

public final class Log {
    private Log() {
    }

    public interface ILog {
        void e(String tag, String message, Throwable t);

        void d(String tag, String s);

        void e(String tag, String message);
    }

    @Inject
    @Named("loggerImpl")
    private ILog logger;

    private static Log log = Injector.getInstance(Log.class);

    public static void e(String tag, String message, Throwable t) {
        log.logger.e(tag, message, t);
    }

    public static void d(String tag, String s) {
        log.logger.d(tag, s);
    }

    public static void e(String tag, String message) {
        log.logger.e(tag, message);
    }
}
