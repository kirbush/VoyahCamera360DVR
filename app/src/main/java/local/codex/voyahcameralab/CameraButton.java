package local.codex.voyahcameralab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

final class CameraButton extends View {
  static final int RECORD=0,STOP=1,GEAR=2,SWITCH=3;
  private final int icon;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RippleDrawable ripple;
  private float visualScale(){return icon==SWITCH?1f:0.7f;}
  private float opacity(){return icon==SWITCH?1f:0.6f;}
  CameraButton(Context context,int icon,String description){super(context);this.icon=icon;setContentDescription(description);setClickable(true);setFocusable(true);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(0xA8181B20);ripple=new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),circle,null);setBackground(ripple);setAlpha(opacity());}
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);int inset=Math.round(Math.min(w,h)*(1f-visualScale())/2f);setBackground(new InsetDrawable(ripple,inset));}
  @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(opacity()*(enabled?1f:0.32f));invalidate();}
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,unit=Math.min(getWidth(),getHeight())/64f*visualScale();canvas.save();canvas.translate(cx,cy);canvas.scale(unit,unit);
    paint.setStrokeWidth(2.2f);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.WHITE);
    if(icon==RECORD){canvas.drawCircle(0,0,20,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFFF514B);canvas.drawCircle(0,0,14,paint);}
    else if(icon==STOP){paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(-11,-11,11,11,2,2,paint);}
    else if(icon==SWITCH){paint.setStrokeWidth(2.5f);canvas.drawArc(-14,-14,14,14,20,140,false,paint);canvas.drawArc(-14,-14,14,14,200,140,false,paint);canvas.drawLine(13,5,17,-2,paint);canvas.drawLine(17,-2,9,-1,paint);canvas.drawLine(-13,-5,-17,2,paint);canvas.drawLine(-17,2,-9,1,paint);}
    else{paint.setStrokeWidth(2.5f);canvas.drawCircle(0,0,13,paint);canvas.drawCircle(0,0,5,paint);paint.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine((float)Math.cos(a)*13,(float)Math.sin(a)*13,(float)Math.cos(a)*18,(float)Math.sin(a)*18,paint);}paint.setStrokeCap(Paint.Cap.BUTT);}
    canvas.restore();
  }
}
