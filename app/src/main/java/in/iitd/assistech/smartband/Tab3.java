package in.iitd.assistech.smartband;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.Random;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

//import com.bumptech.glide.Glide;
import com.bumptech.glide.Glide;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

import java.util.Arrays;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.facebook.FacebookSdk.getApplicationContext;
import static in.iitd.assistech.smartband.MainActivity.EMAIL;
import static in.iitd.assistech.smartband.MainActivity.NAME;
import static in.iitd.assistech.smartband.MainActivity.PHOTO;
import static in.iitd.assistech.smartband.MainActivity.PHOTOURI;
import static in.iitd.assistech.smartband.MainActivity.SIGNED;

public class Tab3 extends Fragment implements View.OnClickListener, GoogleApiClient.OnConnectionFailedListener{


    //--------------------------------
    Button buttonStart, buttonStop, buttonPlayLastRecordAudio,
            buttonStopPlayingRecording ;
    String AudioSavePathInDevice = null;
    MediaRecorder mediaRecorder ;
    Random random ;
    String RandomAudioFileName = "ABCDEFGHIJKLMNOP";
    public static final int RequestPermissionCode = 1;
    MediaPlayer mediaPlayer ;
    //--------------------------------


    public View view;
    private static final String TAG = "Tab3";


    private ExpandableListAdapter notifListAdapter;
    private ExpandableListAdapter soundListAdapter;
    private ExpandableListView notifListView;
    private ExpandableListView soundListView;
    static final String[] notificationListItems = {"Vibration", "Sound", "Flashlight", "Flash Screen"};
    static final String[] soundListItems = {"Vehicle Horn", "Dog Bark"};

    private CircleImageView userProfileImage;
    private TextView userName;
    private TextView userEmail;

    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private GoogleApiClient mGoogleApiClient;
    private String providerId;
    private String uid;
    private String name;
    private String email;


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        int state = notificationListItems.length+soundListItems.length;
        boolean[] notifSwitchState = new boolean[state];
//        boolean[] soundSwitchState = new boolean[soundListItems.length];

        Log.e(TAG, "FUCK YOU!!!!!!!!!!!!!!!!!!!!!!");
        for (int i=0; i<notificationListItems.length; i++){
            notifSwitchState[i] = notifListAdapter.getCheckedState(i);
        }
        for (int i=notificationListItems.length; i<state; i++){
            notifSwitchState[i] = soundListAdapter.getCheckedState(i-notificationListItems.length);
//            soundSwitchState[i] = soundListAdapter.getCheckedState(i);
        }

//        Log.e(TAG, "SoundStare 0 and 1 " + soundSwitchState[0] + ", " + soundSwitchState[1]);

        outState.putBooleanArray("notifState", notifSwitchState);
//        outState.putBooleanArray("soundState", soundSwitchState);
    }

//    @Override
//    public void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }


    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        view = inflater.inflate(R.layout.tab3, container, false);
