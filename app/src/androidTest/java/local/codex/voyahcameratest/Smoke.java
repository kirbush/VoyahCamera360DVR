package local.codex.voyahcameratest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;
public final class Smoke extends Instrumentation {
  Activity activity;JSONArray checks=new JSONArray();
  interface Condition {boolean check(JSONObject state)throws Exception;}
  public void onCreate(Bundle args){super.onCreate(args);start();}
  JSONObject state()throws Exception{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getExternalFilesDir(null),"state.json").toPath()),StandardCharsets.UTF_8));}
  int id(String name){return getTargetContext().getResources().getIdentifier(name,"id","local.codex.voyahcameralab");}
  void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  JSONObject waitFor(String name,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+18000;JSONObject last=null;while(SystemClock.elapsedRealtime()<end){last=state();if(condition.check(last)){checks.put(new JSONObject().put("check",name).put("state",last));Bundle progress=new Bundle();progress.putString("stream","PASS: "+name+"\n");sendStatus(0,progress);return last;}SystemClock.sleep(250);}throw new AssertionError(name+" "+last);}
  void launch(){activity=startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName("local.codex.voyahcameralab","local.codex.voyahcameralab.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  void finishActivity(){runOnMainSync(()->activity.finish());waitForIdleSync();SystemClock.sleep(700);}
  void click(String name){runOnMainSync(()->{View v=activity.findViewById(id(name));check(v!=null&&v.isEnabled(),"Control missing/disabled: "+name);v.performClick();});waitForIdleSync();}
  AlertDialog dialog()throws Exception{java.lang.reflect.Field f=activity.getClass().getDeclaredField("settingsDialog");f.setAccessible(true);return (AlertDialog)f.get(activity);}
  void settings()throws Exception{click("settings_button");check(dialog()!=null,"Gear must open settings");}
  void toggle(String name,boolean value)throws Exception{AlertDialog d=dialog();runOnMainSync(()->((CheckBox)d.findViewById(id(name))).setChecked(value));}
  void apply()throws Exception{AlertDialog d=dialog();runOnMainSync(()->d.getButton(AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();}
  public void onStart(){Bundle result=new Bundle();JSONObject report=new JSONObject();try{
    getTargetContext().getSharedPreferences("camera",0).edit().putString("camera_id","1").putInt("view",0).putBoolean("camera_only",true).putInt("layout",0).putBoolean("auto_record",false).commit();
    launch();waitFor("launch starts preview, recording defaults off",s->s.getBoolean("opening")&&s.getLong("frames")>=3&&!s.getBoolean("recording"));
    runOnMainSync(()->check(activity.findViewById(id("camera_picker"))==null,"Settings must be hidden on main screen"));
    click("record_button");waitFor("manual recording starts",s->s.getBoolean("recording"));SystemClock.sleep(12000);check(state().getBoolean("recording"),"Recording must exceed old 10 second limit");
    click("stop_button");JSONObject movie=waitFor("stop saves MP4 and keeps preview",s->!s.getBoolean("recording")&&s.getBoolean("opening")&&!s.isNull("lastVideo"));report.put("manualVideo",movie.getString("lastVideo"));
    settings();long before=state().getLong("frames");SystemClock.sleep(1500);check(state().getBoolean("opening")&&state().getLong("frames")>before,"Settings dialog must not interrupt video");toggle("auto_record_checkbox",true);apply();finishActivity();launch();
    waitFor("auto-record option persists after restart",s->s.getBoolean("recording")&&s.getBoolean("autoRecordOnOpen"));SystemClock.sleep(1500);click("stop_button");waitFor("automatic recording can be stopped",s->!s.getBoolean("recording")&&s.getBoolean("opening"));
    settings();toggle("auto_record_checkbox",false);toggle("crop_checkbox",false);apply();finishActivity();launch();
    waitFor("saved crop and auto-record disabled survive restart",s->s.getBoolean("opening")&&s.getLong("frames")>=3&&!s.getBoolean("recording")&&!s.getBoolean("autoRecordOnOpen")&&!s.getBoolean("cameraOnly"));
    settings();toggle("crop_checkbox",true);apply();waitFor("restore camera-only view",s->s.getBoolean("cameraOnly")&&s.getBoolean("opening")&&s.getLong("frames")>=3);
    runOnMainSync(()->{ViewGroup content=activity.findViewById(android.R.id.content);View root=content.getChildAt(0);ViewGroup.LayoutParams lp=root.getLayoutParams();lp.width=600;lp.height=320;root.setLayoutParams(lp);});waitForIdleSync();SystemClock.sleep(1000);
    runOnMainSync(()->{View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);View rec=activity.findViewById(id("record_button"));int[] rp=new int[2],bp=new int[2];root.getLocationOnScreen(rp);rec.getLocationOnScreen(bp);check(rec.getWidth()>0&&bp[1]>=rp[1]&&bp[1]+rec.getHeight()<=rp[1]+root.getHeight(),"Recording controls must fit 600x320 window");});
    check(state().getBoolean("opening"),"Small-window preview must remain open");report.put("smallWindow","600x320 controls fit; preview remains open");finishActivity();
    waitFor("invisible activity releases camera",s->!s.getBoolean("opening")&&!s.getBoolean("recording"));report.put("passed",true);report.put("checks",checks);result.putString("stream",report.toString(2));
    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"minimal-instrumentation.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    finish(Activity.RESULT_OK,result);
  }catch(Throwable error){try{getTargetContext().getSharedPreferences("camera",0).edit().putBoolean("auto_record",false).commit();if(activity!=null)finishActivity();report.put("passed",false).put("error",error.toString()).put("checks",checks);result.putString("stream",report.toString(2));}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}}
}
