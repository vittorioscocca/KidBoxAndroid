package android.util;

/**
 * Rimpiazza per i soli unit test JVM lo stub di android.util.Log dell'android.jar, che lancia
 * "not mocked" a ogni chiamata: KBLog è attraversato da mezzo codebase, quindi senza questo
 * qualunque test che tocchi un ramo con un log fallirebbe per il log e non per la logica.
 * Sta nel test source set e non in main proprio perché a runtime sul device deve restare
 * il Log vero del framework; qui basta che le chiamate siano innocue e visibili a stdout.
 * Preferito a testOptions.unitTests.isReturnDefaultValues, che invece silenzierebbe in blocco
 * ogni API Android non mockata e nasconderebbe stub davvero problematici negli altri test.
 */
public final class Log {

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {
    }

    public static int v(String tag, String msg) {
        return println(VERBOSE, tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        return println(VERBOSE, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int d(String tag, String msg) {
        return println(DEBUG, tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        return println(DEBUG, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int i(String tag, String msg) {
        return println(INFO, tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        return println(INFO, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int w(String tag, String msg) {
        return println(WARN, tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        return println(WARN, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int w(String tag, Throwable tr) {
        return println(WARN, tag, getStackTraceString(tr));
    }

    public static int e(String tag, String msg) {
        return println(ERROR, tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        return println(ERROR, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static int wtf(String tag, String msg) {
        return println(ASSERT, tag, msg);
    }

    public static int wtf(String tag, Throwable tr) {
        return println(ASSERT, tag, getStackTraceString(tr));
    }

    public static int wtf(String tag, String msg, Throwable tr) {
        return println(ASSERT, tag, msg + '\n' + getStackTraceString(tr));
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    public static int println(int priority, String tag, String msg) {
        String line = priority + "/" + tag + ": " + msg;
        System.out.println(line);
        return line.length();
    }
}
