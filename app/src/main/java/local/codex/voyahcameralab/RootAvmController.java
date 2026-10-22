package local.codex.voyahcameralab;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class RootAvmController {
  private static final String SERVICE_SHA="88e65cdae3f3793156d000be26620d36a696a54b3073deb6c72d4595e548ee19";
  private static final String LIBRARY_SHA="87306b1bd0b1e189c702170186b4507e5128d8c431a902d5dc75ebe7f79c57a6";
  private boolean verified;
  private static String hash(File file)throws Exception{
    MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];
    try(InputStream in=new FileInputStream(file)){int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}
    StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
  }
  void select(Context context,int view)throws Exception{
    if(view!=1&&view!=2)throw new IllegalArgumentException("Only front/rear selection is supported");
    if(!verified){
      if(!SERVICE_SHA.equals(hash(new File("/system/bin/dls_reverse_svr")))||!LIBRARY_SHA.equals(hash(new File("/system/lib64/libqg_hal.so"))))throw new IOException("Эта версия прошивки ещё не проверена для переключения камер");
      verified=true;
    }
    File executable=new File(context.getFilesDir(),"avm-control-arm64");
    if(!executable.isFile()||!NativeBuild.HELPER_SHA256.equals(hash(executable))){
      File temporary=new File(context.getFilesDir(),"avm-control-arm64.tmp");
      try(InputStream in=context.getAssets().open("avm-control-arm64");OutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[16384];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
      if(!NativeBuild.HELPER_SHA256.equals(hash(temporary))||!temporary.setExecutable(true,true)||!temporary.renameTo(executable))throw new IOException("Не удалось подготовить переключатель камер");
    }
    Process process=new ProcessBuilder("/system/xbin/su","0",executable.getAbsolutePath(),view==1?"front":"rear").redirectErrorStream(true).start();
    try{
      if(!process.waitFor(4,TimeUnit.SECONDS))throw new IOException("Нет ответа от переключателя камер");
      ByteArrayOutputStream result=new ByteArrayOutputStream();try(InputStream in=process.getInputStream()){byte[] bytes=new byte[1024];int count;while((count=in.read(bytes))!=-1)result.write(bytes,0,count);}
      String output=result.toString("UTF-8").trim();
      if(process.exitValue()!=0||!output.contains("AVM_VIEW_OK="+view))throw new IOException("Не удалось сменить ракурс: "+(output.isEmpty()?"нужен доступ su":output));
    }finally{process.destroy();}
  }
}
