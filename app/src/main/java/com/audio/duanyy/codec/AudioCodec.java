package com.audio.duanyy.codec;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by duanyy on 2016/8/27.
 * 此类实现了音频数据的解码、编码功能
 *
 *  getInstance();
 *  prepare();
 *  start();
 */
public class AudioCodec {

    private static AudioCodec mAudioCodec;
    private static final String TAG = "AudioCodec";

    private String srcPath;
    private String dstPath;
    private String mimeType;

    private FileOutputStream mFos;
    private BufferedOutputStream mBos;

    private MediaCodec mAudioDecoder;
    private MediaCodec mAudioEncoder;

    private MediaExtractor mMediaExtrator;

    private ByteBuffer[] mDecodeInputBuffers;
    private ByteBuffer[] mDecodeOutputBuffers;
    private ByteBuffer[] mEncodeInputBuffers;
    private ByteBuffer[] mEncodeOutputBuffers;

    private List<byte[]> mChunkPCMDataContainer;

    private MediaCodec.BufferInfo mDecodeBufferInfo;
    private MediaCodec.BufferInfo mEncodeBufferInfo;

    private OnAudioCodecListener mOnAudioCodecListener;

    private long fileTotlaSize;
    private long decodeSize;


    private AudioCodec(String srcFile,String dstFile,String mimeType){
        this.srcPath = srcFile;
        this.dstPath = dstFile;
        this.mimeType = mimeType;
    }

    public static AudioCodec getInstance(String srcFile,String dstFile,String mimeType){
        if (mAudioCodec == null){
            mAudioCodec = new AudioCodec(srcFile,dstFile,mimeType);
        }
        return mAudioCodec;
    }

    public void setmOnAudioCodecListener(OnAudioCodecListener mOnAudioCodecListener) {
        this.mOnAudioCodecListener = mOnAudioCodecListener;
    }

    //准备工作
    public void prepare(){

        mChunkPCMDataContainer = new ArrayList<>();

        try {
            File file2 = new File(dstPath);
            if (file2.exists()){
                file2.delete();
            }
            mFos = new FileOutputStream(dstPath);
            mBos = new BufferedOutputStream(mFos);
            File file = new File(srcPath);
            fileTotlaSize = file.length();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        initAudioDecoder();
        initAudioAACEncoder();
    }

    //1.初始化解码器
    private void initAudioDecoder(){

        mMediaExtrator = new MediaExtractor();
        try {
            mMediaExtrator.setDataSource(srcPath);
            int trackCount = mMediaExtrator.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mMediaExtrator.getTrackFormat(i);
                trackFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE,44100);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video")){
//                    int frameRate = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
//                    Log.d(TAG,"frameRate: "+frameRate);
                    Log.d(TAG,"mimetype:"+mime);
                }
                if (mime.startsWith("audio"));{
                    mMediaExtrator.selectTrack(i);
                    mAudioDecoder = MediaCodec.createDecoderByType(mime);
//                    Bundle params = new Bundle();
//                    params.putInt(MediaCodec.K);
//                    mAudioDecoder.setParameters(params);
                    //TODO 第四个参数为什么传 0 ？
                    mAudioDecoder.configure(trackFormat,null,null,0);
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioDecoder == null) {
            Log.d(TAG,"音频解码器初始化失败！");
        }

        mAudioDecoder.start();

        //Call this after start() returns.
        mDecodeInputBuffers = mAudioDecoder.getInputBuffers();

        //Call this after start() returns and whenever dequeueOutputBuffer signals an output buffer change by returning INFO_OUTPUT_BUFFERS_CHANGED
        mDecodeOutputBuffers = mAudioDecoder.getOutputBuffers();
        mDecodeBufferInfo = new MediaCodec.BufferInfo();

    }


