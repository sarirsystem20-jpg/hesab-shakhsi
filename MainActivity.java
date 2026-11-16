package ir.hesabshakhsi.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int CREATE_BACKUP = 4101;
    private static final int OPEN_BACKUP = 4102;
    private WebView webView;
    private String pendingBackupJson;
    private ValueCallback<Uri[]> filePathCallback;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, OPEN_BACKUP); }
                catch (ActivityNotFoundException e) { filePathCallback = null; Toast.makeText(MainActivity.this, "انتخاب فایل در این گوشی در دسترس نیست.", Toast.LENGTH_LONG).show(); return false; }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface public void saveBackup(String json) {
            runOnUiThread(() -> {
                pendingBackupJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "hesab-shakhsi-backup.json");
                try { startActivityForResult(intent, CREATE_BACKUP); }
                catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "ذخیره فایل در این گوشی در دسترس نیست.", Toast.LENGTH_LONG).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_BACKUP) {
            if (filePathCallback != null) {
                Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
        } else if (requestCode == CREATE_BACKUP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupJson != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new Exception("no output stream");
                    out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Toast.makeText(this, "فایل پشتیبان ذخیره شد.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "ذخیره پشتیبان انجام نشد.", Toast.LENGTH_LONG).show();
                }
            }
            pendingBackupJson = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("(function(){if(typeof home==='function'){home();return 'home'}return 'none'})()", value -> {
            if ("\"none\"".equals(value) && webView.canGoBack()) webView.goBack();
        });
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        if (webView != null) { webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); }
        super.onDestroy();
    }
}
