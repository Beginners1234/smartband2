package in.iitd.assistech.smartband;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.sounds.ClassifySound;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

import static com.sounds.ClassifySound.PROCESS_LENGTH;
import static in.iitd.assistech.smartband.Tab3.notificationListItems;
import static in.iitd.assistech.smartband.Tab3.soundListItems;
import static java.security.AccessController.getContext;

public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener,
        OnTabEvent{

    private static final String TAG = "MainActivity";
    public static String NAME;
    public static String EMAIL;
    public static String PHOTO;
    public static String PHOTOURI;
    public static String SIGNED;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    static Pager adapter;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /***Variables for audiorecord*/
    private MediaRecorder mRecorder = null;
    private static int REQUEST_MICROPHONE = 101;
    public static final int RECORDER_SAMPLERATE = ClassifySound.RECORDER_SAMPLERATE;
    public static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    public static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int RECORD_TIME_DURATION = 500; //0.5 seconds
    static int BufferElements2Rec = (int)RECORDER_SAMPLERATE * RECORD_TIME_DURATION/1000; // number of 16 bits for 3 seconds
    //    BufferElements2Rec =
    static String[] warnMsgs = {"Car Horn Detected", "Dog Bark Detected", "Ambient"};
    static int[] warnImgs = new int[] {R.drawable.car_horn, R.drawable.dog_bark, 0};

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    int bufferSize;
    int BytesPerElement;
    public short[] sData = new short[BufferElements2Rec];
    static double thresh_prob = 0.75;
    double[][] history_prob = new double[5][ClassifySound.numOutput];
    int hist_prob_counter = 0;

    private static boolean[] startNotifListState;
    private static boolean[] startSoundListState;
    /*
    public AlertDialog.Builder warnDB;
    public AlertDialog warnDialog;
    */
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "SmartBand";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    public AlertDialog myDialog;


    static Handler uiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            double[] prob_sound = (double[]) msg.obj;
            boolean[] notifState = adapter.getInitialNotifListState();
            adapter.editTab2Text(prob_sound, notifState);
        }
    };

    void setProbOut(double[] outProb){
        //send msg to edit value of prob on screen
        //Launch dialog interface;
        Message message = uiHandler.obtainMessage();
        message.obj = outProb;
        message.sendToTarget();
//        for(int i=0; i<(outProb.length-2); i++){
//            if(outProb[i]> 0.60){
//                showDialog(MainActivity.this, i, outProb);
////                Message message = uiHandler.obtainMessage();
////                message.obj = outProb;
////                message.sendToTarget();
//            }
//        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
//        SharedPreferences.Editor editor = app_preferences.edit();
//        boolean[] notifState = getFinalNotifState();
//        editor.putBoolean("Vibration", notifState[0]);
//        editor.putBoolean("Sound", notifState[1]);
//        editor.putBoolean("FlashLight", notifState[2]);
//        editor.putBoolean("FlashScreen", notifState[3]);
//        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean[] notifState = new boolean[notificationListItems.length];
        for (int i=0; i<notificationListItems.length; i++){
            notifState[i] = app_preferences.getBoolean(notificationListItems[i], true);
        }
        boolean[] soundState =  new boolean[soundListItems.length];
        for (int i=0; i<soundListItems.length; i++){
            soundState[i] = app_preferences.getBoolean(soundListItems[i], true);
        }

        startNotifListState = notifState;
        startSoundListState = soundState;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = app_preferences.edit();
        if(adapter.getInitialNotifListState() != null){
            boolean[] notifState = adapter.getInitialNotifListState();
            for (int i=0; i<notificationListItems.length; i++){
                editor.putBoolean(notificationListItems[i], notifState[i]);
            }
            editor.commit();
        }

        if(adapter.getInitialSoundListState() != null){
            boolean[] soundState = adapter.getInitialSoundListState();
            for (int i=0; i<soundListItems.length; i++){
                editor.putBoolean(soundListItems[i], soundState[i]);
            }
            editor.commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        NAME = getIntent().getStringExtra("NAME");
        EMAIL = getIntent().getStringExtra("EMAIL");
        PHOTO = getIntent().getStringExtra("PHOTO");
        PHOTOURI = getIntent().getStringExtra("PHOTOURI");
        SIGNED = getIntent().getStringExtra("SIGNED");

        //TODO: Hardware Acceleration
//        getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
//                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);


        //Adding toolbar to the activity
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        //Initializing the tablayout
        tabLayout = (TabLayout) findViewById(R.id.tabLayout);

        //Initializing viewPager
        viewPager = (ViewPager) findViewById(R.id.pager);
        tabLayout.setupWithViewPager(viewPager);

        //Adding the tabs using addTab() method
        tabLayout.addTab(tabLayout.newTab());        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        //Creating our pager adapter
        adapter = new Pager(getSupportFragmentManager(), tabLayout.getTabCount());
        //Adding adapter to pager
        viewPager.setAdapter(adapter);

        tabLayout.getTabAt(0).setText("Chat");
        tabLayout.getTabAt(1).setText("Sound");
        tabLayout.getTabAt(2).setText("Settings");

        //Adding onTabSelectedListener to swipe views
//        tabLayout.setOnTabSelectedListener(this);
        tabLayout.addOnTabSelectedListener(this);

//        for(int i=0; i<sData.length; i++){
//            sData[i] = (short)0;
//        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);

        }

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        viewPager.setCurrentItem(1);

        int permission = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void onButtonClick(String text) {
        if(text == "MicReadButton"){
            startRecording();
//            try{
//
//                processMicSound();
//            }catch (InterruptedException e){
//                Log.e(TAG, "onButtonClick() " + e.toString());
//            }
        }else if(text == "StopRecordButton"){
            stopRecording();
        }
    }

    /**--------------Sign In-------------------**/

    /**----------------------------------------**/
    /**----------For Sound Processing and stuff----------**/
    //Load the weights and bias in the form of matrix from sheet in Assets Folder

    public void processMicSound() throws InterruptedException{
        for(int i=0; i<sData.length; i++){
            sData[i] = (short)0;
        }
        for(int i=0; i<history_prob.length; i++){
            for(int j=0; j<history_prob[0].length; j++){
                history_prob[i][j] = 0.0;
            }
        }
        //@link https://stackoverflow.com/questions/40459490/processing-in-audiorecord-thread
        HandlerThread myHandlerThread = new HandlerThread("my-handler-thread");
        myHandlerThread.start();

        //make below gloabl and remove final
        final Handler myHandler = new Handler(myHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
//                someHeavyProcessing((short[]) msg.obj, msg.arg1);
                try{
                    Log.e(TAG, "handleMessage");
                    long start_time = System.nanoTime();

                    //Calculate probability using ClassifySound.
                    short[] temp_sound = (short[]) msg.obj;
                    short[] temp1 = Arrays.copyOfRange(temp_sound, 0, PROCESS_LENGTH);
                    double[] temp_prob = ClassifySound.getProb(temp1);
                    Log.e(TAG + " temp_prob", Double.toString(temp_prob[0]) + ", " + Double.toString(temp_prob[1]));
//                            + ", " + Double.toString(temp_prob[2])+ ", " + Double.toString(temp_prob[3]));

                    setProbOut(temp_prob);
                    writeToExcel("Sound_File.csv", temp1);

//                    for(int i=0; i<temp_prob.length; i++){
//                        if(Double.isNaN(temp_prob[i])) temp_prob[i] = 1.0;
//                        history_prob[hist_prob_counter][i] = temp_prob[i];
//                    }
//
//                    double[] mean_prob = new double[temp_prob.length];
//                    for(int j=0; j<mean_prob.length; j++){
//                        double sum = 0;
//                        for(int i=0; i<history_prob.length; i++){
//                            sum += history_prob[i][j];
//                        }
//                        mean_prob[j] = sum * 1.0/history_prob.length;
//                    }
//
//                    hist_prob_counter++;//update the counter and make it zero if equals length of hist
//                    if(hist_prob_counter >= history_prob.length) {
//                        hist_prob_counter = 0;
//                        setProbOut(mean_prob);
//                    }

//                    Log.e(TAG + " mean_prob ", Double.toString(mean_prob[0]) + ", " + Double.toString(mean_prob[1])
//                            + ", " + Double.toString(mean_prob[2]));
                    long end_time = System.nanoTime();
                    double temp = (end_time - start_time)*1.0/1000000;
                    Log.e(TAG, "TIME : " + Double.toString(temp));
//                    Log.e(TAG, Double.toString(mean_prob[0]) + ", " + Double.toString(mean_prob[1])
//                                        + ", " + Double.toString(mean_prob[2]));
                }catch (Exception e){
                    System.out.print(TAG + " myHandler : " + e.toString());
                }
                return true;
            }
        });

        if(!isRecording){
            BytesPerElement = 2; // 2 bytes in 16bit format
            if (bufferSize != AudioRecord.ERROR_BAD_VALUE && bufferSize > 0){
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING,BufferElements2Rec*BytesPerElement);//
                recorder.startRecording();
            }

