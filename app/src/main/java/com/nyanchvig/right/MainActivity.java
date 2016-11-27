package com.nyanchvig.right;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.aspose.words.AsposeWordsApplication;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.SaveFormat;
import com.github.johnpersano.supertoasts.library.Style;
import com.github.johnpersano.supertoasts.library.SuperActivityToast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveFolder.DriveFileResult;
import com.google.android.gms.drive.DriveFolder.DriveFolderResult;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;

import org.jsoup.Jsoup;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.wasabeef.richeditor.RichEditor;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public RichEditor mEditor;
    public String[] commonWordList;
    private GestureLibrary gestureLibrary = null;
    private static final String TAG = "drive-quickstart";
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private GoogleApiClient mGoogleApiClient;

    DriveFolder right_folder;

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    String fileName;
    String fileID;

    boolean refineMode = false;

    private int mInterval = 500; // 5 seconds by default, can be changed later
    private Handler mHandler;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);

        AsposeWordsApplication awapp = new AsposeWordsApplication();
        awapp.loadLibs(getApplicationContext());

        mHandler = new Handler();
        //startRepeatingTask(); /*Not being used as of now*/

        if (mGoogleApiClient == null) {
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the client. Once connected, the camera is launched.
        mGoogleApiClient.connect();

        commonWordList = new String[] {"the","I" , "of", "and", "to", "a", "in", "for", "is", "on", "that", "by", "this", "with", "i", "you", "it", "not", "or", "be", "are",
                "from", "at", "as", "your", "all", "have", "new", "more", "an", "was", "we", "will", "us", "if"};

        mEditor = (RichEditor) findViewById(R.id.editor);

        setEditor();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("sample_text",getString(R.string.demo_text));
        clipboard.setPrimaryClip(clip);

        GestureOverlayView gestureOverlayView = (GestureOverlayView)findViewById(R.id.gestures);
        gestureLibrary = GestureLibraries.fromRawResource(this, R.raw.gesture);
        gestureLibrary.load();
        gestureOverlayView.addOnGesturePerformedListener(gesturePerformedListener);

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        // The failure has a resolution. Resolve it.
        // Called typically when the app is not yet authorized, and an
        // authorization
        // dialog is displayed to the user.
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");
        try {
            right_folder = DriveId.decodeFromString(sharedPreferences.getString(getString(R.string.right_folder), "0")).asDriveFolder();
        }catch (IllegalArgumentException e){
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle("Right").build();
            Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(
                    mGoogleApiClient, changeSet).setResultCallback(callback);
        }
    }

    final ResultCallback<DriveFolderResult> callback = new ResultCallback<DriveFolderResult>() {
        @Override
        public void onResult(DriveFolderResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.i(TAG,"Error while trying to create the folder");
                return;
            }
            right_folder = result.getDriveFolder();
            String rightFolderID = right_folder.getDriveId().toString();
            Log.i(TAG,"Created a folder: " + rightFolderID);
            editor=sharedPreferences.edit();
            editor.putString(getString(R.string.right_folder), rightFolderID);
            editor.commit();
        }
    };


    public void saveFile(final String newFilename){
        final ResultCallback<DriveContentsResult> driveContentsCallback =
                new ResultCallback<DriveContentsResult>() {
                    @Override
                    public void onResult(DriveContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.i(TAG, "Error while trying to create new file contents");
                            return;
                        }
                        if (newFilename.equals(fileName)){
                            DriveFile driveFile = Drive.DriveApi.getFile(mGoogleApiClient,
                                    DriveId.decodeFromString(fileID));
                            driveFile.delete(mGoogleApiClient);
                            SuperActivityToast.create(MainActivity.this, new Style(), Style.TYPE_BUTTON)
                                    .setProgressBarColor(Color.WHITE)
                                    .setText("Document Updated")
                                    .setDuration(Style.DURATION_LONG)
                                    .setFrame(Style.FRAME_LOLLIPOP)
                                    .setColor(Color.BLACK)
                                    .setAnimations(Style.ANIMATIONS_POP).show();
                        }
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle(newFilename)
                                .setMimeType("application/rtf")
                                .setStarred(true).build();
                        right_folder.createFile(mGoogleApiClient, changeSet, result.getDriveContents())
                                .setResultCallback(new ResultCallback<DriveFileResult>() {
                                    @Override
                                    public void onResult(DriveFileResult result) {
                                        if (!result.getStatus().isSuccess()) {
                                            Log.i(TAG, "Error while trying to create the file");
                                            return;
                                        }
                                        DriveFile file = result.getDriveFile();
                                        fileID = file.getDriveId().toString();
                                        fileName = newFilename;
                                        Log.i(TAG, "Created a file: " + file.getDriveId());
                                        file.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(saveCallback);
                                    }
                                });
                    }
                };
        Drive.DriveApi.newDriveContents(mGoogleApiClient)
                .setResultCallback(driveContentsCallback);
    }

    final private ResultCallback<DriveContentsResult> saveCallback =
            new ResultCallback<DriveContentsResult>() {
                @Override
                public void onResult(DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        // Handle error
                        return;
                    }
                    DriveContents driveContents = result.getDriveContents();
                    try {
                        ParcelFileDescriptor parcelFileDescriptor = driveContents.getParcelFileDescriptor();
                        // Append to the file.
                        FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor
                                .getFileDescriptor());
                        Writer writer = new OutputStreamWriter(fileOutputStream);
                        writer.append(convertToRTF(mEditor.getHtml()));
                        writer.close();
                    } catch (IOException e) {
                        Log.d(TAG, "You fukt up");
                    }
                    driveContents.commit(mGoogleApiClient, null).setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status result) {
                            Log.i(TAG, "Committed changes: "+mEditor.getHtml());
                            SuperActivityToast.create(MainActivity.this, new Style(), Style.TYPE_BUTTON)
                                    .setProgressBarColor(Color.WHITE)
                                    .setText("Document Saved")
                                    .setDuration(Style.DURATION_LONG)
                                    .setFrame(Style.FRAME_LOLLIPOP)
                                    .setColor(Color.BLACK)
                                    .setAnimations(Style.ANIMATIONS_POP).show();
                            // Handle the response status
                        }
                    });
                }
            };


    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    GestureOverlayView.OnGesturePerformedListener gesturePerformedListener = new GestureOverlayView.OnGesturePerformedListener() {
        @Override
        public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
            ArrayList<Prediction> prediction = gestureLibrary.recognize(gesture);
            if(prediction.size() > 0){
                Log.d("GESTURE_DETECTED", prediction.get(0).name);
                if (prediction.get(0).name.equalsIgnoreCase("left_arrow")){
                    mEditor.undo();
                }
                else if (prediction.get(0).name.equalsIgnoreCase("right_arrow")){
                    mEditor.redo();
                }
                else if (prediction.get(0).name.equalsIgnoreCase("ac_circle")){
                    mEditor.setItalic();
                }
                else if (prediction.get(0).name.equalsIgnoreCase("beta")){
                    mEditor.setBold();
                }
                else if (prediction.get(0).name.equalsIgnoreCase("U-shape")){
                    mEditor.setUnderline();
                }

            }
        }
    };

    void setEditor(){
        mEditor.setEditorHeight(200);
        mEditor.setLineHeight(2);
        mEditor.setEditorFontSize(22);
        mEditor.setEditorFontColor(Color.BLACK);
        //mEditor.setEditorBackgroundColor(Color.BLUE);
        //mEditor.setBackgroundColor(Color.BLUE);
        //mEditor.setBackgroundResource(R.drawable.bg);
        mEditor.setPadding(10, 15, 10, 10);
        //    mEditor.setBackground("https://raw.githubusercontent.com/wasabeef/art/master/chip.jpg");
        mEditor.setPlaceholder("Insert text here...");

        mEditor.setOnTextChangeListener(new RichEditor.OnTextChangeListener() {
            @Override
            public void onTextChange(String text) {
                if (refineMode){
                    proofRead();
                }
            }
        });

        findViewById(R.id.action_undo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.undo();
            }
        });

        findViewById(R.id.action_redo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.redo();
            }
        });

        findViewById(R.id.action_bold).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setBold();
            }
        });

        findViewById(R.id.action_italic).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setItalic();
            }
        });

        /*findViewById(R.id.action_subscript).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setSubscript();
            }
        });

        findViewById(R.id.action_superscript).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setSuperscript();
            }
        });

        findViewById(R.id.action_strikethrough).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setStrikeThrough();
            }
        });*/

        findViewById(R.id.action_underline).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setUnderline();
            }
        });

        /*findViewById(R.id.action_heading1).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(1);
            }
        });

        findViewById(R.id.action_heading2).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(2);
            }
        });

        findViewById(R.id.action_heading3).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(3);
            }
        });

        findViewById(R.id.action_heading4).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(4);
            }
        });

        findViewById(R.id.action_heading5).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(5);
            }
        });

        findViewById(R.id.action_heading6).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(6);
            }
        });

        findViewById(R.id.action_txt_color).setOnClickListener(new View.OnClickListener() {
            private boolean isChanged;

            @Override public void onClick(View v) {
                mEditor.setTextColor(isChanged ? Color.BLACK : Color.RED);
                isChanged = !isChanged;
            }
        });

        findViewById(R.id.action_bg_color).setOnClickListener(new View.OnClickListener() {
            private boolean isChanged;

            @Override public void onClick(View v) {
                mEditor.setTextBackgroundColor(isChanged ? Color.TRANSPARENT : Color.YELLOW);
                isChanged = !isChanged;
            }
        });*/

        findViewById(R.id.action_indent).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setIndent();
            }
        });

        findViewById(R.id.action_outdent).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setOutdent();
            }
        });

        findViewById(R.id.action_align_left).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignLeft();
            }
        });

        findViewById(R.id.action_align_center).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignCenter();
            }
        });

        findViewById(R.id.action_align_right).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignRight();
            }
        });

        /*findViewById(R.id.action_blockquote).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setBlockquote();
            }
        });

        findViewById(R.id.action_insert_image).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.insertImage("http://www.1honeywan.com/dachshund/image/7.21/7.21_3_thumb.JPG",
                        "dachshund");
            }
        });

        findViewById(R.id.action_insert_link).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.insertLink("https://github.com/wasabeef", "wasabeef");
            }
        });
        findViewById(R.id.action_insert_checkbox).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.insertTodo();
            }
        });*/
    }

    public void proofRead(){
        mEditor.removeHighlights();
        String htmlText = mEditor.getHtml();
        String text;
        try{
            text = Jsoup.parse(htmlText).text();

        } catch (IllegalArgumentException e){
            return;
        }
        String[] textList = text.split("\\s+");
        Map<String, Integer> map = new HashMap<>();
        for (String w : textList) {
            if (!Arrays.asList(commonWordList).contains(w)) {
                Integer n = map.get(w);
                n = (n == null) ? 1 : ++n;
                map.put(w, n);
            }
        }
        try {
            List<String> mostFrequent = mostOften(map, 2);
            Log.d("FREQ_COUNTER", mostFrequent.toString());
            mEditor.highlightWords(mostFrequent.get(0));
            mEditor.highlightWords(mostFrequent.get(1));
        } catch (IndexOutOfBoundsException e) {
            return;
        }
    }

    public static List<String> mostOften(Map<String, Integer> m, int k){
        List<MyWord> l = new ArrayList<>();
        for(Map.Entry<String, Integer> entry : m.entrySet())
            l.add(new MyWord(entry.getKey(), entry.getValue()));

        Collections.sort(l);
        List<String> list = new ArrayList<>();
        for(MyWord w : l.subList(0, k))
            list.add(w.word);
        return list;
    }

    private static String convertToRTF(String htmlStr) {
        try {
            Document doc = new Document();
            DocumentBuilder builder = new DocumentBuilder(doc);
            builder.insertHtml(htmlStr);
            ByteArrayOutputStream op = new ByteArrayOutputStream();
            doc.save(op, SaveFormat.RTF);
            String rtFString = op.toString();
            rtFString = rtFString.replace("Evaluation Only. Created with Aspose.Words. Copyright 2003-2014 Aspose Pty Ltd.", "");
            Log.d(TAG,rtFString);
            return rtFString;
        }

        catch( Exception e) {
            e.printStackTrace();
        }
        return null;
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        stopRepeatingTask();
//    }
//
//    Runnable mStatusChecker = new Runnable() {
//        @Override
//        public void run() {
//            try {
//                if (refineMode){
//                    proofRead();
//                }
//            } finally {
//                // 100% guarantee that this always happens, even if
//                // your update method throws an exception
//                mHandler.postDelayed(mStatusChecker, mInterval);
//            }
//        }
//    };
//
//    void startRepeatingTask() {
//        mStatusChecker.run();
//    }
//
//    void stopRepeatingTask() {
//        mHandler.removeCallbacks(mStatusChecker);
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_save, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // User chose the "Settings" item, show the app settings UI...
                return true;

            case R.id.action_save:
                new MaterialDialog.Builder(this)
                        .title(R.string.document_title)
                        .inputType(InputType.TYPE_CLASS_TEXT)
                        .input(getString(R.string.title_hint), fileName, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                String newFileName = input.toString();
                                saveFile(newFileName);
                                // Do something
                            }
                        }).show();
                return true;

            case R.id.action_review:
                refineMode = !refineMode;
                if (refineMode){
                    proofRead();
                }
                else{
                    mEditor.removeHighlights();
                }
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
}
