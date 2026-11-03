package io.github.kadirjohn.kspeed;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconMaskTest {
    @Test public void unchangedMarkFitsCircularMaskInBothColorModes() throws Exception {
        int[] resources={R.drawable.ic_launcher_foreground,R.drawable.ic_launcher_monochrome};
        for(int index=0;index<resources.length;index++) {
            Drawable drawable=RuntimeEnvironment.getApplication().getDrawable(resources[index]);
            Bitmap mark=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);
            drawable.setBounds(0,0,432,432); drawable.draw(new Canvas(mark));
            int ink=0;
            for(int y=0;y<432;y++)for(int x=0;x<432;x++) {
                if(Color.alpha(mark.getPixel(x,y))<128)continue;
                ink++;
                assertTrue("Mark would be clipped by a circular mask at "+x+","+y,
                        Math.hypot(x-216,y-216)<144);
            }
            assertTrue("Both K and arrow must be visible",ink>10000);
            File dir=new File("build/reports/icon-masks");dir.mkdirs();
            for(boolean circle:new boolean[]{true,false}) {
                Bitmap preview=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(preview);Path mask=new Path();
                if(circle)mask.addCircle(216,216,144,Path.Direction.CW);
                else mask.addRoundRect(72,72,360,360,55,55,Path.Direction.CW);
                canvas.clipPath(mask);canvas.drawColor(Color.rgb(23,32,31));canvas.drawBitmap(mark,0,0,null);
                try(FileOutputStream out=new FileOutputStream(new File(dir,(index==0?"color":"mono")+(circle?"-circle.png":"-squircle.png")))) {
                    preview.compress(Bitmap.CompressFormat.PNG,100,out);
                }
            }
        }
    }
}
