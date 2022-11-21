package com.example.myapplication.vision;

import static com.example.myapplication.api_ver.get_api.getStaionBusData;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.api_notice.Alarm_Reciver_api;
import com.example.myapplication.api_notice.NoticeApi;
import com.example.myapplication.api_ver.get_api;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class blind_wait extends AppCompatActivity {
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference;
    private FirebaseAuth mFirebaseAuth = FirebaseAuth.getInstance();
    FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
    NoticeApi noticeData;
    String[] busData_list;
    String[] busData_fast;
    String busData_arrt, fast_arrp;
    int notice_pos = 0;
    boolean busRide = false, first_bool = true;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;
    TextToSpeech tts;
    String sNodeord = null, eNodeord, nowNodeOrd;

    @Override
    protected void onCreate(Bundle savedIntancdState) {
        super.onCreate(savedIntancdState);
        setContentView(R.layout.blind_wait);

        TextView textView = (TextView) findViewById(R.id.busData_text);
        final Intent my_intent = new Intent(blind_wait.this, Alarm_Reciver_api.class);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { //tts구현
            @Override
            public void onInit(int i) {
                if (i == TextToSpeech.SUCCESS) { //tts 잘되면
                    tts.setLanguage(Locale.KOREAN);     //한국어로 설정
                    //tts.setSpeechRate(0.8f); //말하기 속도 지정 1.0이 기본값
                }
            }
        });

        databaseReference = database.getReference().child("Notice_api");
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int j = 1;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if (snapshot.child("Uid").getValue(String.class).equals(firebaseUser.getUid())) {
                        noticeData = snapshot.getValue(NoticeApi.class);
                        notice_pos = j;
                        break;
                    }
                    j++;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                String[] RouteData = get_api.getBusRoute(noticeData.getCityCode(), noticeData.getRouteId(), "1").split("\n");
                for (int i=0; i<RouteData.length; i++) {
                    String[] RouteData_List = RouteData[i].split(" ");
                    if (RouteData_List[2].equals(noticeData.getSbusStopNodeId())) {
                        sNodeord = RouteData_List[5];
                    }
                    if (RouteData_List[2].equals(noticeData.getEbusStopNodeId())) {
                        eNodeord = RouteData_List[5];
                        break;
                    }
                }
            }
        };
        timer.schedule(timerTask, 1000);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (!busRide) {
                    String[] busData;
                    busData = getStaionBusData(noticeData.getCityCode(), noticeData.getRouteId(), noticeData.getSbusStopNodeId()).split("\n");
                    if (busData.length == 0) {
                        textView.setText("API 서버 오류... 대기중");
                    } else {
                        for (int i = 0; i < busData.length; i++) {
                            busData_list = busData[i].split(" ");
                            // [0]:남은정류장수, [1]:남은시간, [2]:정류장명, [3]:버스번호
                            if (i == 0) {
                                busData_arrt = busData_list[1];
                                busData_fast = busData_list;
                            } else {
                                if (Integer.parseInt(busData_arrt) > Integer.parseInt(busData_list[1])) {
                                    busData_arrt = busData_list[1];
                                    busData_fast = busData_list;
                                }
                            }
                        }
                        textView.setText("탑승 정류장 : " + busData_fast[2] + "\n버스 번호 : " + busData_fast[3] + "\n남은 정류장 수 : " + busData_fast[0] + "\n남은 시간 : " + busData_fast[1]);
                        if (busData_fast[0].equals("1")) {
                            busRide = true;
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run()
                                {
                                    tts.speak("잠시 후 버스가 도착할 예정입니다. 탑승을 준비해주세요.", TextToSpeech.QUEUE_ADD, null);
                                }
                            }, 0);

                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            vibrator.vibrate(new long[]{500, 500, 500, 500, 500, 500, 500, 500, 500, 500}, -1);
                            database.getReference("Notice_api").child(Integer.toString(notice_pos)).child("busRide").setValue("1");

                            String[] busServiceData = get_api.getBusServiceData(noticeData.getCityCode(), noticeData.getRouteId(), "1").split("\n");
                            for (int i = 0; i < busServiceData.length; i++) {
                                String[] busSD_List = busServiceData[i].split(" ");
                                if (busSD_List[1].equals(Integer.toString(Integer.parseInt(sNodeord) - 1))) {
                                    database.getReference("Notice_api").child(Integer.toString(notice_pos)).child("Vehicleno").setValue(busSD_List[2]);
                                    break;
                                }
                            }

                            /*
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                pendingIntent = PendingIntent.getBroadcast(blind_wait.this, 0, my_intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            } else {
                                pendingIntent = PendingIntent.getBroadcast(blind_wait.this, 0, my_intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            }

                            if (Build.VERSION.SDK_INT >= 23) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
                            } else {
                                alarmManager.set(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
                            }*/

                        }
                    }
                } else {
                    if (first_bool) {
                        fast_arrp = busData_fast[0];
                        first_bool = false;
                    }
                    if (!fast_arrp.equals(busData_fast[3])) {
                        String[] RouteposData = get_api.getBusServiceData(noticeData.getCityCode(), noticeData.getRouteId(), "1").split("\n");
                        for (int i = 0; i < RouteposData.length; i++) {
                            String[] Routepos_List = RouteposData[i].split(" ");
                            if (Routepos_List[2].equals(noticeData.getVehicleno())) {
                                nowNodeOrd = Routepos_List[1];
                                break;
                            }
                        }

                        String[] busData;
                        busData = getStaionBusData(noticeData.getCityCode(), noticeData.getRouteId(), noticeData.getEbusStopNodeId()).split("\n");
                        if (busData.length == 0) {
                            textView.setText("API 서버 오류... 대기중");
                        } else {
                            for (int i = 0; i < busData.length; i++) {
                                busData_list = busData[i].split(" ");
                                // [0]:남은정류장수, [1]:남은시간, [2]:정류장명, [3]:버스번호
                                if (Integer.parseInt(busData_list[0]) + Integer.parseInt(nowNodeOrd) == Integer.parseInt(eNodeord)) {
                                    textView.setText("하차 정류장 : " + busData_fast[2] + "\n버스 번호 : " + busData_fast[3]);
                                    if (busData_fast[0].equals("1")) {
                                        String[] busServiceData = get_api.getBusServiceData(noticeData.getCityCode(), noticeData.getRouteId(), "1").split("\n");
                                        for (int j = 0; j < busServiceData.length; j++) {
                                            String[] busSD_List = busServiceData[j].split(" ");
                                            if (noticeData.getVehicleno().equals(busSD_List[2])) {
                                                nowNodeOrd = busSD_List[1];
                                                break;
                                            }
                                        }
                                        if (nowNodeOrd.equals(Integer.toString(Integer.parseInt(eNodeord) - 1))) {
                                            busRide = false;
                                            Handler handler = new Handler(Looper.getMainLooper());
                                            handler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    tts.speak("잠시 후 목적지에 도착할 예정입니다. 하차를 준비해주세요.", TextToSpeech.QUEUE_ADD, null);
                                                }
                                            }, 0);

                                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                            vibrator.vibrate(new long[]{500, 500, 500, 500, 500, 500, 500, 500, 500, 500}, -1);

                                    /*
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        pendingIntent = PendingIntent.getBroadcast(blind_wait.this, 0, my_intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                                    } else {
                                        pendingIntent = PendingIntent.getBroadcast(blind_wait.this, 0, my_intent, PendingIntent.FLAG_UPDATE_CURRENT);
                                    }

                                    if (Build.VERSION.SDK_INT >= 23) {
                                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
                                    } else {
                                        alarmManager.set(AlarmManager.RTC_WAKEUP, 0, pendingIntent);
                                    }
                                    */
                                            while (true) {
                                                if (!tts.isSpeaking()) {
                                                    break;
                                                }
                                            }
                                            database.getReference("Notice_api").child(Integer.toString(notice_pos)).removeValue();
                                            Intent finish_intent = new Intent(blind_wait.this, blind_main.class);
                                            startActivity(finish_intent);
                                            finish();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };

        timer.schedule(timerTask, 10*1000, 10*1000);
    }
}
