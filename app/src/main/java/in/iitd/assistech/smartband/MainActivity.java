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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

    /***Variables for audiorecord*/
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
//        for(int i=0; i<(outProb.length-1); i++){
//            if(outProb[i]> 0.80){
//                showDialog(MainActivity.this, i, outProb);
//                Message message = uiHandler.obtainMessage();
//                message.obj = outProb;
//                message.sendToTarget();
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

        viewPager.setCurrentItem(1);
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
                    Log.e(TAG + " temp_prob", Double.toString(temp_prob[0]) + ", " + Double.toString(temp_prob[1])
                            + ", " + Double.toString(temp_prob[2]));

                    setProbOut(temp_prob);

                    for(int i=0; i<temp_prob.length; i++){
                        if(Double.isNaN(temp_prob[i])) temp_prob[i] = 1.0;
                        history_prob[hist_prob_counter][i] = temp_prob[i];
                    }
                    hist_prob_counter++;//update the counter and make it zero if equals length of hist
                    if(hist_prob_counter >= history_prob.length) hist_prob_counter = 0;

                    double[] mean_prob = new double[temp_prob.length];
                    for(int j=0; j<mean_prob.length; j++){
                        double sum = 0;
                        for(int i=0; i<history_prob.length; i++){
                            sum += history_prob[i][j];
                        }
                        mean_prob[j] = sum * 1.0/history_prob.length;
                    }

//                    setProbOut(mean_prob);

                    Log.e(TAG + " mean_prob ", Double.toString(mean_prob[0]) + ", " + Double.toString(mean_prob[1])
                            + ", " + Double.toString(mean_prob[2]));
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
                        RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);//bufrSize
                recorder.startRecording();
            }

            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    while (isRecording) {
                        synchronized (this){
                            int num_read = recorder.read(sData, 0, BufferElements2Rec);
                            Message message = myHandler.obtainMessage();
                            message.obj = sData;
                            message.arg1 = num_read;
                            message.sendToTarget();
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

    static boolean[] getStartNotifListState(){
        return startNotifListState;
    }

    static boolean[] getStartSoundListState(){
        return startSoundListState;
    }
}