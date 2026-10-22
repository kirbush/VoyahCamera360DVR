package local.codex.voyahcameralab;
final class FrameHealth {
  private long last=-1;
  void reset(){last=-1;}
  void frame(long now){last=now;}
  boolean stalled(long now){return last>=0&&now-last>=5000;}
  long age(long now){return last<0?-1:Math.max(0,now-last);}
}