    //2.初始化编码器
    private void initAudioAACEncoder(){
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(mimeType,44100,1);//mime类型、采样率、声道数
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,256000);//设置比特率
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_OUT_MONO);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);//TODO 2设置的什么属性？
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,100*1024);// unit:byte

        try {
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mAudioEncoder.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAudioEncoder == null){
            Log.d(TAG,"音频编码器初始化失败！");
            return;
        }

        mAudioEncoder.start();
        mEncodeInputBuffers = mAudioEncoder.getInputBuffers();
        mEncodeOutputBuffers = mAudioEncoder.getOutputBuffers();
        mEncodeBufferInfo = new MediaCodec.BufferInfo();

    }

    private boolean codeOver = false;

    //3.开启线程，启动转码
    public void start(){

        Log.d(TAG,"start work!");
        new Thread(new DecodeRunnable()).start();
        new Thread(new EncodeRunnable()).start();

    }

    /**
     * 解码线程
     */
    private class DecodeRunnable implements Runnable{
        @Override
        public void run() {
            while (!codeOver){
                srcAudioFormat2PCM();
            }
        }
    }

    private class EncodeRunnable implements Runnable{
        long startTime = System.currentTimeMillis();
        @Override
        public void run() {
            while (!codeOver || !mChunkPCMDataContainer.isEmpty()){
                dstAudioFormatFromPCM();

            }
            if (mOnAudioCodecListener != null) {
                mOnAudioCodecListener.onComplete();
            }
            Log.d(TAG,"end work!  srcSize:"+fileTotlaSize+", dstSize:"+decodeSize+", time: "+(System.currentTimeMillis()-startTime));
        }
    }

    /**
     * 将源文件中的音频数据转换成PCM原始数据
     * 在线程中调用此方法，不停将解码出来的数据放入 mChunkPCMDataContainer 原始数据暂存集合
     * 步骤：1.解码；2.输出
     */
    private void srcAudioFormat2PCM(){

        int length = mDecodeInputBuffers.length-1;
        for (int i = 0; i < length; i++) {
            int intputIndex = mAudioDecoder.dequeueInputBuffer(-1);
            //检查是否编码完成
            if (intputIndex < 0){
                codeOver = true;
                return;
            }
            ByteBuffer inputBuffer = mDecodeInputBuffers[intputIndex];
            inputBuffer.clear();
            int sampleSize = mMediaExtrator.readSampleData(inputBuffer, 0);
            if (sampleSize < 0){
                codeOver = true;
            }else {
                mAudioDecoder.queueInputBuffer(intputIndex,0,sampleSize,0,0);//通知解码器对刚才传入的数据块进行解码。利用index标识是哪一个缓冲数据块
                mMediaExtrator.advance();
                decodeSize += sampleSize;
                Log.d(TAG,"解码器queueInputBuffer...");
            }
        }

        int outputIndex = mAudioDecoder.dequeueOutputBuffer(mDecodeBufferInfo, 10000);//出队一块缓冲数据
        ByteBuffer outputBuffer;
        byte[] chunkPCM;
        while (outputIndex >= 0){
            outputBuffer = mAudioDecoder.getOutputBuffer(outputIndex);
            chunkPCM = new byte[mDecodeBufferInfo.size];
            outputBuffer.get(chunkPCM);//将缓冲区中的数据读出，并存入字节数组中。
            outputBuffer.clear();
            putPCMData(chunkPCM);
            mAudioDecoder.releaseOutputBuffer(outputIndex,false);//释放当前缓冲区
            outputIndex = mAudioDecoder.dequeueOutputBuffer(mDecodeBufferInfo,10000);//再次出队数据，如果没有数据输出则返回-1，即循环结束
            Log.d(TAG,"解码器dequeueOutputBuffer...");
        }
    }

    /**
     * 从PCM原始数据暂存区内取出数据，并利用编码器进行编码
     * 步骤：1.输入；2.编码；3.编码后的数据输出到文件
     */
    private void dstAudioFormatFromPCM(){
        byte[] chunkPCM;
        int inputIndex;ByteBuffer inputBuffer;ByteBuffer outputBuffer;byte[] chunkAudio;int outBitSize;int outPacketSize;
        int length = mEncodeInputBuffers.length;
        for (int i = 0; i < length; i++) {
            chunkPCM = getPCMData();
            if (chunkPCM == null){
                break;
            }
            inputIndex = mAudioEncoder.dequeueInputBuffer(-1);
            inputBuffer = mEncodeInputBuffers[inputIndex];
            inputBuffer.clear();
            inputBuffer.limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);
            mAudioEncoder.queueInputBuffer(inputIndex,0,chunkPCM.length,0,0);//通知编码器，对刚才输入的数据进行编码
            Log.d(TAG,"编码器queueInputBuffer...");
        }
        int outputIndex = mAudioEncoder.dequeueOutputBuffer(mEncodeBufferInfo, 10000);
        while (outputIndex >= 0){
            outBitSize = mEncodeBufferInfo.size;
            outPacketSize = outBitSize + 7;
            outputBuffer = mEncodeOutputBuffers[outputIndex];
            outputBuffer.position(mEncodeBufferInfo.offset);
            outputBuffer.limit(mEncodeBufferInfo.offset + outBitSize);
            chunkAudio = new byte[outPacketSize];
            addADTStoPacket(chunkAudio,outPacketSize);
            outputBuffer.get(chunkAudio,7,outBitSize);
            outputBuffer.position(mEncodeBufferInfo.offset);
            try {
                mBos.write(chunkAudio,0,chunkAudio.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAudioEncoder.releaseOutputBuffer(outputIndex,false);
            outputIndex = mAudioEncoder.dequeueOutputBuffer(mEncodeBufferInfo,10000);
            Log.d(TAG,"编码器dequeueOutputBuffer...");
        }
    }

    private void putPCMData(byte[] pcmChunk){

        synchronized (AudioCodec.class){
            mChunkPCMDataContainer.add(pcmChunk);
        }

    }

    private byte[] getPCMData(){

        synchronized (AudioCodec.class){
            if (mChunkPCMDataContainer.isEmpty()){
                return null;
            }
            byte[] pcmData = mChunkPCMDataContainer.get(0);
            mChunkPCMDataContainer.remove(pcmData);
            return pcmData;
        }

    }

    /**
     * 添加ADTS头
     * @param packet
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz  采样率samplerate
        int chanCfg = 1; // CPE   声道配置

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public interface OnAudioCodecListener{
        void onComplete();
    }

}
