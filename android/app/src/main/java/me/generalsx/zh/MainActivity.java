// GeneralsX @feature android-port 08/09/2026 (issue #12)
// GeneralsX @bugfix Codex 04/10/2026 Import the base Generals and Zero Hour installs together.
package me.generalsx.zh;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends Activity {

    private static final int REQUEST_IMPORT_GENERALS = 1001;
    private static final int REQUEST_IMPORT_ZERO_HOUR = 1002;
    private static final int REQUEST_EXPORT_LOG = 1003;
    private static final String STATE_GENERALS_TREE = "generalsTree";
    private static final String STATE_ZERO_HOUR_TREE = "zeroHourTree";
    private static final int COLOR_BACKGROUND = Color.rgb(12, 18, 32);
    private static final int COLOR_CARD = Color.rgb(25, 34, 52);
    private static final int COLOR_ACCENT = Color.rgb(241, 173, 67);
    private static final int COLOR_TEXT = Color.rgb(242, 245, 250);
    private static final int COLOR_MUTED = Color.rgb(160, 173, 194);

    /** A SAF directory queued for recursion: its tree URI + filesystem target. */
    private static final class DirEntry {
        final Uri uri;
        final File dest;
        DirEntry(Uri uri, File dest) { this.uri = uri; this.dest = dest; }
    }

    private Uri generalsTreeUri;
    private Uri zeroHourTreeUri;
    private boolean existingGameDataReady;
    private TextView generalsStatus;
    private TextView zeroHourStatus;
    private TextView importStatus;
    private Button importButton;

    private File externalGameData() {
        return new File(getExternalFilesDir(null), "GameData");
    }

    private File internalGameData() {
        return new File(getFilesDir(), "GameData");
    }

    // An incomplete copy of only one game's archives cannot launch Zero Hour.
    private static boolean looksLikeGameData(File dir) {
        File archives = new File(dir, "Data");
        return archives.isDirectory()
            && new File(archives, "INI.big").length() > 0
            && new File(archives, "INIZH.big").length() > 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDiagnostics.init(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (savedInstanceState != null) {
            String savedTree = savedInstanceState.getString(STATE_GENERALS_TREE);
            if (savedTree != null) generalsTreeUri = Uri.parse(savedTree);
            savedTree = savedInstanceState.getString(STATE_ZERO_HOUR_TREE);
            if (savedTree != null) zeroHourTreeUri = Uri.parse(savedTree);
        }

        File ext = externalGameData();
        File internal = internalGameData();
        int disabled = quarantineAddons(ext) + quarantineAddons(internal);
        existingGameDataReady = looksLikeGameData(ext) || looksLikeGameData(internal);
        showSetupScreen();
        AppDiagnostics.write("DATA", "external=" + ext + " internal=" + internal
            + " ready=" + existingGameDataReady + " disabledAddons=" + disabled, null);
        if (disabled > 0) importStatus.setText("Дополнения сохранены отдельно: " + disabled
            + ". Теперь запускаются обычные Generals Zero Hour.");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (generalsTreeUri != null) state.putString(STATE_GENERALS_TREE, generalsTreeUri.toString());
        if (zeroHourTreeUri != null) state.putString(STATE_ZERO_HOUR_TREE, zeroHourTreeUri.toString());
        super.onSaveInstanceState(state);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBackground(int color, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(20));
        if (strokeColor != Color.TRANSPARENT) background.setStroke(dp(1), strokeColor);
        return background;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void showSetupScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(28), dp(22), dp(24));
        scroll.addView(page);

        TextView eyebrow = label("ANDROID • УСТАНОВКА ИГРЫ", 12, COLOR_ACCENT, true);
        page.addView(eyebrow);
        TextView title = label("Generals\nZero Hour", 34, COLOR_TEXT, true);
        title.setLineSpacing(0, 0.94f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        page.addView(title, titleParams);
        TextView intro = label("Укажи папки обеих игр. Приложение объединит их данные перед запуском.", 16, COLOR_MUTED, false);
        intro.setLineSpacing(dp(3), 1.04f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(10);
        introParams.bottomMargin = dp(22);
        page.addView(intro, introParams);

        generalsStatus = addGameCard(page, "01", "Command & Conquer: Generals",
            "Базовая игра • выбери папку с архивами .big или папкой Data", true);
        zeroHourStatus = addGameCard(page, "02", "Command & Conquer: Generals Zero Hour",
            "Zero Hour • выбери папку с архивами .big или папкой Data", false);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        info.setBackground(roundedBackground(Color.rgb(20, 29, 45), Color.TRANSPARENT));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(4);
        page.addView(info, infoParams);
        info.addView(label("КАКИЕ ПАПКИ ВЫБРАТЬ", 12, COLOR_ACCENT, true));
        TextView instructions = label(
            "Сначала выбери Generals, затем Zero Hour. Архивы .big могут лежать прямо в папке игры или внутри Data. Импортируются данные обычной игры; Contra и другие дополнения автоматически исключаются.",
            14, COLOR_MUTED, false);
        instructions.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams instructionsParams = new LinearLayout.LayoutParams(-1, -2);
        instructionsParams.topMargin = dp(7);
        info.addView(instructions, instructionsParams);

        importStatus = label("", 14, Color.rgb(255, 140, 120), false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(14);
        page.addView(importStatus, statusParams);

        importButton = new Button(this);
        importButton.setAllCaps(false);
        importButton.setTextSize(16);
        importButton.setTextColor(Color.rgb(31, 25, 16));
        importButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        importButton.setPadding(dp(16), dp(7), dp(16), dp(7));
        importButton.setBackground(roundedBackground(COLOR_ACCENT, Color.TRANSPARENT));
        importButton.setBackgroundTintList(null);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(56));
        actionParams.topMargin = dp(10);
        page.addView(importButton, actionParams);
        Button logs = new Button(this);
        logs.setAllCaps(false);
        logs.setText("Показать журнал ошибок");
        logs.setOnClickListener(v -> {
            TextView text = label(AppDiagnostics.report(), 12, COLOR_TEXT, false);
            text.setTextIsSelectable(true);
            text.setPadding(dp(12), dp(12), dp(12), dp(12));
            ScrollView logScroll = new ScrollView(this);
            logScroll.addView(text);
            new AlertDialog.Builder(this).setTitle("Журнал запуска")
                .setView(logScroll).setPositiveButton("Закрыть", null)
                .setNeutralButton("Сохранить файл", (dialog, which) -> {
                    Intent export = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    export.addCategory(Intent.CATEGORY_OPENABLE);
                    export.setType("text/plain");
                    export.putExtra(Intent.EXTRA_TITLE, "Generals-error-log.txt");
                    startActivityForResult(export, REQUEST_EXPORT_LOG);
                }).show();
        });
        page.addView(logs, new LinearLayout.LayoutParams(-1, -2));
        importButton.setOnClickListener(v -> {
            importStatus.setText("");
            if (generalsTreeUri != null && zeroHourTreeUri != null) {
                importBothInstallations(generalsTreeUri, zeroHourTreeUri);
            } else if (existingGameDataReady && generalsTreeUri == null && zeroHourTreeUri == null) {
                launchGame();
            } else {
                importStatus.setText("Выбери обе папки игры выше, затем продолжи.");
            }
        });
        refreshSetupScreen();
        setContentView(scroll);
    }

    private TextView addGameCard(LinearLayout page, String number, String titleText,
                                 String subtitle, boolean baseGame) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setBackground(roundedBackground(COLOR_CARD, Color.rgb(43, 57, 79)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        page.addView(card, cardParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading);
        TextView badge = label(number, 13, COLOR_ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedBackground(Color.rgb(59, 48, 34), Color.TRANSPARENT));
        heading.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(36)));
        TextView name = label(titleText, 17, COLOR_TEXT, true);
        name.setLineSpacing(0, 1.0f);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.leftMargin = dp(12);
        heading.addView(name, nameParams);

        TextView hint = label(subtitle, 13, COLOR_MUTED, false);
        hint.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        card.addView(hint, hintParams);

        TextView status = label("Папка ещё не выбрана", 13, COLOR_MUTED, false);
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(-1, -2);
        statusLayout.topMargin = dp(12);
        card.addView(status, statusLayout);

        Button choose = new Button(this);
        choose.setAllCaps(false);
        choose.setTextSize(14);
        choose.setTextColor(COLOR_TEXT);
        choose.setText("Выбрать папку");
        choose.setBackground(roundedBackground(Color.rgb(39, 52, 73), Color.rgb(63, 81, 107)));
        choose.setBackgroundTintList(null);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(48));
        chooseParams.topMargin = dp(8);
        card.addView(choose, chooseParams);
        choose.setOnClickListener(v -> {
            importStatus.setText("");
            if (baseGame) pickFolder(REQUEST_IMPORT_GENERALS, "Выбери папку Command & Conquer: Generals");
            else pickFolder(REQUEST_IMPORT_ZERO_HOUR, "Выбери папку Command & Conquer: Generals Zero Hour");
        });
        return status;
    }

    private void refreshSetupScreen() {
        if (generalsStatus == null || zeroHourStatus == null || importButton == null) return;
        generalsStatus.setText(generalsTreeUri != null ? "✓ Папка Generals выбрана" :
            existingGameDataReady ? "✓ Игровые архивы Generals найдены в приложении" : "Папка ещё не выбрана");
        generalsStatus.setTextColor(generalsTreeUri != null || existingGameDataReady ? Color.rgb(118, 221, 158) : COLOR_MUTED);
        zeroHourStatus.setText(zeroHourTreeUri != null ? "✓ Папка Zero Hour выбрана" :
            existingGameDataReady ? "✓ Архивы Zero Hour найдены в приложении" : "Папка ещё не выбрана");
        zeroHourStatus.setTextColor(zeroHourTreeUri != null || existingGameDataReady ? Color.rgb(118, 221, 158) : COLOR_MUTED);
        if (generalsTreeUri != null && zeroHourTreeUri != null) {
            importButton.setText("Объединить данные и запустить");
            importButton.setEnabled(true);
            importButton.setAlpha(1f);
        } else if (existingGameDataReady && generalsTreeUri == null && zeroHourTreeUri == null) {
            importButton.setText("Запустить игру");
            importButton.setEnabled(true);
            importButton.setAlpha(1f);
        } else {
            importButton.setText("Сначала выбери обе папки");
            importButton.setEnabled(false);
            importButton.setAlpha(0.58f);
        }
    }

    private void pickFolder(int requestCode, String title) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(Intent.EXTRA_TITLE, title);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_LOG) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IOException("Cannot open log destination");
                    out.write(AppDiagnostics.report().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    importStatus.setText("Журнал сохранён в выбранный файл.");
                } catch (IOException error) {
                    AppDiagnostics.write("EXPORT", "Cannot export log", error);
                    importStatus.setText("Не удалось сохранить журнал: " + error.getMessage());
                }
            }
            return;
        }
        if (requestCode != REQUEST_IMPORT_GENERALS && requestCode != REQUEST_IMPORT_ZERO_HOUR) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            importStatus.setText("Папка не выбрана. Нажми «Выбрать папку» и попробуй ещё раз.");
            return;
        }

        Uri selectedTree = data.getData();
        AppDiagnostics.write("PICKER", "request=" + requestCode + " URI=" + selectedTree, null);
        keepReadPermission(selectedTree, data.getFlags());
        if (requestCode == REQUEST_IMPORT_GENERALS) {
            generalsTreeUri = selectedTree;
        } else {
            zeroHourTreeUri = selectedTree;
        }
        refreshSetupScreen();
        importStatus.setText(generalsTreeUri != null && zeroHourTreeUri != null
            ? "Обе папки выбраны. Нажми кнопку ниже, чтобы объединить игровые данные."
            : "Папка выбрана. Теперь выбери вторую игру и продолжи.");
        importStatus.setTextColor(Color.rgb(118, 221, 158));
    }

    private void keepReadPermission(Uri treeUri, int flags) {
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (SecurityException ignored) {
            // The picker can still be read for this import while the URI grant is active.
        }
    }

    private void importBothInstallations(Uri baseGame, Uri zeroHourGame) {
        File destRoot = internalGameData();
        if (!destRoot.isDirectory() && !destRoot.mkdirs()) {
            showError("Could not create game data folder: " + destRoot);
            return;
        }

        AlertDialog progress = new AlertDialog.Builder(this)
            .setTitle("Importing game data")
            .setMessage("Preparing Command & Conquer: Generals…")
            .setCancelable(false)
            .show();

        Thread worker = new Thread(() -> {
            String error = null;
            try {
                copyInstallation(baseGame, destRoot, "Command & Conquer: Generals", progress);
                copyInstallation(zeroHourGame, destRoot, "Command & Conquer: Generals Zero Hour", progress);
                if (!looksLikeGameData(destRoot)) {
                    throw new IOException("Both installs must include Data/INI.big and " +
                        "Data/INIZH.big. Check that you selected the Generals folder first " +
                        "and the Zero Hour folder second.");
                }
            } catch (Exception e) {
                AppDiagnostics.write("IMPORT FAILED", "base=" + baseGame + " ZH=" + zeroHourGame, e);
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String failure = error;
            runOnUiThread(() -> {
                progress.dismiss();
                existingGameDataReady = failure == null;
                if (failure != null) {
                    importStatus.setText(failure);
                    importStatus.setTextColor(Color.rgb(255, 140, 120));
                    refreshSetupScreen();
                } else {
                    launchGame();
                }
            });
        }, "gx-import");
        worker.start();
    }

    private Uri findDataFolder(Uri treeUri) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        String[] projection = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        boolean hasBigArchive = false;
        Uri dataFolder = null;
        try (Cursor c = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (c == null) throw new IOException("Cannot read the selected folder");
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                if (name == null) continue;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) && name.equalsIgnoreCase("Data")) {
                    dataFolder = child;
                } else if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)
                    && name.toLowerCase(Locale.ROOT).endsWith(".big")) {
                    hasBigArchive = true;
                }
            }
        }

        if (hasBigArchive) return treeUri; // Retail installations keep archives at the root.
        if (dataFolder != null) return dataFolder;
        throw new IOException("The selected folder has no Data folder or .big archives. " +
            "Select the game installation folder that contains Data/.");
    }

    private void copyInstallation(Uri installTree, File destRoot, String gameName,
                                  AlertDialog progress) throws IOException {
        Uri dataFolder = findDataFolder(installTree);
        // Copy a complete install root to preserve its normal layout. If the
        // picker points directly at Data/, put those archives under GameData/Data.
        File destination = dataFolder.equals(installTree)
            ? new File(destRoot, "Data") : destRoot;
        runOnUiThread(() -> progress.setMessage("Copying " + gameName + "…"));
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Cannot create folder " + destination);
        }
        copyTreeRecursive(dataFolder.equals(installTree) ? dataFolder : installTree,
            destination, progress);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Game data error")
            .setMessage(message)
            .setPositiveButton("Try again", (d, w) -> recreate())
            .setNegativeButton("Launch anyway", (d, w) -> launchGame())
            .setCancelable(false)
            .show();
    }

    private void launchGame() {
        AppDiagnostics.write("LAUNCH", "Starting GameActivity", null);
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    /** Copy a SAF tree; large .big archives are streamed in 1 MB chunks. */
    private void copyTreeRecursive(Uri treeUri, File destDir, AlertDialog progress) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.isTreeUri(treeUri)
                ? DocumentsContract.getTreeDocumentId(treeUri)
                : DocumentsContract.getDocumentId(treeUri));
        Queue<DirEntry> pending = new ArrayDeque<>();
        copyChildren(childrenUri, destDir, progress, pending);

        while (!pending.isEmpty()) {
            DirEntry next = pending.poll();
            if (!next.dest.isDirectory() && !next.dest.mkdirs()) {
                throw new IOException("Cannot create folder " + next.dest);
            }
            copyChildren(DocumentsContract.buildChildDocumentsUriUsingTree(
                next.uri, DocumentsContract.getDocumentId(next.uri)), next.dest, progress, pending);
        }
    }

    private void copyChildren(Uri childrenUri, File destDir, AlertDialog progress,
                              Queue<DirEntry> pending) throws IOException {
        String[] projection = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor c = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (c == null) throw new IOException("Cannot list the selected game folder");
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? -1 : c.getLong(3);
                if (name == null) continue;
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, docId);
                File dest = new File(destDir, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    // A root with flat BIG archives can also contain loose Data/ files.
                    if (destDir.getName().equals("Data") && name.equalsIgnoreCase("Data")) dest = destDir;
                    pending.add(new DirEntry(docUri, dest));
                } else {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".big") && !isRetailArchive(name)) {
                        AppDiagnostics.write("SKIP ADDON", name, null);
                        continue;
                    }
                    copyFile(docUri, dest, size, progress);
                }
            }
        }
    }

    private void copyFile(Uri docUri, File dest, long size, AlertDialog progress) throws IOException {
        AppDiagnostics.write("COPY", "URI=" + docUri + " destination=" + dest + " bytes=" + size, null);
        byte[] buffer = new byte[1024 * 1024];
        long copied = 0;
        try (InputStream in = getContentResolver().openInputStream(docUri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("Cannot open " + dest.getName());
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                copied += n;
            }
        } catch (IOException e) {
            dest.delete(); // Do not leave a truncated archive that appears complete.
            throw e;
        }
        if (size > 0) {
            if (copied != size) {
                if (!dest.delete()) AppDiagnostics.write("IMPORT", "Cannot remove incomplete file " + dest, null);
                throw new IOException("Incomplete archive: " + dest.getName() + " (" + copied + "/" + size + ")");
            }
            final String name = dest.getName();
            final long done = copied;
            runOnUiThread(() -> progress.setMessage(
                name + "\n" + (done / (1024 * 1024)) + " MB copied"));
        }
    }

    // GeneralsX @bugfix Codex 04/10/2026 Keep incompatible addons out of vanilla imports.
    private static boolean isRetailArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.matches("(ini|maps|music|patch|shaders|terrain|textures|window|gensec)(zh)?\\.big")
            || lower.matches("(audio|speech|w3d)(english|russian|french|german|italian|spanish|polish|brazilian|korean|chinese|japanese)?(zh)?\\.big")
            || lower.matches("(english|russian|french|german|italian|spanish|polish|brazilian|korean|chinese|japanese)(zh)?\\.big");
    }

    private int quarantineAddons(File root) {
        if (!root.isDirectory()) return 0;
        File backup = new File(root.getParentFile(), "DisabledImportMods");
        return quarantineAddonsRecursive(root, root, backup);
    }

    private int quarantineAddonsRecursive(File root, File directory, File backup) {
        File[] files = directory.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                count += quarantineAddonsRecursive(root, file, backup);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".big") && !isRetailArchive(file.getName())) {
                String relative = file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                File target = new File(backup, relative);
                if (target.exists()) target = new File(target.getParentFile(), System.currentTimeMillis() + "-" + target.getName());
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
                    AppDiagnostics.write("ADDON BACKUP FAILED", "Cannot create " + target.getParent(), null);
                } else if (file.renameTo(target)) {
                    ++count;
                    AppDiagnostics.write("ADDON BACKUP", file + " -> " + target, null);
                } else {
                    AppDiagnostics.write("ADDON BACKUP FAILED", file.toString(), null);
                }
            }
        }
        return count;
    }
}

