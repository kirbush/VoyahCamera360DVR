package local.codex.voyahcameralab;
final class FramePolicy {
  final int outputWidth,outputHeight,rotation;final float cropLeft,cropRight;
  FramePolicy(int width,int height,int rotation,boolean avm){
    this(width,height,rotation,avm,0,false);
  }
  FramePolicy(int width,int height,int rotation,boolean avm,int layout,boolean cameraOnly){
    if(width<=0||height<=0)throw new IllegalArgumentException("Invalid image size");
    this.rotation=((rotation%360)+360)%360;
    boolean avmGeometry=avm&&width==1920&&(height==720||height==1080||height==1200);
    this.cropRight=avmGeometry&&layout==1?0.75f:1f;
    this.cropLeft=avmGeometry&&cameraOnly?(layout==1?560f/1920f:720f/1920f):0f;
    int visibleWidth=Math.round(width*(cropRight-cropLeft));
    int visibleHeight=avmGeometry?720:height;
    boolean swap=this.rotation==90||this.rotation==270;
    this.outputWidth=(swap?visibleHeight:visibleWidth)&~1;
    this.outputHeight=(swap?visibleWidth:visibleHeight)&~1;
  }
  int[] fit(int width,int height){
    if(width<1||height<1)return new int[]{0,0,1,1};
    double scale=Math.min((double)width/outputWidth,(double)height/outputHeight);
    int w=Math.max(1,(int)Math.round(outputWidth*scale)),h=Math.max(1,(int)Math.round(outputHeight*scale));
    return new int[]{(width-w)/2,(height-h)/2,w,h};
  }
}
