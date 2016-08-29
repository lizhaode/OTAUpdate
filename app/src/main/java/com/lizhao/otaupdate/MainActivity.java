package com.lizhao.otaupdate;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private TextView ShowVersion;
    private Button CheckUpdate;
    private URL mUrl;
    private String mVersion;
    private String mDownloadService = Context.DOWNLOAD_SERVICE;
    private DownloadManager mDownloadManager;
    private DownloadManager.Request mRequest;
    private DownloadManager.Query mQuery;
    private Cursor mCursor;
    private SAXReader mSAXReader;
    private Element mRootElement;

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
                    Log.d("lizhao","update.xml文件不存在");
                    e.printStackTrace();
                }

                try {
                    String ReturnVersion = ReturnUpdateVersion(mUrl);
                    if (ReturnVersion.compareTo(mVersion) > 0){
                        Toast.makeText(MainActivity.this,"存在新版本:" + ReturnVersion,Toast.LENGTH_SHORT).show();

                        String ReturnPath = ReturnUpdatePath();
                        Toast.makeText(MainActivity.this,"开始下载升级包,进度可在通知栏查看",Toast.LENGTH_SHORT).show();

                        long id = StartDown(ReturnPath);
                        boolean bool = IsDownComplete(id);
                        if (bool){
                            Toast.makeText(MainActivity.this,"下载完成已检测到",Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (DocumentException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public String ReturnUpdateVersion(URL url) throws DocumentException {
        mSAXReader = new SAXReader();
        Document mDocument = mSAXReader.read(url);
        mRootElement = mDocument.getRootElement();

        return mRootElement.elementText("version");
    }

    public String ReturnUpdatePath(){
        return mRootElement.elementText("path");
    }

    public long StartDown(String url){
        mRequest = new DownloadManager.Request(Uri.parse(url));
        mRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        mRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        mRequest.setTitle("OTA下载");
        mRequest.setDescription("下载 OTA 升级包中...");
        mRequest.setDestinationInExternalPublicDir("","update.zip");
        mDownloadManager = (DownloadManager) getSystemService(mDownloadService);
        long id = mDownloadManager.enqueue(mRequest);

        return id;
    }


    public boolean IsDownComplete(long id){
        mDownloadManager = (DownloadManager) getSystemService(mDownloadService);
        mQuery = new DownloadManager.Query().setFilterById(id);
        mCursor = mDownloadManager.query(mQuery);
        if (mCursor != null && mCursor.moveToFirst()){
            int status = mCursor.getInt(mCursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            while (true){
                if (status == DownloadManager.STATUS_SUCCESSFUL){
                    break;
                }
                return true;
            }
        }
        return false;
    }
}
