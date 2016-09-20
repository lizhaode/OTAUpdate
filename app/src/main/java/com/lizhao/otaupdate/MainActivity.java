package com.lizhao.otaupdate;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RecoverySystem;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView ShowVersion;
    private Button CheckUpdate;
    private ProgressBar mProgressBar;
    private OkHttpClient mOkHttpClient = new OkHttpClient();
    private String CurrentVersion;
    private String LastVersion;
    private String logTag = "lizhaode";
    private int progress;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message message){
            switch (message.what){
                case 1:
                    Toast.makeText(MainActivity.this,"下载完成,开始进入 recovery",Toast.LENGTH_SHORT).show();
                    try {
                        RecoverySystem.installPackage(MainActivity.this,new File("/data/update.zip"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case 2:
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(progress);
                    break;
                case 3:
                    Toast.makeText(MainActivity.this,"您的IMEI不在数据库中",Toast.LENGTH_LONG).show();
                    CheckUpdate.setEnabled(true);
                    break;
                case 4:
                    Toast.makeText(MainActivity.this,"您的IMEI在禁止升级范围",Toast.LENGTH_LONG).show();
                    CheckUpdate.setEnabled(true);
                    break;
                case 5:
                    Toast.makeText(MainActivity.this, String.format("存在新版本:%s",LastVersion),Toast.LENGTH_SHORT).show();
                    Toast.makeText(MainActivity.this, "开始下载升级包,请不要进行其他操作", Toast.LENGTH_SHORT).show();
                    break;
                case 6:
                    Toast.makeText(MainActivity.this,"未发现新版本",Toast.LENGTH_SHORT).show();
                    CheckUpdate.setEnabled(true);
                    break;

            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CheckUpdate = (Button) findViewById(R.id.CheckUpdate);
        mProgressBar = (ProgressBar) findViewById(R.id.DownProgress);
        ShowVersion = (TextView) findViewById(R.id.VersionShow);
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        CurrentVersion = Build.DISPLAY.substring(10);
        ShowVersion.setText(String.format("当前版本:%s",CurrentVersion));
        //获取IMEI
        TelephonyManager mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        final String imei = mTelephonyManager.getDeviceId();
        if (imei == null){
            Toast.makeText(MainActivity.this,"手机的IMEI号是空",Toast.LENGTH_SHORT).show();
            CheckUpdate.setEnabled(false);
        }

        CheckUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckUpdate.setEnabled(false);
                try {
                    URL versionurl = new URL("http://172.16.3.48:8081/getlastversion");
                    final URL downurl = new URL("http://172.16.3.48:8081/getdownfile");
                    RequestBody mRequestBody = new FormBody.Builder().add("imei",imei).build();
                    Request mRequest = new Request.Builder().post(mRequestBody).url(versionurl).build();
                    mOkHttpClient.newCall(mRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {

                            if (response.code() == 404) {
                                throw new IOException(String.format("请求缺少IMEI参数,response code:%s", response.code()));
                            } else if (response.code() == 405) {
                                Message message = new Message();
                                message.what = 3;
                                handler.sendMessage(message);
                                throw new IOException(String.format("IMEI不在数据库中,response code:%d",response.code()));
                            } else if (!response.isSuccessful()) {
                                throw new IOException(String.format("请求版本时网络异常,response code:%s", response.code()));
                            }
                            //IMEI被禁止处理
                            LastVersion = response.body().string();
                            Log.d(logTag,String.format("最新版本号:%s",LastVersion));
                            if (LastVersion.equals("IMEIForbidden")) {
                                Message message = new Message();
                                message.what = 4;
                                handler.sendMessage(message);
                                throw new IOException(String.format("IMEI被禁止,response code:%d",response.code()));
                            }
                            if (LastVersion.compareTo(CurrentVersion) > 0) {
                                Message message = new Message();
                                message.what = 5;
                                handler.sendMessage(message);

                                //开始下载
                                Request mRequest = new Request.Builder().header("version", LastVersion).url(downurl).build();
                                mOkHttpClient.newCall(mRequest).enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        e.printStackTrace();
                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        if (response.code() == 404) {
                                            throw new IOException(String.format("指定的版本号错误:response code:%s", response.code()));
                                        } else if (!response.isSuccessful()) {
                                            throw new IOException(String.format("下载文件时网络异常,response code:%s", response.code()));
                                        }
                                        //获取下载文件大小,为进度条做准备
                                        long filelength = response.body().contentLength() / 1024 / 1024;
                                        Log.d(logTag, String.format("文件大小:%dMB", filelength));
                                        //设置/data分区为zip存储目录
                                        String filepath = String.format("%s/update.zip", Environment.getDataDirectory().getCanonicalPath());
                                        Log.d(logTag, String.format("zip文件路径:%s", filepath));
                                        File file = new File(filepath);
                                        if (file.exists()) {
                                            file.delete();
                                            file.createNewFile();
                                        } else {
                                            file.createNewFile();
                                        }
                                        //使用文件流保存请求到的文件
                                        byte[] buffer = new byte[104857600];
                                        int len;
                                        InputStream mInputStream = response.body().byteStream();
                                        FileOutputStream mFileOutputStream = new FileOutputStream(file);
                                        long DownloadLenght = 0;
                                        while ((len = mInputStream.read(buffer)) != -1) {
                                            mFileOutputStream.write(buffer, 0, len);
                                            DownloadLenght += len;
                                            progress = (int) (DownloadLenght * 100 / 1024 / 1024 / filelength);
                                            Message message = new Message();
                                            message.what = 2;
                                            handler.sendMessage(message);
                                        }
                                        mFileOutputStream.flush();
                                        mFileOutputStream.close();
                                        mInputStream.close();

                                        Message message = new Message();
                                        message.what = 1;
                                        handler.sendMessage(message);


                                    }
                                });
                            } else {
                                Log.d(logTag,"手机当前版本高于服务器最新版本");
                                Message message = new Message();
                                message.what = 6;
                                handler.sendMessage(message);
                            }

                        }
                    });
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}


