package com.audio.duanyy;

import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.audio.duanyy.codec.AudioCodec;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void btnStartCodec(View view) {

        if (isSDCardExit()){
            Log.d(TAG,Environment.getExternalStorageDirectory()+"");
            File rootDirectory = Environment.getExternalStorageDirectory();
            File[] files = rootDirectory.listFiles();
            for (File file:files){
                Log.d(TAG,file.getAbsolutePath());
            }
        }

        String srcPath = "/storage/emulated/0/sample.mp4";
        String dstPath = "/storage/emulated/0/sample_AAC.aac";

        AudioCodec audioCodec = AudioCodec.getInstance(srcPath, dstPath, MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.setmOnAudioCodecListener(
                        new AudioCodec.OnAudioCodecListener() {
                            @Override
                            public void onComplete() {
                                MainActivity.this.runOnUiThread(
                                        new Thread(){
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"转码成功！！！",Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                );
                            }
                        }
                );
        audioCodec.prepare();
        audioCodec.start();

    }

    private boolean isSDCardExit(){
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

}
