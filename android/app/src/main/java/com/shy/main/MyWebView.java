package com.shy.main;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.KeyEvent;
import android.util.AttributeSet;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ConsoleMessage;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.seer.common.Logger;
import java.io.File;
import java.io.FileInputStream;

public class MyWebView extends WebView {
    public MyWebView(Context context)
    {
        super(context);
    }
    public void loadSettings()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        final MainActivity main = (MainActivity) getContext();
        WebSettings webSettings = this.getSettings();
        webSettings.setNeedInitialFocus(false);
        // 支持JavaScript
        webSettings.setJavaScriptEnabled(true);
        // 支持缩放
        webSettings.setBuiltInZoomControls(false);
        webSettings.setSupportZoom(false);
        webSettings.setDisplayZoomControls(false);
        // 支持保存数据
        webSettings.setSaveFormData(true);
        webSettings.setDomStorageEnabled(true);
        //允许webview对文件的操作
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        if(Build.VERSION.SDK_INT>= 16)
        {
            webSettings.setAllowFileAccessFromFileURLs(true);
        }
        // 设置网页自动适配
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {//小于4.4（不包括4.4）用这个
            webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        }
        // 这个是设置WebView是否支持ViewPort属性，
        // ViewPort是在html中配置，主要为了适配各种屏幕,如果html中有配置这个属性，那么即使不设置这个属性也是会适配屏幕的
        // 注意：当html中有这个属性时，WebView设置缩放属性是不起作用的
        webSettings.setUseWideViewPort(true);
        // 当html中没有配置ViewPort这个属性时，同时还需要设置下面这个属性才能适配屏幕
        webSettings.setLoadWithOverviewMode(true);
        // 设置
        this.setWebViewClient(new WebViewClient()
        {
            /** 开始载入页面 */
            @Override
            public void onPageStarted(WebView view,String url,Bitmap favicon)
            {
                main.setProgressBarIndeterminateVisibility(true);// 设置标题栏的滚动条开始
                main.browserUrl = url;
                super.onPageStarted(view, url, favicon);
            }
            /** 捕获点击事件 */
            public boolean shouldOverrideUrlLoading(WebView view,String url)
            {
                MyWebView.this.loadUrl(url);
                return true;
            }
            /** 错误返回 */
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            /** 页面载入完毕 */
            @Override
            public void onPageFinished(WebView view, String url) {
                main.setProgressBarIndeterminateVisibility(false);// 设置标题栏的滚动条停止
                super.onPageFinished(view, url);
            }
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = null;
                try {
                    String Url = request.getUrl().getPath();
                    if (Url.indexOf("user") > -1)
                    {
                        // 重新构造WebResourceResponse 将数据已流的方式传入
                        // 根据网络资源,找到本地资源的File
                        File file = new File(Url);
                        FileInputStream input = new FileInputStream(file);
                        response = new WebResourceResponse(MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(Url)), "UTF-8", input);
                    } else {
                        return super.shouldInterceptRequest(view, request);
                    }
                } catch (Exception e) {
                    return super.shouldInterceptRequest(view, request);
                }
                return response;
            }
        });
        this.setWebChromeClient(new WebChromeClient()
        {
            /** 设置进度条 */
            public void onProgressChanged(WebView view,int newProgress)
            {
                // 设置标题栏的进度条的百分比
                // WebViewApp.this.getWindow().setFeatureInt(   Window.FEATURE_PROGRESS, newProgress * 100);
                //super.onProgressChanged(view, newProgress);
            }
            /** 设置标题 */
            public void onReceivedTitle(WebView view, String title) {
                // WebViewApp.this.setTitle(title);
                // super.onReceivedTitle(view, title);
            }
            /*android 低版本 Desperate*/
            @Override
            public void onConsoleMessage(String message, int lineNumber,String sourceID)
            {
                Logger.log.i("[console]"+message + "(" +sourceID  + ":" + lineNumber+")");
                super.onConsoleMessage(message, lineNumber, sourceID);
            }
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage)
            {
                Logger.log.i("[console]"+ "["+consoleMessage.messageLevel()+"] "+ consoleMessage.message() + "(" +consoleMessage.sourceId()  + ":" + consoleMessage.lineNumber()+")");
                if(
                        consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.DEBUG
                                ||
                                consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                                ||
                                consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING
                )
                {
                    Logger.log.e(consoleMessage.message());
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return new BaseInputConnection(this, false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        MainActivity main = (MainActivity) getContext();
        main.keyResponse(event.getAction(), event.getKeyCode());
        return super.dispatchKeyEvent(event);
    }


}
