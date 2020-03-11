package com.yisan.audiodemo.recorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yisan.audiodemo.R;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件模式录音
 */
public class FileRecorderActivity extends AppCompatActivity {


    private static final String TAG = "FileRecorderActivity";

    private TextView mTvRecorderFile;
    private TextView mTvDesc;
    private TextView mTvPlay;
    /**
     * 线程池
     */
    private ExecutorService executorService;
    private MediaRecorder mediaRecorder;
    private File audioFile;
    private long startRecorderTime, stopRecorderTime;
    /**
     * 住线程Handler
     */
    private Handler handler = new Handler(Looper.getMainLooper());

    /**
     * 主线程和子线程、保持变量内存同步
     */
    private volatile boolean isPlaying = false;
    /**
     * 播放声音
     */
    private MediaPlayer mediaPlayer;

    public static void show(Context context) {
        context.startActivity(new Intent(context, FileRecorderActivity.class));
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_recorder);

        mTvDesc = findViewById(R.id.tv_desc);
        mTvRecorderFile = findViewById(R.id.tv_recorder_file);
        mTvPlay = findViewById(R.id.tv_recorder_play);


        //单线程、Android系统录音的api是线程不安全的,多少线程调用jni的录音方法会发生奔溃。所以用单线程
        executorService = Executors.newSingleThreadExecutor();

        //按下说话，释放发送，所以我们不要OnClickListener
        mTvRecorderFile.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        //开始录音
                        startRecorder();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        //停止录音
                        stopRecorder();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        mTvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!isPlaying && audioFile != null) {
                    //修改播放状态
                    isPlaying = true;
                    mTvPlay.setText("正在播放........");
                    //在后台操作耗时事务
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
     */
    private void startRecorder() {
        //改变UI
        mTvRecorderFile.setText("正在录音");
        mTvRecorderFile.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        //交给后台录音、执行录音逻辑
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前的 recorder
                releaseRecorder();
                //执行录音逻辑
                if (!doStart()) {
                    recorderFail();
                }

            }
        });
    }

    /**
     * 停止录音
     */
    private void stopRecorder() {
        //改变UI
        mTvRecorderFile.setText("按住说话");
        mTvRecorderFile.setBackgroundColor(getResources().getColor(R.color.colorAccent));
        //执行后台任务，执行停止逻辑
        if (!doStop()) {
            recorderFail();
        }
        //释放Recorder
        releaseRecorder();
    }


    /**
     * 开始录音
     *
     * @return
     */
    private boolean doStart() {

        try {
            //创建MediaRecorder
            mediaRecorder = new MediaRecorder();
            //创建录音文件
            audioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/audioDemo/"
                    + System.currentTimeMillis() + ".m4a");
            //6.0以上需要动态读写权限
            audioFile.getParentFile().mkdirs();
            boolean result = audioFile.createNewFile();
            if (!result) {
                Log.e(TAG, "audioFile make dir fail !! ");
            }
            //配置MediaRecorder
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//从麦克风获取音频数据
            //把文件保存为MP4格式
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //设置录音频率、所有Android系统都支持的频率
            mediaRecorder.setAudioSamplingRate(44100);
            //设置通用的AAC编码格式
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            //音质比较好的频率
            mediaRecorder.setAudioEncodingBitRate(96000);
            //设置录音文件保存的位置
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());

            //开始录音
            mediaRecorder.prepare();
            mediaRecorder.start();

            //记录开始录音的时间，用于统计时长
            startRecorderTime = System.currentTimeMillis();
            //录音成功
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            recorderFail();
            stopRecorder();
            return false;
        }
        return true;
    }


    /**
     * 停止录音
     *
     * @return boolean
     */
    private boolean doStop() {
        try {
            //停止录音
            mediaRecorder.stop();
            //记录停止时间，统计时长
            stopRecorderTime = System.currentTimeMillis();
            //只接受超过3秒的录音，在UI上显示出来
            final int second = (int) (stopRecorderTime - startRecorderTime) / 1000;
            if (second > 3) {
                //在主线程改UI，显示出来
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvDesc.setText("录音成功 " + second + " 秒");
                    }
                });
            }

        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    /**
     * 录音失败
     */
    private void recorderFail() {
        audioFile = null;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileRecorderActivity.this, "录音错误", Toast.LENGTH_SHORT).show();
            }
        });

    }


    /**
     * 释放录音
     */
    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }


    /**
     * 播放录音
     */
    private void doPlay(File audioFile) {

        try {
            //配置MediaPlayer播放器
            mediaPlayer = new MediaPlayer();
            //设置声音的文件
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());

            //设置监听回调
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {

                    stopPlay();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {

                    playFail();
                    stopPlay();

                    return true;
                }
            });

            //设置音量是否循环
            mediaPlayer.setVolume(1, 1);
            mediaPlayer.setLooping(false);

            //准备开始
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
            //异常处理，防止闪退
            playFail();
            stopPlay();
        }

    }

    /**
     * 播放错误
     */
    private void playFail() {
        isPlaying = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileRecorderActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
                mTvPlay.setText("播放");
            }
        });

    }


    /**
     * 停止播放
     */
    private void stopPlay() {
        //重置播放状态
        isPlaying = false;
        //释放播放器
        if (mediaPlayer != null) {
            //释放监听器，防止内存泄漏
            mediaPlayer.setOnCompletionListener(null);
            mediaPlayer.setOnErrorListener(null);

            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                mTvPlay.setText("播放");
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        //销毁线程池、避免内存泄漏
        executorService.shutdownNow();
        executorService = null;

        releaseRecorder();


        stopPlay();
    }

}
