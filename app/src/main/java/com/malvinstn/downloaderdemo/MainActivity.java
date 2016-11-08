package com.malvinstn.downloaderdemo;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    private static final String KEY_LATEST_DOWNLOAD_ID = TAG + ".latest_download_id";
    private static final int REQUEST_PERMISSION = 2211;
    private static final int MAX_PROGRESS = 100;
    private long mLatestDownloadId = 0L;
    private IntentFilter mDownloadIntentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    private BroadcastReceiver mDownloadCompletedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Download has completed
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (id != mLatestDownloadId || id == 0L) {
                Log.w(TAG, "Ignoring unrelated download " + id);
                return;
            }
            Cursor cursor = getCursor(id);
            if (cursor != null && isDownloadCompleted(cursor)) {
                Uri uriForDownloadedFile = getDownloadedUri(cursor);
                if (uriForDownloadedFile != null) {
                    updatePercentage(MAX_PROGRESS);
                    showSnackbar(mFloatingActionButton, "Download completed.");
                    openDownloadedFile(uriForDownloadedFile);
                } else {
                    //Failed download.
                    updatePercentage(-1);
                }
                cursor.close();
            }
        }
    };
    private Thread mProgressThread;
    private FloatingActionButton mFloatingActionButton;
    private EditText mUrlEditText;
    private ProgressBar mProgressBar;
    private boolean mPermissionGranted;
    private DownloadManager mDownloadManager;
    private TextView mProgressText;
    private Button mOpenButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mUrlEditText = (EditText) findViewById(R.id.fieldUrl);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mProgressBar.setMax(MAX_PROGRESS);
        mProgressText = (TextView) findViewById(R.id.textProgress);
        mOpenButton = (Button) findViewById(R.id.buttonOpen);
        mOpenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLatestDownloadId > 0) {
                    Cursor cursor = getCursor(mLatestDownloadId);
                    if (cursor != null) {
                        Uri downloadedUri = getDownloadedUri(cursor);
                        cursor.close();
                        if (downloadedUri != null) {
                            openDownloadedFile(downloadedUri);
                            return;
                        }
                    }
                }
                showSnackbar(mFloatingActionButton, getString(R.string.unable_to_open_file));
            }
        });
        mFloatingActionButton = (FloatingActionButton) findViewById(R.id.fab);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = mUrlEditText.getText().toString();
                if (TextUtils.isEmpty(url)) {
                    showSnackbar(view, getString(R.string.empty_url));
                } else if (mLatestDownloadId > 0) {
                    boolean completed = false;
                    Cursor cursor = getCursor(mLatestDownloadId);
                    if (cursor != null) {
                        if (isDownloadCompleted(cursor)) {
                            completed = true;
                        }
                        cursor.close();
                    }
                    if (completed) {
                        startDownload(url);
                    } else {
                        showSnackbar(view, getString(R.string.download_in_progress));
                    }
                } else if (!mPermissionGranted) {
                    showSnackbar(view, getString(R.string.permission_denied));
                } else if (Patterns.WEB_URL.matcher(mUrlEditText.getText()).matches()) {
                    // TODO check Mime-Type of the file on the server.
                    startDownload(url);
                } else {
                    showSnackbar(view, getString(R.string.invalid_url));
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSION);
        } else {
            mPermissionGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length >= 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mPermissionGranted = true;
                }
            }
        }
    }

    private void showSnackbar(View view, String message) {
        final Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.dismiss, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
            }
        }).show();
    }

    private void updateProgress(int downloaded, int total) {
        int progress;
        if (total <= 0) {
            progress = 0;
        } else {
            progress = (downloaded * 200 + total) / (total * 2);
        }
        Log.d(TAG, "Progress: " + downloaded + "/" + total + " (" + progress + "%)");
        updatePercentage(progress);
    }

    private void updatePercentage(int value) {
        mOpenButton.setVisibility(View.GONE);
        if (value < 0) {
            mProgressText.setText(R.string.failed);
            value = 0;
        } else if (value == MAX_PROGRESS) {
            mProgressText.setText(R.string.completed);

            //Show the Open Button.
            mOpenButton.setVisibility(View.VISIBLE);
        } else {
            mProgressText.setText(getString(R.string.percentage_format, value));
        }
        mProgressBar.setProgress(value);
    }

    private boolean isDownloadCompleted(@NonNull Cursor cursor) {
        boolean completed = false;
        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        Log.d(TAG, "Status " + status);
        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
            // Download has completed.
            completed = true;
        } else {
            // Not yet completed
            Log.d(TAG, "Download not yet completed.");
        }
        return completed;
    }

    private void openDownloadedFile(Uri uri) {
        Log.d(TAG, "Downloaded " + uri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager packageManager = getPackageManager();
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent);
        } else {
            Log.e(TAG, "No Intent available to handle install action");
            showSnackbar(mFloatingActionButton, getString(R.string.unable_to_open_file));
        }
    }

    private Cursor getCursor(long id) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        DownloadManager downloadManager = getDownloadManager();
        Cursor cursor = downloadManager.query(query);

        // It shouldn't be empty, but just in case.
        if (!cursor.moveToFirst()) {
            Log.e(TAG, "Empty row");
            return null;
        }
        return cursor;
    }

    private DownloadManager getDownloadManager() {
        if (mDownloadManager == null) {
            mDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        }
        return mDownloadManager;
    }

    private Uri getDownloadedUri(Cursor cursor) {
        int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        String downloadedPackageUriString = cursor.getString(uriIndex);
        return Uri.parse(downloadedPackageUriString);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mLatestDownloadId = getPreferences().getLong(KEY_LATEST_DOWNLOAD_ID, 0L);
        Log.d(TAG, "onStart: DownloadId " + mLatestDownloadId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Query download progress. In case the download has completed.
        if (mLatestDownloadId > 0) {
            Cursor cursor = getCursor(mLatestDownloadId);
            if (cursor != null) {
                if (!isDownloadCompleted(cursor)) {
                    mProgressThread = new Thread(new DownloadProgressTrackerRunnable());
                    mProgressThread.start();
                } else {
                    // Get the downloaded URI and check if it exist.
                    Uri uriForDownloadedFile = getDownloadedUri(cursor);
                    if (uriForDownloadedFile != null) {
                        // Successful download.
                        updatePercentage(MAX_PROGRESS);
                    } else {
                        // Failed download.
                        updatePercentage(-1);
                    }
                }
                cursor.close();
            }
        }
        registerReceiver(mDownloadCompletedBroadcastReceiver, mDownloadIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mDownloadCompletedBroadcastReceiver);
        if (mProgressThread != null) {
            mProgressThread.interrupt();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: DownloadId " + mLatestDownloadId);
        getPreferences().edit().putLong(KEY_LATEST_DOWNLOAD_ID, mLatestDownloadId).apply();
    }

    private SharedPreferences getPreferences() {
        return getPreferences(MODE_PRIVATE);
    }

    private void startDownload(String url) {
        // Parameters.
        String title = "Downloading My Application Update...";
        String description = "My Application v2.14.20";
        String apkFileName = "myApkName.apk";

        // Create the request.
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(title);
        request.setDescription(description);
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkFileName);

        // Get download service and enqueue file.
        DownloadManager manager = getDownloadManager();
        mLatestDownloadId = manager.enqueue(request);

        // Update percentage progress
        updatePercentage(0);

        // Create thread to track the progress.
        mProgressThread = new Thread(new DownloadProgressTrackerRunnable());
        mProgressThread.start();
    }

    private class DownloadProgressTrackerRunnable implements Runnable {
        @Override
        public void run() {
            boolean downloading = true;
            do {
                if (mLatestDownloadId > 0) {
                    Cursor cursor = getCursor(mLatestDownloadId);
                    if (cursor != null) {
                        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        Log.d(TAG, "Download Status " + status);
                        if (isDownloadCompleted(cursor)) {
                            downloading = false;
                        } else if (status == DownloadManager.STATUS_RUNNING) {
                            final int downloaded = cursor.getInt(cursor
                                    .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                            final int total = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateProgress(downloaded, total);
                                }
                            });
                        }
                        cursor.close();
                    }
                } else {
                    downloading = false;
                }
                Log.d(TAG, "Is Downloading " + downloading);
            } while (downloading);
        }
    }
}
