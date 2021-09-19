package com.shy.main;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.annotation.SuppressLint;
import android.webkit.JavascriptInterface;
import com.alibaba.fastjson.JSONObject;
import com.github.druk.rx2dnssd.Rx2Dnssd;
import com.github.druk.rx2dnssd.Rx2DnssdBindable;
import com.seer.agv.AgvTcpFactory;
import com.seer.common.Logger;
import com.seer.map.SmapIO;
import com.seer.server.HttpServer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * https://www.123si.org/android/article/android-file-directory-and-permissions/
 */
public class MainActivity extends AppCompatActivity {
    private MyWebView webView;
    private ArrayList list;
    /**
     * 先别按 B
     * 当前url地址
     */
    public String browserUrl = "file:///android_asset/index.html";
    public HttpServer server=new HttpServer();
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION};
    private static int REQUEST_PERMISSION_CODE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Logger.log.activity=this;
        Logger.operateLog.activity=this;
        server.createServer(this);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        //弹出
        webView = (MyWebView) findViewById(R.id.web_view);
        webView.loadSettings();
        webView.addJavascriptInterface(new JSInterface(),"android_background");
        // 本地文件处理(如果文件名中有空格需要用+来替代) B QQ
        //browserUrl = "http://192.168.15.176:10000/#/main";
        //browserUrl = "http://192.168.3.234:8080/#/main";
        //browserUrl="http://192.168.3.245:8080/#/main";
        webView.loadUrl(browserUrl);
        //webView.loadUrl(server.GetViewUrl());
        statusBarHide();
    }
    /**
     * 设置Activity的statusBar隐藏
     */
    public void statusBarHide() {
        Activity activity = this;
        ActionBar actionBar = getSupportActionBar();
        if (actionBar!= null)
        {
            actionBar.hide(); //隐藏标题栏
        }
        // 代表 5.0 及以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = activity.getWindow().getDecorView();
            int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(option);
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            return;
        }
        // versionCode > 4.4  and versionCode < 5.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }
    /**
     * https://segmentfault.com/a/1190000016087103
     */
    private void response(String flowId, String msg)
    {
        if (Build.VERSION.SDK_INT >= 19)
        {
            if (this.webView!= null)
                this.webView.evaluateJavascript("android_background_response(" + flowId + "," + msg + ");", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s)
                    {
                        // 上述定义函数执行完成时，return 的内容
                        //Log.d("LogName", s); // Prints: "this"
                    }
                });
        } else {
            this.webView.loadUrl("javascript:android_background_response(" + flowId + "," + msg + ");");
        }
    }
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            response(String.valueOf(msg.what),msg.obj.toString());
        }
    };
    /**
     * 控车的tcp连接,主要用于用户控车调用
     */
    private AgvTcpFactory controlAgvClient = new AgvTcpFactory("control");
    /**
     * 查询车的连接，主要用于系统内部定时查询车的状态
     */
    private AgvTcpFactory searchAgvClient = new AgvTcpFactory("search");
    private final class JSInterface {
        @SuppressLint("JavascriptInterface")
        @JavascriptInterface
        public String post(final String flowId, final String url, final String data) {
            try {
                switch (url) {
                    case "/apk/info":
                        JSONObject json=new JSONObject();
                        json.put("version",BuildConfig.VERSION_NAME);
                        return  json.toJSONString();
                    case "/agv/control":
                        controlAgvClient.request(flowId, data, handler);
                        break;
                    case "/agv/search":
                        searchAgvClient.request(flowId, data, handler);
                        break;
                    case "/agv/disconnect":
                        controlAgvClient.remove(flowId, data, handler);
                        searchAgvClient.remove(flowId, data, handler);
                        return "{}";
                    case "/search/agvs":
                        startBrowse(flowId);
                        break;
                    case "/search/agvs/close":
                        stopBrowse();
                        return "{}";
                    case "/pull/map":
                        new Thread(new Runnable()
                        {
                            @Override
                            public void run() {
                                try {
                                    Looper.prepare();//增加部分
                                    JSONObject object = JSONObject.parseObject(data);
                                    SmapIO io = new SmapIO();
                                    File dir = MainActivity.this.getFilesDir();
                                    JSONObject da = JSONObject.parseObject(object.getString("data"));
                                    String host = object.getString("host");
                                    io.pullMap(data,
                                            dir.getAbsolutePath() + File.separator + host.replace(".", "_") + File.separator + da.getString("map_name") + ".png",
                                            da.getInteger("width"),
                                            da.getInteger("height"),
                                            flowId,
                                            handler
                                    );
                                    Looper.loop();//增加部分
                                }
                                catch (Exception ex)
                                {
                                    Logger.log.e(ex.toString());
                                    System.out.println(ex.toString());
                                }
                            }
                        }).start();
                        break;
                }
            } catch (Exception err) {
                Logger.log.e(err.toString());
                JSONObject json = new JSONObject();
                json.put("error", err.toString());
                return json.toJSONString();
            }
            return "";
        }
    }
    public String StringStick1X;
    public String StringStick1Y;
    public String StringStick2X;
    public String StringStick2Y;
    public static Map<String, Object> map = new HashMap<>();
    public void keyResponse(int eventName,int msg)
    {
        if (Build.VERSION.SDK_INT >= 19)
        {
            this.webView.evaluateJavascript("android_background_key_response(" + eventName + "," + msg + ");", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s)
                {
                    // 上述定义函数执行完成时，return 的内容
                    //Log.d("LogName", s); // Prints: "this"
                }
            });
        } else {
            this.webView.loadUrl("javascript:android_background_key_response(" + eventName + "," + msg + ");");
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
//        int eventSource = event.getSource();
//        if ((((eventSource & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
//                ((eventSource & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK))
//                && event.getAction() == MotionEvent.ACTION_MOVE) {
//            // 连接的设备
//            final int historySize = event.getHistorySize();
//            for (int i = 0; i < historySize; i++) {
//                processJoystickInput(event, i);
//            }
//            //遥感和按键处理机制
//            processJoystickInput(event, -1);
//        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        CheckKeyDown(keyCode, event);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        webView.requestFocus();
        // button.requestFocus();
        CheckKeyUp(keyCode, event);
        return true;
    }

    public void CheckKeyDown(int keyCode, KeyEvent event) {
        map.put(keyCode + "", keyCode);
        org.json.JSONObject jsonObject = new org.json.JSONObject(map);
        //keyResponse("keydown",jsonObject.toString());
    }

    public void CheckKeyUp(int keyCode, KeyEvent event) {
        map.remove(keyCode + "");
        org.json.JSONObject jsonObject = new org.json.JSONObject(map);
        //keyResponse("keyup",jsonObject.toString());
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {
        InputDevice mInputDevice;
        mInputDevice = event.getDevice();
        float x1 = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_X, historyPos);
        float x2 = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Z, historyPos);
        float y1 = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Y, historyPos);
        float y2 = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_RZ, historyPos);
        StringStick1X = Float.toString(x1);
        StringStick1Y = Float.toString(y1);
        StringStick2X = Float.toString(x2);
        StringStick2Y = Float.toString(y2);
        map.put("StringStick1X", StringStick1X);
        map.put("StringStick1Y", StringStick1Y);
        map.put("StringStick2X", StringStick2X);
        map.put("StringStick2Y", StringStick2Y);
    }

    //建立摇杆坐标轴
    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device,
                                         int axis,
                                         int historyPos) {
        final InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range != null) {
            final float flat = range.getFlat();
            final float value = historyPos < 0 ? event.getAxisValue(axis)
                    : event.getHistoricalAxisValue(axis, historyPos);
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private Disposable browseDisposable;

    /**
     * https://github.com/andriydruk/RxDNSSD/tree/ffca26857b31f6fd6bec3bda505969e7bd808891
     * https://docs.seer-group.com/robokit_netprotocol/688127
     *
     * @param flowId
     */
    private void startBrowse(String flowId) {
        if (browseDisposable != null) {
            browseDisposable.dispose();
            browseDisposable = null;
        }
//       Log.i("TAG", "start browse");
        list = new ArrayList();
        //Rx2Dnssd rxDnssd = new Rx2DnssdEmbedded(this);
        Rx2Dnssd rxDnssd = new Rx2DnssdBindable(this);
        String serviceType = "_robokitV2._tcp";
        // serviceType = "_http._tcp";
        try {
            browseDisposable = rxDnssd.browse(serviceType, "local.")
                    .compose(rxDnssd.resolve())//_ni-logos._tcp
                    .compose(rxDnssd.queryRecords())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bonjourService -> {
                                // Log.d("TAG", bonjourService.toString());
                                Map map = new HashMap();
                                if (bonjourService.isLost()) {
                                    map.remove("name");
                                    map.remove("ip");
                                    map.remove("code");
                                } else {
                                    map.put("name", bonjourService.getServiceName());
                                    map.put("ip", bonjourService.getInet4Address());
                                    map.put("code", bonjourService.getPort());
                                }
                                list.add(map);
                                Message message = new Message();
                                message.what = Integer.valueOf(flowId);
                                JSONObject json = new JSONObject();
                                json.put("list", list);
                                message.obj = json.toJSONString();
                                // Log.v("---------------", list.toString());
                                handler.sendMessage(message);
                            },
                            throwable -> {
                                Log.e("TAG", "error", throwable);
                                Logger.log.e(throwable.toString());
                            }
                    );
        } catch (Exception ex) {
            Log.e("TAG", ex.toString());
            Logger.log.e(ex.toString());
        }
    }
    private void stopBrowse()
    {
        if (browseDisposable != null) {
            browseDisposable.dispose();
            browseDisposable = null;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_PERMISSION_CODE);
            }
        }
    }
}
