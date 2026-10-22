package local.codex.voyahcameralab;
final class CaptureGate {
  private int generation;private boolean active;
  synchronized int begin(){active=true;return ++generation;}
  synchronized void cancel(){active=false;generation++;}
  synchronized boolean accepts(int value){return active&&value==generation;}
}