//            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    while (isRecording) {
                        synchronized (this){
//                            int num_read = recorder.read(sData, 0, BufferElements2Rec);
//                            Message message = myHandler.obtainMessage();
//                            message.obj = sData;
//                            message.arg1 = num_read;
//                            message.sendToTarget();
                            writeAudioDataToFile();
                        }
                    }
                }
            }, "AudioRecorder Thread");
            recordingThread.start();

        } else{
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }

        ////
        copyWaveFile(getTempFilename(),getFilename());
        deleteTempFile();
    }

    /**---------------------------------------**/

    public void showDialog(Context context, int idx, double[] outProb) {
        if (myDialog != null && myDialog.isShowing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(warnMsgs[idx]);
        ImageView wrnImg = new ImageView(MainActivity.this);
        //TODO No Text clickable button for dog bark case dialog
//        ViewGroup.LayoutParams imgParams = wrnImg.getLayoutParams();
//        imgParams.width = 60;
//        imgParams.height = 60;
//        wrnImg.setLayoutParams(imgParams);
        wrnImg.setImageResource(warnImgs[idx]);
        builder.setView(wrnImg);
//        builder.setMessage("Message");
//        builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int arg1) {
//                dialog.dismiss();
//            }});
        builder.setPositiveButton("Ok, Thank You!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Wrong Detection", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });

        builder.setCancelable(true);
        myDialog = builder.create();
        myDialog.show();
        final Timer timer2 = new Timer();
        timer2.schedule(new TimerTask() {
            public void run() {
                myDialog.dismiss();
                Log.e(TAG, "TIMER Running");
                timer2.cancel(); //this will cancel the timer of the system
            }
        }, 15000); // the timer will count 5 seconds....
    }

    /**-----------For Tab Layout--------------**/
    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
        tabLayout.getTabAt(tab.getPosition()).select();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);
        int i = recorder.getState();
        if (i==1)
            recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");

        recordingThread.start();
    }

    static boolean[] getStartNotifListState(){
        return startNotifListState;
    }

    static boolean[] getStartSoundListState(){
        return startSoundListState;
    }

    public void writeToExcel(String filename, short[] output){ //String detail
        File external = Environment.getExternalStorageDirectory();
        String sdcardPath = external.getPath() + "/SmartBand/";
        // to this path add a new directory path
        File file = new File(external, filename);
        try{
            if (!file.exists()){
                file.createNewFile();
            }
            FileOutputStream fos  = this.openFileOutput(filename, this.MODE_APPEND);
//                            Writer out = new BufferedWriter(new OutputStreamWriter(openFileOutput(file.getName(), MODE_APPEND)));

            android.text.format.DateFormat df = new android.text.format.DateFormat();
            Date date = new Date();

            FileWriter filewriter = new FileWriter(sdcardPath + filename, true);
            BufferedWriter out = new BufferedWriter(filewriter);
//            StringBuilder sb = new StringBuilder(dateString.length());
//            sb.append(dateString);
            Log.e(TAG, "Time: " + date.toString());

            String data ="";
            for(int i=0; i<output.length; i++){
                data += output[i] + ",";
            }
            data += "\n";
//            data += detail + "\n";

            out.write(data);
            out.close();
            filewriter.close();

            fos.close();
        }catch(Exception e){
            Log.e(TAG, e.toString() + " FileOutputStream");
        }
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void writeitk(byte[] data){
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (null != os) {

        }
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;
        if (null != os) {
            while(isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (read > 0){
                }

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() +
                AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private void copyWaveFile(String inFilename,String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

//            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels,
                                     long byteRate) throws IOException{
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}