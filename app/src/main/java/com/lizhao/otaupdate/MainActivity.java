package com.lizhao.otaupdate;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RecoverySystem;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView ShowVersion;
    private Button CheckUpdate;
    private URL mUrl;
    private String mVersion;
    private SAXBuilder mSAXBuilder;
    private Element mRootElement;
    private long DownloadedLen = 0;
    private long FileLen = 0;
    CountDownLatch OnclikCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ShowVersion = (TextView) findViewById(R.id.VersionShow);
        mVersion = Build.DISPLAY.toString().substring(10);
        ShowVersion.setText("当前版本:" + mVersion);

        CheckUpdate = (Button) findViewById(R.id.CheckUpdate);
        CheckUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mUrl = new URL("http://172.16.3.48:8080/update/update.xml");
                } catch (MalformedURLException e) {
                    Log.d("lizhaodelog","update.xml文件不存在");
                    e.printStackTrace();
                }


                String ReturnVersion = null;
                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/update.xml";
                try {
                    ReturnVersion = ReturnUpdateVersion(mUrl,path);
                    if (ReturnVersion.compareTo(mVersion) > 0){
                        Toast.makeText(MainActivity.this,"存在新版本:" + ReturnVersion,Toast.LENGTH_SHORT).show();

                        String ReturnPath = ReturnUpdatePath();
                        Toast.makeText(MainActivity.this,"开始下载升级包,请不要进行其他操作",Toast.LENGTH_SHORT).show();

                        OnclikCount = new CountDownLatch(1);
                        StartDown(ReturnPath);
                        OnclikCount.await();

                        Toast.makeText(MainActivity.this,"下载完成,开始进入 recovery",Toast.LENGTH_SHORT).show();
                        RecoverySystem.installPackage(MainActivity.this,new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/update.zip"));
                    }
                } catch (JDOMException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }


    public String ReturnUpdateVersion(URL url, final String path) throws JDOMException, IOException, InterruptedException {

        final CountDownLatch count = new CountDownLatch(1);
        OkHttpClient mOkHttpClient = new OkHttpClient();
        Request mRequest = new Request.Builder().url(url).build();
        mOkHttpClient.newCall(mRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("lizhaode","网络连接失败,请检查网络");
                count.countDown();
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()){
                    throw new IOException("获取xml文件失败,response code:" + response.code());
                }

                byte[] buff = new byte[2048];
                int len;
                InputStream mInputStream = response.body().byteStream();
                File file = new File(path);
                if (file.exists()){
                    file.delete();
                    file.createNewFile();
                }else {
                    file.createNewFile();
                }
                FileOutputStream mFileOutStream = new FileOutputStream(file);
                while ((len = mInputStream.read(buff)) != -1){
                    mFileOutStream.write(buff,0,len);
                }
                mFileOutStream.flush();
                mFileOutStream.close();
                mInputStream.close();
                count.countDown();
            }
        });


        count.await();
        File file = new File(path);
        mSAXBuilder = new SAXBuilder();
        Document mDocument = mSAXBuilder.build(file);
        mRootElement = mDocument.getRootElement();

        return mRootElement.getChildText("version");
    }

    public String ReturnUpdatePath(){
        return mRootElement.getChildText("path");
    }

    public void StartDown(String url) throws IOException, InterruptedException {

        OkHttpClient mOkHttpClient = new OkHttpClient();
        Request mRequest = new Request.Builder().url(url).build();
        mOkHttpClient.newCall(mRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("lizhaode", "连接 zip 包失败");
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("下载zip 包连接失败,response code:" + response.code());
                }
                FileLen = response.body().contentLength();
                Log.d("lizhaode","update.zip 文件大小:" + String.valueOf(FileLen/1024/1024) + "MB");

                byte[] buff = new byte[2048];
                int len;
                InputStream mInputStream = response.body().byteStream();
                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/update.zip";
                File file = new File(path);
                if (file.exists()) {
                    file.delete();
                    file.createNewFile();
                } else {
                    file.createNewFile();
                }

                FileOutputStream mFileOutputStream = new FileOutputStream(file);
                while ((len = mInputStream.read(buff)) != -1) {
                    mFileOutputStream.write(buff, 0, len);
                    DownloadedLen += len;
                }
                mFileOutputStream.flush();
                mFileOutputStream.close();
                mInputStream.close();
                OnclikCount.countDown();
            }
        });
    }
}
