// GeneralsX @feature android-port 08/09/2026 (issue #12)
//
// MainActivity — the launcher. Runs BEFORE the game so users on Android 13+
// can install game data at all: newer Android versions block file managers
// from writing into Android/data/<pkg>/, so the old "adb push / USB copy into
// the external files dir" workflow is no longer universally possible.
//
// Flow:
//   1. If a GameData directory is already visible to the engine (external
//      Android/data/<pkg>/files/GameData or internal files/GameData), launch
//      the game immediately.
//   2. Otherwise offer a one-time SAF import: the user picks the folder that
//      contains their Zero Hour install (Data/*.big), and we copy it into the
//      app's INTERNAL files dir (files/GameData) — always readable/writable by
//      the app regardless of scoped-storage rules. SDL3Main.cpp already falls
//      back to that path when the external GameData is absent.
//   3. "Skip" launches the game anyway (useful for adb-power users who stage
//      data after first launch).
//
// No storage permissions are needed: ACTION_OPEN_DOCUMENT_TREE grants access
// to the user-chosen tree, and getFilesDir() is the app's own private storage.

package me.generalsx.zh;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;

public class MainActivity extends Activity {

    private static final int REQUEST_IMPORT_TREE = 1001;

    /** A SAF directory queued for recursion: its tree URI + filesystem target. */
    private static final class DirEntry {
        final Uri uri;
        final File dest;
        DirEntry(Uri uri, File dest) { this.uri = uri; this.dest = dest; }
    }

    // Mirror of SDL3Main.cpp's search order: external GameData first (adb
    // push-able), then internal (this activity's import target).
    private File externalGameData() {
        return new File(getExternalFilesDir(null), "GameData");
    }

    private File internalGameData() {
        return new File(getFilesDir(), "GameData");
    }

    private static boolean looksLikeGameData(File dir) {
        // The engine only needs Data/ with the .big archives; fonts/ are
        // extracted by the native side on first run.
        return new File(dir, "Data").isDirectory();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File ext = externalGameData();
        File internal = internalGameData();
        if (looksLikeGameData(ext) || looksLikeGameData(internal)) {
            launchGame();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Generals Zero Hour")
            .setMessage(
                "No game data found.\n\n" +
                "You need the Zero Hour .big files from your own copy of the game " +
                "(e.g. a Steam or EA App install).\n\n" +
                "Tap Import and select the folder that contains the 'Data' folder " +
                "(and the .big files). They will be copied into the app's private " +
                "storage — this works on all Android versions.")
            .setPositiveButton("Import", (d, w) -> pickFolder())
            .setNegativeButton("Launch anyway", (d, w) -> launchGame())
            .setCancelable(false)
            .show();
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(intent, REQUEST_IMPORT_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_TREE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importTree(data.getData());
            } else {
                // User cancelled the picker: back to the choice dialog.
                recreate();
            }
        }
    }

    // ---- import -----------------------------------------------------------

    private void importTree(Uri treeUri) {
        // Take a persistable grant so a re-import later needs no extra setup.
        try {
            getContentResolver().takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Persistable grant not offered by the provider; the URI stays
            // valid for this process lifetime, which is all we need.
        }

        File destRoot = internalGameData();
        if (!destRoot.isDirectory() && !destRoot.mkdirs()) {
            showError("Could not create " + destRoot);
            return;
        }

        AlertDialog progress = new AlertDialog.Builder(this)
            .setTitle("Importing game data")
            .setMessage("Preparing…")
            .setCancelable(false)
            .show();

        Thread worker = new Thread(() -> {
            String error = null;
            try {
                copyTreeRecursive(treeUri, destRoot, progress);
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String failure = error;
            runOnUiThread(() -> {
                progress.dismiss();
                if (failure != null) {
                    showError("Import failed: " + failure);
                } else {
                    launchGame();
                }
            });
        }, "gx-import");
        worker.start();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Import failed")
            .setMessage(message)
            .setPositiveButton("Retry", (d, w) -> recreate())
            .setNegativeButton("Launch anyway", (d, w) -> launchGame())
            .setCancelable(false)
            .show();
    }

    private void launchGame() {
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    /**
     * Recursively copy the SAF document tree rooted at {@code treeUri} into
     * {@code destDir}. Large .big archives (0.5–2 GB) are streamed in 1 MB
     * chunks; progress is written back to the dialog's message.
     */
    private void copyTreeRecursive(Uri treeUri, File destDir, AlertDialog progress) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getDocumentId(treeUri));
        Queue<DirEntry> pending = new ArrayDeque<>();
        copyChildren(childrenUri, destDir, progress, pending);

        while (!pending.isEmpty()) {
            DirEntry next = pending.poll();
            if (!next.dest.isDirectory() && !next.dest.mkdirs()) {
                throw new IOException("Cannot create folder " + next.dest);
            }
            copyChildren(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    next.uri, DocumentsContract.getDocumentId(next.uri)),
                next.dest, progress, pending);
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
            if (c == null) {
                throw new IOException("Cannot list " + childrenUri);
            }
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? -1 : c.getLong(3);
                if (name == null) {
                    continue;
                }
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
            if (in == null) {
                throw new IOException("Cannot open " + dest.getName());
            }
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                copied += n;
            }
        } catch (IOException e) {
            dest.delete();  // never leave a truncated .big behind
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
