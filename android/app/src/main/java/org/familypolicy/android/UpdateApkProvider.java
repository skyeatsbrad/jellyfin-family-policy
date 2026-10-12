package org.familypolicy.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateApkProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/apk".equals(uri.getPath()) || mode == null || mode.contains("w")) {
            throw new FileNotFoundException("Invalid update request.");
        }
        File apk = AppUpdateManager.updateFile(providerContext());
        if (!apk.isFile()) {
            throw new FileNotFoundException("Verified update APK is unavailable.");
        }
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private android.content.Context providerContext() throws FileNotFoundException {
        android.content.Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Provider context unavailable.");
        }
        return context;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File apk;
        try {
            validateUri(uri);
            apk = AppUpdateManager.updateFile(providerContext());
        } catch (FileNotFoundException ex) {
            return null;
        }
        if (!apk.isFile()) {
            return null;
        }
        String[] columns = projection == null
            ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(column, apk.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(column, apk.length());
            } else {
                row.add(column, null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private static void validateUri(Uri uri) throws FileNotFoundException {
        if (uri == null || !"/apk".equals(uri.getPath())) {
            throw new FileNotFoundException("Invalid update request.");
        }
    }
}
