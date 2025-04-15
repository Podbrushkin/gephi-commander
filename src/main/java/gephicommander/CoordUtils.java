package gephicommander;

import java.awt.geom.Point2D;

import org.gephi.preview.api.G2DTarget;

public class CoordUtils {

    public static Point2D.Float convertCoordModelToView(int width, int height, ScalingTranslate st, Point2D.Float point) {
        var x = (point.getX()  + st.translateX)* st.scaling +width/2*(1-st.scaling);
        var y = (-point.getY() + st.translateY)* st.scaling +height/2*(1-st.scaling);
        return new Point2D.Float((float)x, (float)y);
    }
    public static Point2D convertCoordModelToView(G2DTarget target, float x, float y) {
        Point2D modelPoint = new Point2D.Float(x, -y);
        return target.getGraphics().getTransform().transform(modelPoint, null);
    }

    public static ScalingTranslate getToCenterOn(int width, int height, float scaling, Point2D.Float modelPoint) {
        float translateX = -(modelPoint.x * scaling + (width/2) * (1 - scaling) - width/2) / scaling;
        float translateY = -(-modelPoint.y * scaling + (height/2) * (1 - scaling) - height/2) / scaling;
        return new ScalingTranslate(scaling, translateX, translateY);
    }
    
}