//        userProfileImage = (ImageView)view.findViewById(R.id.mUserProfilePic);
        userName = (TextView) view.findViewById(R.id.userName);
        userEmail = (TextView) view.findViewById(R.id.userEmail);

        userProfileImage = (CircleImageView)view.findViewById(R.id.mUserProfilePic);

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        Log.e(TAG, "Provider ID : " + providerId);
        for (UserInfo user: FirebaseAuth.getInstance().getCurrentUser().getProviderData()) {
            Log.e(TAG, user.getProviderId());
            if (user.getProviderId().equals("facebook.com")) {
                System.out.println("User is signed in with Facebook");
            } else {
                if(mGoogleApiClient == null){
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .build();
                    mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                            .enableAutoManage(getActivity() /* FragmentActivity */, this /* OnConnectionFailedListener */)
                            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                            .build();
                }
            }
        }

        view.findViewById(R.id.signOutButton).setOnClickListener(this);
        view.findViewById(R.id.revokeButton).setOnClickListener(this);

        // UID specific to the provider
        uid = user.getUid();
        updateUI();

        /**--------------------------------**/
        notifListView = (ExpandableListView) view.findViewById(R.id.notificationListView);
        soundListView = (ExpandableListView) view.findViewById(R.id.soundListView);
        if(savedInstanceState != null){
            boolean[] switchState = savedInstanceState.getBooleanArray("notifState");
            boolean[] notifSwitchState = Arrays.copyOf(switchState, notificationListItems.length);
            boolean[] soundSwitchState = new boolean[soundListItems.length];
            for (int i=0; i<soundListItems.length; i++){
                soundSwitchState[i] = switchState[notificationListItems.length+i];
            }
//            boolean[] soundSwitchState = savedInstanceState.getBooleanArray("soundState");
            try{
//                notifListAdapter = new NotifListAdapter(getContext(), notificationListItems, notifSwitchState);
                notifListAdapter = new ExpandableListAdapter(getContext(), notificationListItems, "Notification", notifSwitchState);
//                ListView notifListView = (ListView) view.findViewById(R.id.notificationListView);
                notifListView.setAdapter(notifListAdapter);

                soundListAdapter = new ExpandableListAdapter(getContext(), soundListItems, "Sound Types", soundSwitchState);
                soundListView.setAdapter(soundListAdapter);
            }catch(Exception e){
                Log.e(TAG, e.toString());
                Toast.makeText(getContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        } else{
            boolean[] startNotifState = MainActivity.getStartNotifListState();
            boolean[] startSoundState = MainActivity.getStartSoundListState();
            notifListAdapter = new ExpandableListAdapter(getContext(), notificationListItems, "Notification", startNotifState);
            notifListView.setAdapter(notifListAdapter);

            soundListAdapter = new ExpandableListAdapter(getContext(), soundListItems, "Sound Types", startSoundState);
            soundListView.setAdapter(soundListAdapter);
        }
        /**----------------------------------------------**/

        notifListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {

            @Override
            public void onGroupExpand(int groupPosition) {
//                Toast.makeText(getApplicationContext(), " Expanded",
//                        Toast.LENGTH_SHORT).show();
            }
        });
        /**-------------------------------**/

        //------------------------------------------------------

        buttonStart = (Button) view.findViewById(R.id.button);
        buttonStop = (Button) view.findViewById(R.id.button2);
        buttonPlayLastRecordAudio = (Button) view.findViewById(R.id.button3);
        buttonStopPlayingRecording = (Button)view.findViewById(R.id.button4);

        buttonStop.setEnabled(false);
        buttonPlayLastRecordAudio.setEnabled(false);
        buttonStopPlayingRecording.setEnabled(false);

        random = new Random();

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(checkPermission()) {

                    AudioSavePathInDevice =
                            Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                                    CreateRandomAudioFileName(5) + "AudioRecording.mp4";
                    System.out.println("-----------------------" + "AudioSavePathInDevice : " + AudioSavePathInDevice + "-----------------------");
                    MediaRecorderReady();

                    try {
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                    } catch (IllegalStateException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    buttonStart.setEnabled(false);
                    buttonStop.setEnabled(true);

                    /*Toast.makeText(MainActivity.this, "Recording started",
                            Toast.LENGTH_LONG).show();*/
                } else {
                    requestPermission();
                }

            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaRecorder.stop();
                buttonStop.setEnabled(false);
                buttonPlayLastRecordAudio.setEnabled(true);
                buttonStart.setEnabled(true);
                buttonStopPlayingRecording.setEnabled(false);

                //Toast.makeText(MainActivity.this, "Recording Completed", Toast.LENGTH_LONG).show();
            }
        });

        buttonPlayLastRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {

                buttonStop.setEnabled(false);
                buttonStart.setEnabled(false);
                buttonStopPlayingRecording.setEnabled(true);

                mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(AudioSavePathInDevice);
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mediaPlayer.start();
                /*Toast.makeText(MainActivity.this, "Recording Playing",
                        Toast.LENGTH_LONG).show();*/
            }
        });

        buttonStopPlayingRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonStop.setEnabled(false);
                buttonStart.setEnabled(true);
                buttonStopPlayingRecording.setEnabled(false);
                buttonPlayLastRecordAudio.setEnabled(true);

                if(mediaPlayer != null){
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    MediaRecorderReady();
                }

                Intent i = new Intent(getActivity(), UploadActivity.class);
                i.putExtra("filePath", AudioSavePathInDevice);
                i.putExtra("isImage", false);
                startActivity(i);
            }
        });
        //------------------------------------------------------




        return view;
    }
    public void MediaRecorderReady(){
        mediaRecorder=new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        mediaRecorder.setOutputFile(AudioSavePathInDevice);
    }

    public String CreateRandomAudioFileName(int string){
        StringBuilder stringBuilder = new StringBuilder( string );
        int i = 0 ;
        while(i < string ) {
            stringBuilder.append(RandomAudioFileName.
                    charAt(random.nextInt(RandomAudioFileName.length())));

            i++ ;
        }
        return stringBuilder.toString();
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(getActivity(), new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length> 0) {
                    boolean StoragePermission = grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;
                    boolean RecordPermission = grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        System.out.println("Permission Granted");
                       /* Toast.makeText(MainActivity.this, "Permission Granted",
                                Toast.LENGTH_LONG).show();*/
                    } else {
                        System.out.println("Permission Denied");
                       // Toast.makeText(MainActivity.this,"Permission Denied",Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),
                WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(),
                RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED &&
                result1 == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.signOutButton:
                signOut();
                break;
            case R.id.revokeButton:
                revokeAccess();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mGoogleApiClient.stopAutoManage(getActivity());
        mGoogleApiClient.disconnect();
    }

    private void signOut() {
        // Firebase sign out
        mAuth.signOut();

        // Google sign out
        if(SIGNED.equals("GOOGLE")){
            Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            //TODO: updateUI(null);
                            Log.e(TAG, "Google SignOut1" + status.toString());
                        }
                    });
            Log.e(TAG, "Google SignOut");
        } else if (SIGNED.equals("FB")){
            LoginManager.getInstance().logOut();
        }

        if(mAuth.getCurrentUser() == null){
            Log.e(TAG, "Google SignOut258");
            Intent intent = new Intent(getActivity(), SignInActivity.class);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private void revokeAccess() {
        // Firebase sign out
        mAuth.signOut();

        if(SIGNED.equals("GOOGLE")){
            // Google revoke access
            Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            //TODO: updateUI(null);
                        }
                    });
            Log.e(TAG, "Google SignOut");
        } else if (SIGNED.equals("FB")){
            LoginManager.getInstance().logOut();
        }

        if(mAuth.getCurrentUser() == null){
            Log.e(TAG, "Google SignOut259");
            Intent intent = new Intent(getActivity(), SignInActivity.class);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private void updateUI(){
//        name = user.getDisplayName();
//        email = user.getEmail();
//        photoUrl = user.getPhotoUrl();
//        String mUserprofileUrl = photoUrl.toString();
        name = NAME;
        email = EMAIL;
        String mUserprofileUrl = PHOTOURI;

        userName.setText(name);
        userEmail.setText(email);
        try{
            Glide
                    .with(getContext())
                    .load(mUserprofileUrl)
                    .into(userProfileImage);//.placeholder(R.mipmap.ic_launcher).fitCenter()
        }catch (Exception e){
            Log.e(TAG, e.toString());
        }

        Log.d(TAG, name + email);
        Log.e(TAG, "Photo : " + mUserprofileUrl);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.e(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(getActivity(), "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    /**--------------------------------------------**/
    public boolean[] getFinalNotifState(){
        boolean[] notifSwitchState = new boolean[notificationListItems.length];
        for (int i=0; i<notificationListItems.length; i++){
            notifSwitchState[i] = notifListAdapter.getCheckedState(i);
        }
        return notifSwitchState;
    }

    public boolean[] getFinalSoundState(){
        boolean[] soundSwitchState = new boolean[soundListItems.length];
        for (int i=0; i<soundListItems.length; i++){
            soundSwitchState[i] = notifListAdapter.getCheckedState(i);
        }
        return soundSwitchState;
    }

}
