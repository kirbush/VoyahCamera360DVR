package local.codex.voyahcameralab;
final class TimestampClock {
  private final long start;private long previous=-1;
  TimestampClock(long start){this.start=start;}
  long next(long now){long value=Math.max(previous+1,Math.max(0,(now-start)/1000));previous=value;return value;}
}
