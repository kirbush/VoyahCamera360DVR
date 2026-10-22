package local.codex.voyahcameralab;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.Build;
import android.util.Size;
import org.json.*;
import java.util.*;

final class DeviceInventory {
  static JSONObject read(Context context,CameraManager manager) throws Exception {
    JSONObject root=new JSONObject();
    root.put("application","Voyah Camera Lab 0.1 diagnostic");
    root.put("sdk",Build.VERSION.SDK_INT);root.put("fingerprint",Build.FINGERPRINT);
    root.put("device",Build.DEVICE);root.put("capturedAtMs",System.currentTimeMillis());
    root.put("note","Camera ID is not a physical front/rear label. ID1 is the confirmed composite AVM path on the tested H97X.");
    JSONArray cameras=new JSONArray();
    for(String id:manager.getCameraIdList()) {
      JSONObject entry=new JSONObject();entry.put("id",id);
      try {
        CameraCharacteristics c=manager.getCameraCharacteristics(id);
        entry.put("lensFacing",c.get(CameraCharacteristics.LENS_FACING));
        entry.put("sensorOrientation",c.get(CameraCharacteristics.SENSOR_ORIENTATION));
        entry.put("hardwareLevel",c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        entry.put("capabilities",new JSONArray(Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))));
        entry.put("fpsRanges",String.valueOf(Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))));
        entry.put("physicalIds",new JSONArray(c.getPhysicalCameraIds()));
        StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(map!=null){entry.put("previewSizes",sizes(map.getOutputSizes(SurfaceTexture.class)));entry.put("encoderSizes",sizes(map.getOutputSizes(MediaCodec.class)));}
      } catch(Exception error){entry.put("error",error.toString());}
      cameras.put(entry);
    }
    root.put("cameras",cameras);
    JSONArray concurrent=new JSONArray();
    try {for(Set<String> ids:manager.getConcurrentCameraIds())concurrent.put(new JSONArray(ids));}
    catch(Exception error){root.put("concurrentQueryError",error.toString());}
    root.put("concurrentCameraSets",concurrent);
    JSONArray encoders=new JSONArray();
    for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
      if(!info.isEncoder())continue;
      for(String type:info.getSupportedTypes()) {
        if(!type.equals("video/avc")&&!type.equals("video/hevc"))continue;
        JSONObject item=new JSONObject();item.put("name",info.getName());item.put("type",type);item.put("hardware",info.isHardwareAccelerated());
        try {MediaCodecInfo.CodecCapabilities cap=info.getCapabilitiesForType(type);item.put("colorFormats",new JSONArray(Arrays.toString(cap.colorFormats)));item.put("maxInstances",cap.getMaxSupportedInstances());}
        catch(Exception error){item.put("error",error.toString());}
        encoders.put(item);
      }
    }
    root.put("encoders",encoders);return root;
  }
  static JSONArray sizes(Size[] values){JSONArray result=new JSONArray();if(values!=null)for(Size s:values)result.put(s.getWidth()+"x"+s.getHeight());return result;}
  static Size selectSize(CameraCharacteristics c,String id) {
    StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    Size[] values=map==null?null:map.getOutputSizes(SurfaceTexture.class);
    if(values==null||values.length==0)throw new IllegalStateException("No preview output sizes");
    int wantedW="1".equals(id)?1920:1280,wantedH=720;
    for(Size size:values)if(size.getWidth()==wantedW&&size.getHeight()==wantedH)return size;
    Size chosen=null;
    for(Size size:values){long area=(long)size.getWidth()*size.getHeight();if(size.getWidth()<=1920&&size.getHeight()<=1080&&(chosen==null||area>(long)chosen.getWidth()*chosen.getHeight()))chosen=size;}
    return chosen==null?values[0]:chosen;
  }
}
