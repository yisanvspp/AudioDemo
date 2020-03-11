package com.yisan.audiodemo.recorder;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.yisan.audiodemo.R;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 字节模式录音
 */
public class ByteRecorderActivity extends AppCompatActivity {
    private static final String TAG = "ByteRecorderActivity";

    private TextView tvDesc_;
    private TextView tvRecorderByte;

    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService;

    //录音状态,volatile保证内存同步，避免出现问题
    private volatile boolean isRecordering = false;

    private long startRecorderTime, stopRecorderTime;
    private File audioFile;
    private byte[] buffer;
    public static final int BUFFER_SIZE = 2048;
    private FileOutputStream fos;
    private AudioRecord audioRecord;
    private TextView tvPlay;

    /**
     * 必须使用volatile保证主线程和子线程改变变量时候、一致。
     */
    private volatile boolean isPlaying = false;
    private AudioTrack audioTrack;

    public static void show(Context context) {
        context.startActivity(new Intent(context, ByteRecorderActivity.class));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_byte_recorder);

        tvDesc_ = findViewById(R.id.tv_desc_);
        tvRecorderByte = findViewById(R.id.tv_recorder_byte);
        tvPlay = findViewById(R.id.tv_play);

        //读写音频数据的缓冲区
        buffer = new byte[BUFFER_SIZE];
        //线程池单线程（不能使用多线程）、防止播放的Jni函数奔溃
        executorService = Executors.newSingleThreadExecutor();

        //开始、停止录制
        tvRecorderByte.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //根据当前状态，改变UI执行开始、停止录音的逻辑
                if (isRecordering) {
                    //改变UI状态
                    tvRecorderByte.setText("开始录音");
                    //修改录音状态
                    isRecordering = false;
                } else {
                    //改变UI状态
                    tvRecorderByte.setText("停止录音");
                    //修改录音状态
                    isRecordering = true;
                    //放到后台执行耗时操作
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            //开始录音
                            if (!startRecorder()) {
                                //录音失败
                                recorderFail();
                            }
                        }
                    });
                }
            }
        });

        //播放录音
        tvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (audioFile != null && !isPlaying) {
                    //播放状态
                    isPlaying = true;
                    //修改UI
                    tvPlay.setText("正在播放");
                    //后台处理
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            //播放录音
                            doPlay(audioFile);
                        }
                    });
                }
            }
        });
    }


    /**
     * 开始录音
     *
     * @return boolean
     */
    private boolean startRecorder() {
        try {
            //创建录音文件
            audioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/audioDemo/"
                    + System.currentTimeMillis() + ".pcm");
            audioFile.getParentFile().mkdirs();
            boolean result = audioFile.createNewFile();
            if (!result) {
                Log.e(TAG, "audioFile make dir fail !! ");
            }
            //创建文件输出流
            fos = new FileOutputStream(audioFile);
            //配置AudioRecorder
            //从麦克风采集
            int audioSource = MediaRecorder.AudioSource.MIC;
            //所有安卓系统都支持的频率
            int sampleRate = 44100;
            //单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            //pcm 16是所有安卓系统都支持的
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            //最小的缓存数据
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            //buffer不能小于最低要求，也不能小于我们每次读取的大小
            audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat,
                    Math.max(minBufferSize, BUFFER_SIZE));

            //开始录音
            audioRecord.startRecording();
            //记录开始录音时间，用于统计时长
            startRecorderTime = System.currentTimeMillis();
            //循环读取数据，写入输入流中
            while (isRecordering) {
                //只要还在录音状态，就一直读取数据
                int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                if (read > 0) {
                    fos.write(buffer, 0, read);
                } else {
                    //读取失败，返回false提示用户
                    return false;
                }
            }
            //退出循环、停止录音、释放资源
            return stopRecorder();

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            recorderFail();
            return false;
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
        }
    }


    /**
     * 停止录音
     */
    private boolean stopRecorder() {
        try {
            isRecordering = false;
            //停止录音，关闭文件输出流
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            fos.close();
            //记录结束时间，统计录音时长
            stopRecorderTime = System.currentTimeMillis();
            //大于3秒才算成功，在主线程改变UI显示
            final int second = (int) ((stopRecorderTime - startRecorderTime) / 1000);
            if (second > 3) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvDesc_.setText("录音成功 " + second + " 秒");
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * 录音失败
     */
    private void recorderFail() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ByteRecorderActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
                isRecordering = false;
                tvRecorderByte.setText("开始录音");
            }
        });
    }


    /**
     * 播放录音
     */
    private void doPlay(File audioFile) {
        //配置播放器
        //音乐类型，扬声器播放
        int streamType = AudioManager.STREAM_MUSIC;
        //录音时财通的采样频率，所以播放时候使用相同的采样频率
        int sampleRate = 44100;
        //MoNO表示单声道，录音输入单声道
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        //录音时使用16bit，所有播放时使用相同的格式
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        //流模式
        int mode = AudioTrack.MODE_STREAM;
        //计算最小buffer大小
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        //构造AudioTrack
        audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                Math.max(minBufferSize, BUFFER_SIZE), mode);

        //从文件流读取数据
        FileInputStream is = null;

        try {
            //循环读数据，写到播放器去播放
            int read;
            is = new FileInputStream(audioFile);
            while ((read = is.read(buffer)) > 0) {
                int ret = audioTrack.write(buffer, 0, read);
                //检查write返回值，错误处理
                switch (ret) {
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioTrack.ERROR_DEAD_OBJECT:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
            audioTrack.play();

        } catch (Exception e) {
            e.printStackTrace();
            //错误处理，防止闪退
            playFail();

        } finally {
            isPlaying = false;
            if (is != null) {
                closeQuietly(is);
            }
            //释放播放器
            if (audioFile != null) {
                resetQuietly(audioTrack);
            }


        }

    }

    /**
     * 关闭资源
     *
     */
    private void closeQuietly(FileInputStream fileInputStream) {
        try {
            fileInputStream.close();
            fileInputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放资源
     */
    private void resetQuietly(AudioTrack audioTrack) {
        try {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    tvPlay.setText("播放");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 播放录音失败
     */
    private void playFail() {
        audioFile = null;
        //播放状态
        isPlaying = false;

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ByteRecorderActivity.this, "播放错误", Toast.LENGTH_SHORT).show();
                //修改UI状态
                tvPlay.setText("播放");
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (audioTrack!=null){
            resetQuietly(audioTrack);
        }


        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }



    }
}
