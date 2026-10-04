// GeneralsX @feature android-port 08/09/2026 (issue #12)
// GeneralsX @bugfix Codex 04/10/2026 Import the base Generals and Zero Hour installs together.
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
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends Activity {

    private static final int REQUEST_IMPORT_GENERALS = 1001;
    private static final int REQUEST_IMPORT_ZERO_HOUR = 1002;
    private static final String STATE_GENERALS_TREE = "generalsTree";

    /** A SAF directory queued for recursion: its tree URI + filesystem target. */
    private static final class DirEntry {
        final Uri uri;
        final File dest;
        DirEntry(Uri uri, File dest) { this.uri = uri; this.dest = dest; }
    }

    private Uri generalsTreeUri;

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
        if (savedInstanceState != null) {
            String savedTree = savedInstanceState.getString(STATE_GENERALS_TREE);
            if (savedTree != null) generalsTreeUri = Uri.parse(savedTree);
        }

        File ext = externalGameData();
        File internal = internalGameData();
        if (looksLikeGameData(ext) || looksLikeGameData(internal)) {
            launchGame();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Generals Zero Hour")
            .setMessage(
                "Import the game data from both installations. First choose the " +
                "Command & Conquer: Generals folder, then choose the Command & " +
                "Conquer: Generals Zero Hour folder. Select each game's main " +
                "folder (the one containing Data), not its Data folder. The " +
                "Zero Hour files will be copied last so they take precedence.")
            .setPositiveButton("Import", (d, w) -> pickFolder(
                REQUEST_IMPORT_GENERALS, "Choose the Command & Conquer: Generals folder"))
            .setNegativeButton("Launch anyway", (d, w) -> launchGame())
            .setCancelable(false)
            .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (generalsTreeUri != null) state.putString(STATE_GENERALS_TREE, generalsTreeUri.toString());
        super.onSaveInstanceState(state);
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
            generalsTreeUri = null;
            recreate();
            return;
        }

        Uri selectedTree = data.getData();
        keepReadPermission(selectedTree, data.getFlags());
        if (requestCode == REQUEST_IMPORT_GENERALS) {
            generalsTreeUri = selectedTree;
            pickFolder(REQUEST_IMPORT_ZERO_HOUR,
                "Now choose the Command & Conquer: Generals Zero Hour folder");
        } else if (generalsTreeUri != null) {
            importBothInstallations(generalsTreeUri, selectedTree);
        } else {
            recreate();
        }
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
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String failure = error;
            runOnUiThread(() -> {
                progress.dismiss();
                generalsTreeUri = null;
                if (failure != null) showError("Game data import failed: " + failure);
                else launchGame();
            });
        }, "gx-import");
        worker.start();
    }

    private Uri findDataFolder(Uri treeUri) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getDocumentId(treeUri));
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
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    /** Copy a SAF tree; large .big archives are streamed in 1 MB chunks. */
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
