package local.codex.voyahcameralab;

import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.Surface;
import java.nio.ByteBuffer;

final class AvcRecorder {
  final Uri uri;final Surface input;final TimestampClock clock;
  private final Context context;private final MediaCodec codec;private final MediaMuxer muxer;
  private final ParcelFileDescriptor descriptor;private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
  private int track=-1,samples;private boolean started,closed;
  AvcRecorder(Context context,int width,int height,String cameraId) throws Exception {
    this.context=context;
    ContentValues values=new ContentValues();
    values.put(MediaStore.Video.Media.DISPLAY_NAME,"camera-"+cameraId+"-"+System.currentTimeMillis()+".mp4");
    values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
    values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VoyahCamera360DVR");
    values.put(MediaStore.Video.Media.IS_PENDING,1);
    Uri created=context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
    if(created==null)throw new IllegalStateException("Cannot create output video");
    uri=created;
    MediaCodec preparingCodec=null;MediaMuxer preparingMuxer=null;ParcelFileDescriptor preparingFd=null;Surface preparingSurface=null;
    try {
      preparingFd=context.getContentResolver().openFileDescriptor(uri,"rw");
      if(preparingFd==null)throw new IllegalStateException("Cannot open output video");
      preparingMuxer=new MediaMuxer(preparingFd.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      preparingCodec=MediaCodec.createEncoderByType("video/avc");
      MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
      format.setInteger(MediaFormat.KEY_BIT_RATE,6000000);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
      preparingCodec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
      preparingSurface=preparingCodec.createInputSurface();preparingCodec.start();
      codec=preparingCodec;muxer=preparingMuxer;descriptor=preparingFd;input=preparingSurface;
      clock=new TimestampClock(System.nanoTime());
    } catch(Exception failure){
      if(preparingSurface!=null)preparingSurface.release();
      if(preparingCodec!=null)try{preparingCodec.release();}catch(Exception ignored){}
      if(preparingMuxer!=null)try{preparingMuxer.release();}catch(Exception ignored){}
      if(preparingFd!=null)try{preparingFd.close();}catch(Exception ignored){}
      context.getContentResolver().delete(uri,null,null);throw failure;
    }
  }
  void drain(boolean end) throws Exception {
    if(end)codec.signalEndOfInputStream();
    long deadline=SystemClock.elapsedRealtime()+2500;
    while(true){
      int index=codec.dequeueOutputBuffer(info,end?10000:0);
      if(index==MediaCodec.INFO_TRY_AGAIN_LATER){if(!end)return;if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("Encoder EOS timeout");continue;}
      if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IllegalStateException("Encoder changed format twice");track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;continue;}
      if(index<0)continue;
      ByteBuffer data=codec.getOutputBuffer(index);
      boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
      try {
        if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
        if(info.size>0){if(!started||data==null)throw new IllegalStateException("Encoded data without muxer format");data.position(info.offset);data.limit(info.offset+info.size);muxer.writeSampleData(track,data,info);samples++;}
      }finally{codec.releaseOutputBuffer(index,false);}
      if(eos)return;
    }
  }
  Uri finish() throws Exception {
    if(closed)return null;closed=true;Exception error=null;
    try{drain(true);}catch(Exception failure){error=failure;}
    try{if(started)muxer.stop();}catch(Exception failure){if(error==null)error=failure;}
    try{muxer.release();}catch(Exception ignored){}
    try{codec.stop();}catch(Exception ignored){}
    codec.release();input.release();descriptor.close();
    if(error!=null||samples==0){context.getContentResolver().delete(uri,null,null);if(error!=null)throw error;throw new IllegalStateException("No encoded frames");}
    ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.IS_PENDING,0);
    context.getContentResolver().update(uri,values,null,null);return uri;
  }
}
