package local.codex.voyahcameralab;
public final class PolicyTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    FramePolicy normal=new FramePolicy(1280,720,0,false);
    check(normal.outputWidth==1280&&normal.outputHeight==720,"Normal camera dimensions");
    FramePolicy full=new FramePolicy(1920,720,0,true);
    check(full.outputWidth==1920&&full.outputHeight==720,"Ordinary360 fills1920px; do not assume APA padding");
    FramePolicy avm=new FramePolicy(1920,720,0,true,1,false);
    check(avm.outputWidth==1440&&avm.outputHeight==720,"APA layout excludes only known empty right columns");
    check(avm.cropRight==0.75f,"Preserve APA pixel geometry");
    FramePolicy view=new FramePolicy(1920,1080,0,true,0,true);
    check(view.outputWidth==1200&&view.outputHeight==720&&view.cropLeft==0.375f&&view.cropRight==1f,"Ordinary360 camera-only excludes toolbar and birdseye");
    FramePolicy scaledAvm=new FramePolicy(1920,1080,0,true);
    check(scaledAvm.outputWidth==1920&&scaledAvm.outputHeight==720,"Normalize HAL-scaled AVM1080 back to its720-line geometry");
    FramePolicy rotated=new FramePolicy(1280,720,90,false);
    check(rotated.outputWidth==720&&rotated.outputHeight==1280,"Portrait sensor rotation");
    int[] fit=avm.fit(1000,600);
    check(fit[0]==0&&fit[1]==50&&fit[2]==1000&&fit[3]==500,"Letterbox without distortion");
    TimestampClock time=new TimestampClock(1000000000L);
    check(time.next(1000000000L)==0,"First timestamp");
    check(time.next(1000000000L)==1,"Duplicate clock must advance");
    check(time.next(999999999L)==2,"Backward clock must not reverse presentation");
    check(time.next(1033333000L)==33333,"Elapsed-time based media duration");
    CaptureGate gate=new CaptureGate();int old=gate.begin();
    check(gate.accepts(old),"Current open request");gate.cancel();
    check(!gate.accepts(old),"Late onOpened after close must be rejected");
    int current=gate.begin();check(!gate.accepts(old)&&gate.accepts(current),"Late callback cannot replace a newer session");
    FrameHealth health=new FrameHealth();check(!health.stalled(8000),"Initial no-frame timeout is separate");health.frame(9000);check(!health.stalled(13000)&&health.stalled(14000),"Interrupted stream becomes visible after five seconds");health.frame(15000);check(!health.stalled(15000),"Recovered frames clear stream interruption");health.reset();check(health.age(16000)==-1,"Closed stream forgets old frame time");
    System.out.println("PASS: AVM geometry, timestamps, late callbacks and frame-stall recovery");
  }
}
