package local.codex.voyahcameralab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private CameraManager manager;private HandlerThread captureThread;private Handler capture,main;
  private TextureView texture;private TextView notice,recordTime,viewName;private CameraButton recordButton,stopButton,settingsButton,viewButton;
  private SharedPreferences preferences;private AlertDialog settingsDialog;
  private final CaptureGate gate=new CaptureGate();
  private final FrameHealth health=new FrameHealth();private final RootAvmController avm=new RootAvmController();
  private final ExecutorService controlWorker=Executors.newSingleThreadExecutor();private Future<?> viewRequest;
  private int selectedView,appliedView;private boolean switching,stallReported;private String viewError="";
  private CameraDevice camera;private CameraCaptureSession session;private GlCameraRenderer renderer;
  private volatile boolean resumed;private boolean opening,recording,recordStarting,recordStopping,autoPreview=true,autoRecord,autoClose,settingsVisible;
  private boolean cameraOnly=true;private int layout,activeToken;private long frames,recordStartedAt;
  private String selectedId="1",cameraId="",detail="",message="Подключение камеры…";private Uri lastVideo;
  private String[] cameraIds=new String[0];private JSONObject inventory;
  private final ExecutorService inventoryWorker=Executors.newSingleThreadExecutor();
  private final Runnable frameWatch=new Runnable(){public void run(){
    if(!opening)return;
    if(health.stalled(SystemClock.elapsedRealtime())){
      if(!stallReported){stallReported=true;if(recording)stopRecording();setStatus("Нет новых кадров. Ожидаем видеопоток…");}
      updateUi();
    }
    main.postDelayed(this,1000);
  }};
  private final Runnable recordClock=new Runnable(){public void run(){
    if(!recording)return;long seconds=(SystemClock.elapsedRealtime()-recordStartedAt)/1000;
    recordTime.setText(String.format(Locale.ROOT,"●  %02d:%02d",seconds/60,seconds%60));main.postDelayed(this,500);
  }};

  @Override public void onCreate(Bundle state){
    super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    preferences=getSharedPreferences("camera",MODE_PRIVATE);selectedId=preferences.getString("camera_id","1");layout=preferences.getInt("layout",0);cameraOnly=preferences.getBoolean("camera_only",true);selectedView=preferences.getInt("view",0);
    String savedVideo=preferences.getString("last_video",null);if(savedVideo!=null)lastVideo=Uri.parse(savedVideo);
    manager=(CameraManager)getSystemService(CAMERA_SERVICE);main=new Handler(Looper.getMainLooper());
    captureThread=new HandlerThread("VoyahCameraCapture");captureThread.start();capture=new Handler(captureThread.getLooper());
    readIntent(getIntent());buildUi();
  }
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private TextView label(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.WHITE);view.setTextSize(size);view.setPadding(dp(8),dp(6),dp(8),dp(6));return view;}
  private GradientDrawable pill(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
  private void buildUi(){
    FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
    texture=new TextureView(this);texture.setId(R.id.camera_preview);root.addView(texture,new FrameLayout.LayoutParams(-1,-1));
    notice=label(message,18);notice.setId(R.id.status_text);notice.setGravity(Gravity.CENTER);notice.setBackground(pill(0xB0181B20,12));
    FrameLayout.LayoutParams noticeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);noticeParams.leftMargin=dp(24);noticeParams.rightMargin=dp(24);root.addView(notice,noticeParams);
    notice.setOnClickListener(v->{if(!opening){autoPreview=false;startPreview();}});
    recordTime=label("",16);recordTime.setTextColor(0xFFFF5B55);recordTime.setBackground(pill(0xB0181B20,18));
    FrameLayout.LayoutParams timeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);timeParams.topMargin=dp(20);timeParams.leftMargin=dp(24);root.addView(recordTime,timeParams);
    viewButton=new CameraButton(this,CameraButton.SWITCH,"Переключить переднюю и заднюю камеры");viewButton.setId(R.id.switch_view_button);
    FrameLayout.LayoutParams viewParams=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.TOP|Gravity.LEFT);viewParams.topMargin=dp(16);viewParams.leftMargin=dp(24);root.addView(viewButton,viewParams);viewButton.setOnClickListener(v->requestView(appliedView==1?2:1,true));
    viewName=label("360",13);FrameLayout.LayoutParams nameParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);nameParams.topMargin=dp(29);nameParams.leftMargin=dp(86);root.addView(viewName,nameParams);
    timeParams.topMargin=dp(80);
    settingsButton=new CameraButton(this,CameraButton.GEAR,"Настройки");settingsButton.setId(R.id.settings_button);
    FrameLayout.LayoutParams gearParams=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.TOP|Gravity.RIGHT);gearParams.topMargin=dp(16);gearParams.rightMargin=dp(24);root.addView(settingsButton,gearParams);settingsButton.setOnClickListener(v->showSettings());
    LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setOrientation(LinearLayout.HORIZONTAL);
    recordButton=new CameraButton(this,CameraButton.RECORD,"Начать запись");recordButton.setId(R.id.record_button);controls.addView(recordButton,new LinearLayout.LayoutParams(dp(64),dp(64)));recordButton.setOnClickListener(v->startRecording());
    stopButton=new CameraButton(this,CameraButton.STOP,"Остановить запись");stopButton.setId(R.id.stop_button);LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(dp(64),dp(64));stopParams.leftMargin=dp(20);controls.addView(stopButton,stopParams);stopButton.setOnClickListener(v->stopRecording());
    FrameLayout.LayoutParams controlsParams=new FrameLayout.LayoutParams(-2,dp(64),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);controlsParams.bottomMargin=dp(20);root.addView(controls,controlsParams);
    root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
      Rect visible=new Rect();root.getWindowVisibleDisplayFrame(visible);int[] origin=new int[2];root.getLocationOnScreen(origin);
      int insetBottom=Math.max(0,origin[1]+root.getHeight()-visible.bottom),insetRight=Math.max(0,origin[0]+root.getWidth()-visible.right);
      int newBottom=dp(20)+insetBottom,newRight=dp(24)+insetRight;
      if(controlsParams.bottomMargin!=newBottom){controlsParams.bottomMargin=newBottom;controls.setLayoutParams(controlsParams);}
      if(gearParams.rightMargin!=newRight){gearParams.rightMargin=newRight;settingsButton.setLayoutParams(gearParams);}
    });
    setContentView(root);updateUi();
    texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
      public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){maybeAutoStart();}
      public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int w,int h){capture.post(()->{if(renderer!=null)renderer.resize(w,h);});}
      public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){closeCamera("Окно закрыто");capture.post(surface::release);return false;}
      public void onSurfaceTextureUpdated(SurfaceTexture surface){}
    });
  }
  private void readIntent(Intent intent){
    autoPreview=intent.getBooleanExtra("auto_preview",true)||intent.getBooleanExtra("auto_record",false);
    autoRecord=intent.getBooleanExtra("auto_record",preferences.getBoolean("auto_record",false));autoClose=intent.getBooleanExtra("auto_close",false);
    String requested=intent.getStringExtra("camera_id");if(requested!=null)selectedId=requested;
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);closeCamera("Подключение камеры…");setIntent(intent);readIntent(intent);refreshInventory();}
  @Override protected void onResume(){super.onResume();resumed=true;autoPreview=true;autoRecord=getIntent().getBooleanExtra("auto_record",preferences.getBoolean("auto_record",false));if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);else refreshInventory();}
  @Override protected void onPause(){super.onPause();}
  @Override protected void onStop(){resumed=false;autoPreview=false;if(viewRequest!=null)viewRequest.cancel(true);closeCamera("Камера освобождена");super.onStop();}
  @Override protected void onDestroy(){gate.cancel();inventoryWorker.shutdownNow();controlWorker.shutdownNow();main.removeCallbacks(recordClock);main.removeCallbacks(frameWatch);if(settingsDialog!=null)settingsDialog.dismiss();capture.post(()->{stopInternal();captureThread.quitSafely();});super.onDestroy();}
  @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){autoPreview=true;refreshInventory();}else setStatus("Разрешите доступ к камере в настройках Android");}
  private void refreshInventory(){
    try{cameraIds=manager.getCameraIdList();if(!Arrays.asList(cameraIds).contains(selectedId)){selectedId=Arrays.asList(cameraIds).contains("1")?"1":(cameraIds.length>0?cameraIds[0]:"");}maybeAutoStart();}
    catch(Exception error){setStatus("Камера недоступна: "+error.getMessage());}
    inventoryWorker.execute(()->{try{JSONObject result=DeviceInventory.read(this,manager);writeLocal("inventory.json",result.toString(2));main.post(()->{if(!isDestroyed())inventory=result;});}catch(Exception error){android.util.Log.w("VoyahCameraLab","Inventory",error);}});
  }
  private void maybeAutoStart(){if(autoPreview&&resumed&&!settingsVisible&&texture.isAvailable()&&!selectedId.isEmpty()&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){autoPreview=false;startPreview();}}
  private void startPreview(){
    if(!resumed||!texture.isAvailable()||opening||selectedId.isEmpty())return;
    if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){setStatus("Нужно разрешение на камеру");return;}
    cameraId=selectedId;String id=cameraId;boolean useAvm="1".equals(id),only=cameraOnly;int chosenLayout=layout;
    SurfaceTexture output=texture.getSurfaceTexture();int width=texture.getWidth(),height=texture.getHeight();
    int token=gate.begin();activeToken=token;opening=true;frames=0;health.reset();stallReported=false;appliedView=0;setStatus("Подключение камеры…");
    main.removeCallbacks(frameWatch);main.postDelayed(frameWatch,1000);
    if(useAvm&&selectedView>0)requestView(selectedView,false);
    capture.post(()->{
      if(!gate.accepts(token)||!resumed)return;
      try{
        CameraCharacteristics characteristics=manager.getCameraCharacteristics(id);Size size=DeviceInventory.selectSize(characteristics,id);
        Integer orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);FramePolicy policy=new FramePolicy(size.getWidth(),size.getHeight(),orientation==null?0:orientation,useAvm,chosenLayout,only);
        detail=size.getWidth()+"×"+size.getHeight()+" → "+policy.outputWidth+"×"+policy.outputHeight;
        renderer=new GlCameraRenderer(this,capture,output,width,height,size.getWidth(),size.getHeight(),policy,new GlCameraRenderer.Listener(){
          public void onFrame(long count){main.post(()->{if(!gate.accepts(token))return;frames=count;health.frame(SystemClock.elapsedRealtime());stallReported=false;if(autoRecord&&count>=3&&!switching){autoRecord=false;startRecording();}setStatus("Вход "+id+" · "+detail+" · кадров "+count);});}
          public void onError(String error){cameraFailure(token,error);}
          public void onRecordingFinished(Uri uri,String error){main.post(()->{
            if(uri!=null){lastVideo=uri;preferences.edit().putString("last_video",uri.toString()).apply();}
            if(!gate.accepts(token))return;
            recording=false;recordStarting=false;recordStopping=false;main.removeCallbacks(recordClock);
            if(uri!=null){setStatus("Видео сохранено");Toast.makeText(MainActivity.this,"Видео сохранено",Toast.LENGTH_SHORT).show();if(autoClose)closeCamera("Камера освобождена");}
            else{setStatus("Не удалось сохранить запись");Toast.makeText(MainActivity.this,"Не удалось сохранить запись: "+error,Toast.LENGTH_LONG).show();}
            updateUi();
          });}
        });
        manager.openCamera(id,new CameraDevice.StateCallback(){
          @Override public void onOpened(CameraDevice device){
            if(!gate.accepts(token)||!resumed){device.close();return;}camera=device;
            try{final GlCameraRenderer current=renderer;if(current==null){device.close();return;}
              device.createCaptureSession(Collections.singletonList(current.cameraSurface),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession configured){
                  if(!gate.accepts(token)||camera!=device){configured.close();return;}session=configured;
                  try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(current.cameraSurface);configured.setRepeatingRequest(request.build(),null,capture);}
                  catch(Exception error){cameraFailure(token,error.toString());}
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed){failed.close();cameraFailure(token,"Не удалось открыть видеопоток");}
              },capture);
            }catch(Exception error){cameraFailure(token,error.toString());}
          }
          @Override public void onDisconnected(CameraDevice device){device.close();cameraFailure(token,"Камера занята другим приложением");}
          @Override public void onError(CameraDevice device,int error){device.close();cameraFailure(token,"Камера недоступна ("+error+")");}
        },capture);
      }catch(Exception error){cameraFailure(token,error.toString());}
    });
    main.postDelayed(()->{if(gate.accepts(token)&&frames==0)closeCamera("Нет видеопотока. Настройки — справа сверху.");},8000);
  }
  private void cameraFailure(int token,String error){main.post(()->{if(gate.accepts(token))closeCamera(error+". Коснитесь для подключения.");});}
  private void startRecording(){
    if(!opening||recording||recordStarting||recordStopping||switching||health.stalled(SystemClock.elapsedRealtime())||frames<1)return;
    if(getExternalFilesDir(null)!=null&&getExternalFilesDir(null).getUsableSpace()<128L*1024*1024){Toast.makeText(this,"Недостаточно свободного места",Toast.LENGTH_LONG).show();return;}
    int token=activeToken;String id=cameraId;recordStarting=true;updateUi();
    capture.post(()->{
      if(!gate.accepts(token)||renderer==null)return;
      try{renderer.startRecording(id);main.post(()->{if(gate.accepts(token)){recordStarting=false;recording=true;recordStartedAt=SystemClock.elapsedRealtime();updateUi();main.removeCallbacks(recordClock);recordClock.run();setStatus("Запись начата");}});}
      catch(Exception error){main.post(()->{if(!gate.accepts(token))return;recordStarting=false;updateUi();Toast.makeText(this,"Не удалось начать запись: "+error.getMessage(),Toast.LENGTH_LONG).show();});}
    });
  }
  private void stopRecording(){
    if(!recording||recordStopping)return;int token=activeToken;recordStopping=true;updateUi();
    capture.post(()->{if(gate.accepts(token)&&renderer!=null)renderer.stopRecording();});
  }
  private void stopInternal(){
    if(session!=null){try{session.stopRepeating();}catch(Exception ignored){}session.close();session=null;}
    if(camera!=null){camera.close();camera=null;}
    if(renderer!=null){renderer.close();renderer=null;}
  }
  private void closeCamera(String reason){gate.cancel();opening=false;autoRecord=false;recording=false;recordStarting=false;recordStopping=false;switching=false;frames=0;health.reset();main.removeCallbacks(recordClock);main.removeCallbacks(frameWatch);capture.post(this::stopInternal);setStatus(reason);}
  private void requestView(int view,boolean persist){
    if(switching||!opening||!"1".equals(cameraId)||(view!=1&&view!=2))return;
    int token=activeToken;switching=true;viewError="";updateUi();
    viewRequest=controlWorker.submit(()->{
      if(!gate.accepts(token))return;
      try{avm.select(this,view);main.post(()->{if(!gate.accepts(token))return;switching=false;appliedView=view;if(persist){selectedView=view;preferences.edit().putInt("view",view).apply();}setStatus("Выбран ракурс: "+(view==1?"передняя":"задняя"));});}
      catch(Exception error){main.post(()->{if(!gate.accepts(token))return;switching=false;autoRecord=false;viewError=error.getMessage()==null?error.toString():error.getMessage();setStatus("Ракурс не изменён: "+viewError);Toast.makeText(this,viewError,Toast.LENGTH_LONG).show();});}
    });
  }
  private void updateUi(){
    if(recordButton==null)return;boolean stalled=health.stalled(SystemClock.elapsedRealtime());recordButton.setEnabled(opening&&frames>0&&!stalled&&!switching&&!recording&&!recordStarting&&!recordStopping);stopButton.setEnabled(recording&&!recordStopping);
    viewButton.setEnabled(opening&&frames>0&&!switching&&"1".equals(cameraId));viewName.setText(switching?"…":appliedView==1?"Передняя":appliedView==2?"Задняя":"360");
    recordTime.setVisibility(recording||recordStarting?View.VISIBLE:View.GONE);if(recordStarting)recordTime.setText("●  Подготовка");else if(recordStopping)recordTime.setText("●  Сохранение");
    notice.setVisibility(opening&&frames>0&&!stalled?View.GONE:View.VISIBLE);notice.setText(stalled?"Нет новых кадров. Ожидаем видеопоток…":message);
  }
  private void setStatus(String value){
    message=value;updateUi();android.util.Log.i("VoyahCameraLab",value);
    try{JSONObject state=new JSONObject();state.put("message",value);state.put("cameraId",cameraId);state.put("selectedId",selectedId);state.put("cameraOnly",cameraOnly);state.put("layout",layout);state.put("selectedView",selectedView);state.put("appliedView",appliedView);state.put("switchingView",switching);state.put("viewError",viewError);state.put("frameAgeMs",health.age(SystemClock.elapsedRealtime()));state.put("streamStalled",health.stalled(SystemClock.elapsedRealtime()));state.put("autoRecordOnOpen",preferences.getBoolean("auto_record",false));state.put("frames",frames);state.put("opening",opening);state.put("recording",recording);state.put("settingsVisible",settingsVisible);state.put("lastVideo",lastVideo==null?JSONObject.NULL:lastVideo.toString());state.put("updatedMs",System.currentTimeMillis());writeLocal("state.json",state.toString(2));}catch(Exception ignored){}
  }
  private void writeLocal(String name,String value)throws Exception{File directory=getExternalFilesDir(null);if(directory==null)directory=getFilesDir();try(FileOutputStream stream=new FileOutputStream(new File(directory,name))){stream.write(value.getBytes(StandardCharsets.UTF_8));}}
  private Button settingsAction(LinearLayout panel,String text,int id,Runnable action){Button button=new Button(this);button.setText(text);if(id!=0)button.setId(id);panel.addView(button,new LinearLayout.LayoutParams(-1,dp(48)));button.setOnClickListener(v->action.run());return button;}
  private void showSettings(){
    if(settingsVisible)return;settingsVisible=true;
    ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(8));form.setBackgroundColor(0xFF181B20);scroll.addView(form);
    form.addView(label("Видеовход",16));Spinner source=new Spinner(this);source.setId(R.id.camera_picker);String[] ids=cameraIds.clone();String[] names=new String[ids.length];for(int i=0;i<ids.length;i++)names[i]=ids[i]+("1".equals(ids[i])?" · Камеры 360":" · Видеовход");source.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));form.addView(source,new LinearLayout.LayoutParams(-1,dp(48)));for(int i=0;i<ids.length;i++)if(ids[i].equals(selectedId))source.setSelection(i);
    form.addView(label("Компоновка источника",16));Spinner composition=new Spinner(this);composition.setId(R.id.avm_layout);composition.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Обычный 360","Парковщик"}));composition.setSelection(layout);form.addView(composition,new LinearLayout.LayoutParams(-1,dp(48)));
    CheckBox only=new CheckBox(this);only.setId(R.id.crop_checkbox);only.setText("Только вид камеры — без вида сверху");only.setTextColor(Color.WHITE);only.setChecked(cameraOnly);form.addView(only);
    CheckBox automatic=new CheckBox(this);automatic.setId(R.id.auto_record_checkbox);automatic.setText("Начинать запись при открытии");automatic.setTextColor(Color.WHITE);automatic.setChecked(preferences.getBoolean("auto_record",false));form.addView(automatic);
    form.addView(label("Ракурс 360 при открытии",16));Spinner viewPicker=new Spinner(this);viewPicker.setId(R.id.view_picker);viewPicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Текущий — не переключать","Передняя камера","Задняя камера"}));viewPicker.setSelection(selectedView);form.addView(viewPicker,new LinearLayout.LayoutParams(-1,dp(48)));
    form.addView(label("Переключение перед/зад использует имеющийся доступ su. Без него доступен текущий ракурс 360.\nВидео: Movies/VoyahCameraLab",13));
    TextView diagnostic=label("Вход "+selectedId+" · "+detail,12);form.addView(diagnostic);
    settingsAction(form,"Переподключить камеру",R.id.preview_button,()->{settingsDialog.dismiss();closeCamera("Подключение камеры…");startPreview();});
    settingsAction(form,"Освободить камеру",R.id.close_camera_button,()->{settingsDialog.dismiss();closeCamera("Камера освобождена. Коснитесь для подключения.");});
    Button video=settingsAction(form,"Последняя запись",R.id.video_button,()->{settingsDialog.dismiss();viewVideo();});video.setEnabled(lastVideo!=null);
    settingsAction(form,"Сохранить технический отчёт",R.id.report_button,this::exportReport);
    settingsDialog=new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Настройки").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Применить",(dialog,which)->{
      String next=ids.length>0?ids[Math.max(0,source.getSelectedItemPosition())]:selectedId;boolean changed=!next.equals(selectedId)||layout!=composition.getSelectedItemPosition()||cameraOnly!=only.isChecked()||selectedView!=viewPicker.getSelectedItemPosition();
      selectedId=next;layout=composition.getSelectedItemPosition();cameraOnly=only.isChecked();selectedView=viewPicker.getSelectedItemPosition();preferences.edit().putString("camera_id",selectedId).putInt("layout",layout).putInt("view",selectedView).putBoolean("camera_only",cameraOnly).putBoolean("auto_record",automatic.isChecked()).apply();
      if(changed){closeCamera("Подключение камеры…");startPreview();}
    }).create();
    settingsDialog.setOnDismissListener(dialog->{settingsVisible=false;settingsDialog=null;});settingsDialog.show();
    if(settingsDialog.getWindow()!=null){Rect available=new Rect();texture.getWindowVisibleDisplayFrame(available);settingsDialog.getWindow().setLayout(Math.min(dp(440),Math.max(dp(220),available.width()-dp(24))),Math.min(dp(600),Math.max(dp(200),available.height()-dp(24))));}
  }
  private void exportReport(){
    try{if(inventory==null){Toast.makeText(this,"Отчёт ещё готовится",Toast.LENGTH_SHORT).show();return;}ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,"voyah-camera-report-"+System.currentTimeMillis()+".json");values.put(MediaStore.Downloads.MIME_TYPE,"application/json");values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/VoyahCameraLab");Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Cannot create report");try(OutputStream stream=getContentResolver().openOutputStream(uri)){if(stream==null)throw new IOException("Cannot write report");stream.write(inventory.toString(2).getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"Отчёт: Download/VoyahCameraLab",Toast.LENGTH_LONG).show();}
    catch(Exception error){Toast.makeText(this,error.toString(),Toast.LENGTH_LONG).show();}
  }
  private void viewVideo(){if(lastVideo==null)return;closeCamera("Камера освобождена");try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(lastVideo,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception error){Toast.makeText(this,"Видео: Movies/VoyahCameraLab",Toast.LENGTH_LONG).show();}}
}
