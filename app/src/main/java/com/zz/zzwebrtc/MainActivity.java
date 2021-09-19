package com.zz.zzwebrtc;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.webrtc.Logging;

public class MainActivity extends AppCompatActivity {
    EditText et_room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FormatStrategy formatStrategy = PrettyFormatStrategy.newBuilder()
                .showThreadInfo(false)  // 是否显示线程信息，默认为ture
                .methodCount(3)         // 显示的方法行数，默认为2
                .methodOffset(7)        // 隐藏内部方法调用到偏移量，默认为5
                //.logStrategy(customLog) // 更改要打印的日志策略。
                //.tag("quarkboom")   // 每个日志的全局标记。默认PRETTY_LOGGER
                .build();
        Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy));
        Logger.addLogAdapter(new AndroidLogAdapter() {
            @Override
            public boolean isLoggable(int priority, String tag) {
                return true; //BuildConfig.DEBUG 注意这里不能引用Logger中的BuildConfig，要引用自己项目的，true时打印log路径点击跳转
            }
        });


        //日志保存到本地sd卡，保存位置为：
        //                String diskPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        //                String folder = diskPath + File.separatorChar + "logger";
//        Logger.addLogAdapter(new DiskLogAdapter());

//        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);

        et_room = findViewById(R.id.et_room);
    }


    public void JoinRoom(View view) {
        WebRtcManager.getInstance().connect(this, et_room.getText().toString());
    }
}