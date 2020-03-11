package com.yisan.audiodemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import com.yisan.audiodemo.recorder.ByteRecorderActivity;
import com.yisan.audiodemo.recorder.FileRecorderActivity;

/**
 * Android系统录音api
 */
public class MainActivity extends AppCompatActivity {

    private TextView mTvToFile, mTvToByte;

    private static final int REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvToFile = findViewById(R.id.tv_to_file);
        mTvToByte = findViewById(R.id.tv_to_byte);

        mTvToFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileRecorderActivity.show(MainActivity.this);
            }
        });

        mTvToByte.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ByteRecorderActivity.show(MainActivity.this);
            }
        });


        //请求录音动态权限、读写文件
        int check = PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (check != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(MainActivity.this, "获取权限成功", Toast.LENGTH_SHORT).show();

            }
        }
    }
}
