package com.audio.duanyy;

import android.content.Intent;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.audio.duanyy.codec.AudioCodec;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView mSrcTextView;
    private TextView mDstTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView(){
        mSrcTextView = (TextView) findViewById(R.id.textview_src);
        mDstTextView = (TextView) findViewById(R.id.textview_dst);
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

//        String srcPath = "/storage/emulated/0/sample_zxysgnz.mp3";
//        String dstPath = "/storage/emulated/0/sample_zxysgnz.aac";
        String srcPath = "/storage/emulated/0/sample2.mp4";
        String dstPath = "/storage/emulated/0/sample10.aac";

        mSrcTextView.setText(srcPath);
        mDstTextView.setText(dstPath);

        AudioCodec audioCodec = AudioCodec.getInstance(srcPath, dstPath, MediaFormat.MIMETYPE_AUDIO_AAC);
        audioCodec.setmOnAudioCodecListener(
                        new AudioCodec.OnAudioCodecListener() {
                            @Override
                            public void onComplete() {
                                MainActivity.this.runOnUiThread(
                                        new Thread(){
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"音频转码成功！！！",Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                );
                            }
                        }
                );
        audioCodec.prepare();
        audioCodec.start();

    }



    public void btnStartDecodeVideo(View view) {

        String savePath = "/storage/emulated/0/videoDecoded";
        String videoPath = "/storage/emulated/0/sample2.mp4";

        VideoDecoder videoDecoder = new VideoDecoder();
        try {
            videoDecoder.setSaveFrames(savePath,2);
            videoDecoder.videoDecodePrepare(videoPath);
            videoDecoder.excuate();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
//            videoDecoder.close();
        }

    }


    private boolean isSDCardExit(){
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public void btn2SoftInputSurfaceAct(View view) {
        Intent intent = new Intent(this,SoftInputSurfaceActivity.class);
        startActivity(intent);
    }
}
