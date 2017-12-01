package in.iitd.assistech.smartband;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.sounds.ClassifySound;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

import static com.sounds.ClassifySound.PROCESS_LENGTH;
import static in.iitd.assistech.smartband.Tab3.notificationListItems;
import static in.iitd.assistech.smartband.Tab3.soundListItems;

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

    double[][] inputFeat;

    private static int PROB_MSG_HNDL = 123;

    /***Variables for audiorecord*/
    private static int REQUEST_MICROPHONE = 101;
    public static final int RECORDER_SAMPLERATE = 44100;
    public static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    public static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int RECORD_TIME_DURATION = 500; //0.5 seconds
//    BufferElements2Rec =

    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private Thread processThread = null;
    private boolean isRecording = false;
    int bufferSize;
    static int BufferElements2Rec = (int)RECORDER_SAMPLERATE * RECORD_TIME_DURATION/1000; // number of 16 bits for 3 seconds
    int BytesPerElement;
    public short[] sData = new short[BufferElements2Rec];
    short[] sDataCopy = new short[sData.length];
    short[] sDataCopyB = new short[sDataCopy.length];
    public double[] prob = new double[ClassifySound.numOutput];

    private static boolean[] startNotifListState;
    private static boolean[] startSoundListState;


    static Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.getData().getInt("what") == PROB_MSG_HNDL){
                //TODO create an instance of Tab2 fragment and call editValue()
                double hornProb = msg.getData().getDouble("hornProb");
                double barkProb = 0.0;//msg.getData().getDouble("dogBarkProb");
                double gunShotProb = 0.0;//msg.getData().getDouble("gunShotProb");
                double ambientProb = msg.getData().getDouble("ambientProb");
                boolean[] notifState = adapter.getInitialNotifListState();
                double[] prob = new double[3];
                prob[0] = hornProb;
                prob[1] = barkProb;
                prob[2] = ambientProb;
                adapter.editTab2Text(prob, notifState);
            }
        }
    };

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
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
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

//        sData = new short[BufferElements2Rec];
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

        /**-------------------------------**/

    }

    @Override
    public void onButtonClick(String text) {
        if(text == "MicReadButton"){
            try{
                Log.e(TAG, "HEY YOU|");
                processMicSound();
            }catch (InterruptedException e){
                Log.e(TAG, "onButtonClick() " + e.toString());
            }
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
        if(!isRecording){
            BufferElements2Rec = RECORDER_SAMPLERATE * RECORD_TIME_DURATION/1000; // number of 16 bits for 3 seconds
            //BufferElements2Rec = 24000;
            Log.e(TAG, Integer.toString(BufferElements2Rec));

//            int bufrSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            BytesPerElement = 2; // 2 bytes in 16bit format
            if (bufferSize != AudioRecord.ERROR_BAD_VALUE && bufferSize > 0){
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);//bufrSize
                recorder.startRecording();
            }

            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    while (isRecording) {
                        synchronized (this){
//                            sData = new short[BufferElements2Rec];
                            recorder.read(sData, 0, BufferElements2Rec);
                            sDataCopy = Arrays.copyOfRange(sData, 0, PROCESS_LENGTH);
                            sDataCopyB = Arrays.copyOfRange(sDataCopy, 0, PROCESS_LENGTH);

//                                TODO:processAudioEvent();
//                            mProcessing = new MainProcessing(MainActivity.this);
//                            mProcessing.processAudioEvent()
//                            try{
////                                for(int i=0; i<sData.length; i++){
////                                    sDataCopy[i] = sData[i];
////                                }
//                                long start_time = System.nanoTime();
//                                prob = ClassifySound.getProb(sDataCopy);
//                                long end_time = System.nanoTime();
//                                double temp = (start_time - end_time)*1.0/1000000;
//                                Log.e(TAG + " TIME ", Double.toString(temp));
//                                Log.e(TAG, Double.toString(prob[0]) + ", " + Double.toString(prob[1])
//                                        + ", " + Double.toString(prob[2]));
//                                boolean[] notifState = adapter.getInitialNotifListState();
//                                adapter.editTab2Text(prob, notifState);
//                            }catch(Exception e){
//                                Log.e(TAG, e.toString());
//                            }
                        }
                    }
                }
            }, "AudioRecorder Thread");
            recordingThread.start();

            processThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (this){
                        try{
                            Log.e(TAG, "Not Here class");
//                            short[] sDataCopy = new short[sData.length];
//                            for(int i=0; i<sData.length; i++){
//                                sDataCopy[i] = sData[i];
//                            }
//                        Log.e(TAG, s);
                            long start_time = System.nanoTime();
                            prob = ClassifySound.getProb(sDataCopy);
                            setProbOut(prob);
                            long end_time = System.nanoTime();
                            double temp = (start_time - end_time)*1.0/1000000;
                            Log.e(TAG + " TIME ", Double.toString(temp));
                            Log.e(TAG, Double.toString(prob[0]) + ", " + Double.toString(prob[1])
                                    + ", " + Double.toString(prob[2]));
                        }catch(Exception e){
                            Log.e(TAG, e.toString());
                        }
                    }
                }
            });
            processThread.start();
//            processThread.join();
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
            processThread.stop();
            recorder = null;
            recordingThread = null;
        }
    }

    /**---------------------------------------**/

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

    static void setProbOut(double[] outProb){
//        adapter.editTab2Text(outProb[0], outProb[1], outProb[2]);
        double hornProb = (outProb[0]);
//        double gunShotProb = (outProb[2]);
        double dogBarkProb = (outProb[1]);
        double ambientProb = (outProb[2]);

        final Message msg = new Message();
        final Bundle bundle = new Bundle();
        bundle.putInt("what", PROB_MSG_HNDL);
        bundle.putDouble("hornProb", hornProb);
        bundle.putDouble("dogBarkProb", dogBarkProb);
//        bundle.putDouble("gunShotProb", gunShotProb);
        bundle.putDouble("ambientProb", ambientProb);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    static boolean[] getStartNotifListState(){
        return startNotifListState;
    }

    static boolean[] getStartSoundListState(){
        return startSoundListState;
    }
}