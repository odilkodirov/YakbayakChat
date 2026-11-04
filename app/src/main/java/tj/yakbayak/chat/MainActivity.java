package tj.yakbayak.chat;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.view.ViewGroup;
import android.content.Context;

public class MainActivity extends Activity {
  private WebView web;
  private PermissionRequest pendingRequest;
  private static final int MIC_REQUEST = 41;
  private static final String CHANNEL_ID = "yakbayak_chat_messages";

  public class NativeBridge {
    @JavascriptInterface public void notify(String title, String message) {
      runOnUiThread(() -> showMessageNotification(title, message));
    }
  }

  private void showMessageNotification(String title, String message) {
    NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Сообщения Якбаяк CHAT", NotificationManager.IMPORTANCE_HIGH);
      channel.setDescription("Уведомления о новых сообщениях");
      nm.createNotificationChannel(channel);
    }
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
    builder.setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title == null ? "Якбаяк CHAT" : title)
      .setContentText(message == null ? "Новое сообщение" : message)
      .setAutoCancel(true).setContentIntent(pi).setPriority(Notification.PRIORITY_HIGH);
    nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), builder.build());
  }

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(0xFF142D4E);
    web = new WebView(this);
    web.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    web.addJavascriptInterface(new NativeBridge(), "YakbayakNative");
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
          boolean wantsAudio = false;
          for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
          if (wantsAudio && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRequest = request;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
          } else request.grant(request.getResources());
        });
      }
      @Override public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
        fileCallback = callback;
        Intent intent = params.createIntent();
        try { startActivityForResult(intent, 42); } catch (Exception e) { fileCallback = null; return false; }
        return true;
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }
  private android.webkit.ValueCallback<Uri[]> fileCallback;
  @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 42 && fileCallback != null) {
      Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
      fileCallback.onReceiveValue(result); fileCallback = null;
    }
  }
  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (requestCode == MIC_REQUEST && pendingRequest != null) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pendingRequest.grant(pendingRequest.getResources());
      else pendingRequest.deny();
      pendingRequest = null;
    }
  }
  @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
