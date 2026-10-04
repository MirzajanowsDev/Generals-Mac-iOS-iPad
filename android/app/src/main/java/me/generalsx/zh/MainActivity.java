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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (savedInstanceState != null) {
            String savedTree = savedInstanceState.getString(STATE_GENERALS_TREE);
            if (savedTree != null) generalsTreeUri = Uri.parse(savedTree);
            savedTree = savedInstanceState.getString(STATE_ZERO_HOUR_TREE);
            if (savedTree != null) zeroHourTreeUri = Uri.parse(savedTree);
        }

        File ext = externalGameData();
        File internal = internalGameData();
        existingGameDataReady = looksLikeGameData(ext) || looksLikeGameData(internal);
        showSetupScreen();
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

        TextView eyebrow = label("ANDROID вЂў РЈРЎРўРђРќРћР’РљРђ РР“Р Р«", 12, COLOR_ACCENT, true);
        page.addView(eyebrow);
        TextView title = label("Generals\nZero Hour", 34, COLOR_TEXT, true);
        title.setLineSpacing(0, 0.94f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        page.addView(title, titleParams);
        TextView intro = label("РЈРєР°Р¶Рё РїР°РїРєРё РѕР±РµРёС… РёРіСЂ. РџСЂРёР»РѕР¶РµРЅРёРµ РѕР±СЉРµРґРёРЅРёС‚ РёС… РґР°РЅРЅС‹Рµ РїРµСЂРµРґ Р·Р°РїСѓСЃРєРѕРј.", 16, COLOR_MUTED, false);
        intro.setLineSpacing(dp(3), 1.04f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(10);
        introParams.bottomMargin = dp(22);
        page.addView(intro, introParams);

        generalsStatus = addGameCard(page, "01", "Command & Conquer: Generals",
            "Р‘Р°Р·РѕРІР°СЏ РёРіСЂР° вЂў РІС‹Р±РµСЂРё РїР°РїРєСѓ, РІРЅСѓС‚СЂРё РєРѕС‚РѕСЂРѕР№ РЅР°С…РѕРґРёС‚СЃСЏ Data", true);
        zeroHourStatus = addGameCard(page, "02", "Command & Conquer: Generals Zero Hour",
            "Zero Hour вЂў РІС‹Р±РµСЂРё РїР°РїРєСѓ, РІРЅСѓС‚СЂРё РєРѕС‚РѕСЂРѕР№ РЅР°С…РѕРґРёС‚СЃСЏ Data", false);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        info.setBackground(roundedBackground(Color.rgb(20, 29, 45), Color.TRANSPARENT));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(4);
        page.addView(info, infoParams);
        info.addView(label("РљРђРљРР• РџРђРџРљР Р’Р«Р‘Р РђРўР¬", 12, COLOR_ACCENT, true));
        TextView instructions = label(
            "Р’С‹Р±РµСЂРё РёРјРµРЅРЅРѕ РїР°РїРєСѓ РёРіСЂС‹, РіРґРµ РІРёРґРЅР° РїР°РїРєР° Data. РЎРЅР°С‡Р°Р»Р° Generals, Р·Р°С‚РµРј Zero Hour. РђСЂС…РёРІС‹ .big СЃРєРѕРїРёСЂСѓСЋС‚СЃСЏ Рё РѕР±СЉРµРґРёРЅСЏС‚СЃСЏ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё.",
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
        importButton.setOnClickListener(v -> {
            importStatus.setText("");
            if (generalsTreeUri != null && zeroHourTreeUri != null) {
                importBothInstallations(generalsTreeUri, zeroHourTreeUri);
            } else if (existingGameDataReady && generalsTreeUri == null && zeroHourTreeUri == null) {
                launchGame();
            } else {
                importStatus.setText("Р’С‹Р±РµСЂРё РѕР±Рµ РїР°РїРєРё РёРіСЂС‹ РІС‹С€Рµ, Р·Р°С‚РµРј РїСЂРѕРґРѕР»Р¶Рё.");
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

        TextView status = label("РџР°РїРєР° РµС‰С‘ РЅРµ РІС‹Р±СЂР°РЅР°", 13, COLOR_MUTED, false);
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(-1, -2);
        statusLayout.topMargin = dp(12);
        card.addView(status, statusLayout);

        Button choose = new Button(this);
        choose.setAllCaps(false);
        choose.setTextSize(14);
        choose.setTextColor(COLOR_TEXT);
        choose.setText("Р’С‹Р±СЂР°С‚СЊ РїР°РїРєСѓ");
        choose.setBackground(roundedBackground(Color.rgb(39, 52, 73), Color.rgb(63, 81, 107)));
        choose.setBackgroundTintList(null);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(48));
        chooseParams.topMargin = dp(8);
        card.addView(choose, chooseParams);
        choose.setOnClickListener(v -> {
            importStatus.setText("");
            if (baseGame) pickFolder(REQUEST_IMPORT_GENERALS, "Р’С‹Р±РµСЂРё РїР°РїРєСѓ Command & Conquer: Generals");
            else pickFolder(REQUEST_IMPORT_ZERO_HOUR, "Р’С‹Р±РµСЂРё РїР°РїРєСѓ Command & Conquer: Generals Zero Hour");
        });
        return status;
    }

    private void refreshSetupScreen() {
        if (generalsStatus == null || zeroHourStatus == null || importButton == null) return;
        generalsStatus.setText(generalsTreeUri != null ? "вњ“ РџР°РїРєР° Generals РІС‹Р±СЂР°РЅР°" :
            existingGameDataReady ? "вњ“ РРіСЂРѕРІС‹Рµ Р°СЂС…РёРІС‹ Generals РЅР°Р№РґРµРЅС‹ РІ РїСЂРёР»РѕР¶РµРЅРёРё" : "РџР°РїРєР° РµС‰С‘ РЅРµ РІС‹Р±СЂР°РЅР°");
        generalsStatus.setTextColor(generalsTreeUri != null || existingGameDataReady ? Color.rgb(118, 221, 158) : COLOR_MUTED);
        zeroHourStatus.setText(zeroHourTreeUri != null ? "вњ“ РџР°РїРєР° Zero Hour РІС‹Р±СЂР°РЅР°" :
            existingGameDataReady ? "вњ“ РђСЂС…РёРІС‹ Zero Hour РЅР°Р№РґРµРЅС‹ РІ РїСЂРёР»РѕР¶РµРЅРёРё" : "РџР°РїРєР° РµС‰С‘ РЅРµ РІС‹Р±СЂР°РЅР°");
        zeroHourStatus.setTextColor(zeroHourTreeUri != null || existingGameDataReady ? Color.rgb(118, 221, 158) : COLOR_MUTED);
        if (generalsTreeUri != null && zeroHourTreeUri != null) {
            importButton.setText("РћР±СЉРµРґРёРЅРёС‚СЊ РґР°РЅРЅС‹Рµ Рё Р·Р°РїСѓСЃС‚РёС‚СЊ");
            importButton.setEnabled(true);
            importButton.setAlpha(1f);
        } else if (existingGameDataReady && generalsTreeUri == null && zeroHourTreeUri == null) {
            importButton.setText("Р—Р°РїСѓСЃС‚РёС‚СЊ РёРіСЂСѓ");
            importButton.setEnabled(true);
            importButton.setAlpha(1f);
        } else {
            importButton.setText("РЎРЅР°С‡Р°Р»Р° РІС‹Р±РµСЂРё РѕР±Рµ РїР°РїРєРё");
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
        if (requestCode != REQUEST_IMPORT_GENERALS && requestCode != REQUEST_IMPORT_ZERO_HOUR) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            importStatus.setText("РџР°РїРєР° РЅРµ РІС‹Р±СЂР°РЅР°. РќР°Р¶РјРё В«Р’С‹Р±СЂР°С‚СЊ РїР°РїРєСѓВ» Рё РїРѕРїСЂРѕР±СѓР№ РµС‰С‘ СЂР°Р·.");
            return;
        }

        Uri selectedTree = data.getData();
        keepReadPermission(selectedTree, data.getFlags());
        if (requestCode == REQUEST_IMPORT_GENERALS) {
            generalsTreeUri = selectedTree;
        } else {
            zeroHourTreeUri = selectedTree;
        }
        refreshSetupScreen();
        importStatus.setText(generalsTreeUri != null && zeroHourTreeUri != null
            ? "РћР±Рµ РїР°РїРєРё РІС‹Р±СЂР°РЅС‹. РќР°Р¶РјРё РєРЅРѕРїРєСѓ РЅРёР¶Рµ, С‡С‚РѕР±С‹ РѕР±СЉРµРґРёРЅРёС‚СЊ РёРіСЂРѕРІС‹Рµ РґР°РЅРЅС‹Рµ."
            : "РџР°РїРєР° РІС‹Р±СЂР°РЅР°. РўРµРїРµСЂСЊ РІС‹Р±РµСЂРё РІС‚РѕСЂСѓСЋ РёРіСЂСѓ Рё РїСЂРѕРґРѕР»Р¶Рё.");
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
            .setMessage("Preparing Command & Conquer: GeneralsвЂ¦")
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

        if (dataFolder != null) return dataFolder;
        if (hasBigArchive) return treeUri; // The user selected Data/ itself.
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
        runOnUiThread(() -> progress.setMessage("Copying " + gameName + "вЂ¦"));
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
                    pending.add(new DirEntry(docUri, dest));
                } else {
                    copyFile(docUri, dest, size, progress);
                }
            }
        }
    }

    private void copyFile(Uri docUri, File dest, long size, AlertDialog progress) throws IOException {
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
            final String name = dest.getName();
            final long done = copied;
            runOnUiThread(() -> progress.setMessage(
                name + "\n" + (done / (1024 * 1024)) + " MB copied"));
        }
    }
}

