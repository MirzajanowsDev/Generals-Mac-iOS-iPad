package me.generalsx.zh;

import android.content.Context;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;

// GeneralsX @feature Codex 04/10/2026 Persist release startup and import diagnostics.
final class AppDiagnostics {
    private static File directory;
    private static boolean initialized;

    static synchronized void init(Context context) {
        if (initialized) return;
        initialized = true;
        File storage = context.getExternalFilesDir(null);
        directory = new File(storage != null ? storage : context.getFilesDir(), "diagnostics");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.e("GeneralsSetup", "Cannot create diagnostics directory " + directory);
        }
        write("START", "version=" + BuildConfig.VERSION_NAME + " device=" + Build.MODEL
            + " SDK=" + Build.VERSION.SDK_INT + " pid=" + Process.myPid(), null);
        captureExitHistory(context);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write("JAVA CRASH", "thread=" + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
        Thread collector = new Thread(() -> {
            java.lang.Process process = null;
            try {
                process = new ProcessBuilder("logcat", "-b", "all", "-v", "threadtime", "--pid=" + Process.myPid(),
                    "GeneralsX:V", "GeneralsNative:V", "GeneralsLAN:V", "GeneralsSetup:V", "SDL:V", "AndroidRuntime:V", "libc:F", "*:S")
                    .redirectErrorStream(true).start();
                File oldLog = new File(directory, "logcat-last.txt");
                File previousLog = new File(directory, "logcat-previous.txt");
                if (previousLog.exists() && !previousLog.delete()) Log.w("GeneralsSetup", "Cannot rotate logcat");
                if (oldLog.exists() && !oldLog.renameTo(previousLog)) Log.w("GeneralsSetup", "Cannot preserve logcat");
                try (InputStream in = process.getInputStream();
                     OutputStream out = new FileOutputStream(new File(directory, "logcat-last.txt"))) {
                    byte[] buffer = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buffer)) != -1 && total < 4 * 1024 * 1024) {
                        out.write(buffer, 0, n);
                        out.flush();
                        total += n;
                    }
                }
            } catch (IOException error) {
                write("LOGCAT", "Cannot capture this process", error);
            } finally {
                if (process != null) process.destroy();
            }
        }, "gx-diagnostics");
        collector.setDaemon(true);
        collector.start();
    }

    // GeneralsX @bugfix Codex 05/10/2026 Native tombstones belong to debuggerd,
    // not the dead PID; preserve Android's own exit trace on the next launch.
    private static void captureExitHistory(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        try (OutputStream out = new FileOutputStream(new File(directory, "exit-history.txt"))) {
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5)) {
                String header = "\nPID=" + exit.getPid() + " time=" + exit.getTimestamp()
                    + " reason=" + exit.getReason() + " status=" + exit.getStatus()
                    + " description=" + exit.getDescription() + "\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
                try (InputStream trace = exit.getTraceInputStream()) {
                    if (trace != null) {
                        byte[] bytes = new byte[8192];
                        int remaining = 65536;
                        int count;
                        while (remaining > 0 && (count = trace.read(bytes, 0, Math.min(bytes.length, remaining))) != -1) {
                            out.write(bytes, 0, count);
                            remaining -= count;
                        }
                    }
                } catch (IOException error) {
                    out.write(("Trace unavailable: " + error + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | RuntimeException error) {
            write("EXIT HISTORY", "Cannot read previous process exits", error);
        }
    }

    static File getDirectory() { return directory; }

    static synchronized void write(String stage, String message, Throwable error) {
        String text = System.currentTimeMillis() + " " + stage + ": " + message + "\n"
            + (error == null ? "" : Log.getStackTraceString(error));
        Log.i("GeneralsSetup", text);
        if (directory == null) return;
        File file = new File(directory, "setup-last.txt");
        if (file.length() > 1024 * 1024) {
            File previous = new File(directory, "setup-previous.txt");
            if (previous.exists() && !previous.delete()) Log.w("GeneralsSetup", "Cannot rotate " + previous);
            if (!file.renameTo(previous)) Log.w("GeneralsSetup", "Cannot rotate " + file);
        }
        try (OutputStream out = new FileOutputStream(file, true)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            Log.e("GeneralsSetup", "Cannot write diagnostics", failure);
        }
    }

    static String report() {
        StringBuilder text = new StringBuilder();
        if (directory == null) return "Diagnostics unavailable";
        java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(
            "setup-last.txt", "native-previous.txt", "native-last.txt", "logcat-previous.txt", "logcat-last.txt", "exit-history.txt"));
        File[] graphicsLogs = directory.listFiles((dir, name) -> name.endsWith("_d3d8.log") || name.endsWith("_d3d9.log"));
        if (graphicsLogs != null) for (File file : graphicsLogs) names.add(file.getName());
        for (String name : names) {
            File file = new File(directory, name);
            text.append("\n=== ").append(name).append(" ===\n");
            try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                long start = Math.max(0, in.length() - 65536);
                in.seek(start);
                byte[] bytes = new byte[(int)(in.length() - start)];
                in.readFully(bytes);
                text.append(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException error) {
                text.append(error.getMessage()).append('\n');
            }
        }
        return text.toString();
    }
}

