package gephicommander;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.gephi.graph.api.Node;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.project.api.Workspace;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Lookup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class MyPNGExporter extends PNGExporter {
    
    private static ScriptEngine engine = GephiCommander.engine;

    private JsonObject options = new JsonObject();

    private ProgressTicket progress;
    private boolean cancel = false;
    private Workspace workspace;
    private OutputStream stream;
    private int widthImg = 1024;
    private int heightImg = 1024;
    private boolean transparentBackground = false;
    private int margin = 4;
    private G2DTarget target;
    private Color oldColor;
    
    private static JsonObject previousInfo;
    private Node node;

    public MyPNGExporter(){}
    public MyPNGExporter(JsonObject options) {
        super();
        this.options = options;
        if (options.has("findNode")) {
            var jsonPrim = options.get("findNode").getAsJsonPrimitive();
            if (jsonPrim.isString()) {
                node = GephiCommander.getNodeById(jsonPrim.getAsString());
            } else {
                node = GephiCommander.getNodeById(jsonPrim.getAsNumber());
            }
            System.out.printf("found node: %s %s %s %s %n",
                node.getId(),node.getLabel(),node.x(),node.y());
        }
        if (options.has("transparentBg") && 
            options.get("transparentBg").getAsBoolean()) {
            this.setTransparentBackground(true);
        }
    }

    @Override
    public boolean execute() {
        Progress.start(progress);

        PreviewController ctrl
            = Lookup.getDefault().lookup(PreviewController.class);
        PreviewModel m = ctrl.getModel(workspace);
        

        setExportProperties(m);
        ctrl.refreshPreview(workspace);

        target = (G2DTarget) ctrl.getRenderTarget(
            RenderTarget.G2D_TARGET,
            workspace);
        if (target instanceof LongTask) {
            ((LongTask) target).setProgressTicket(progress);
        }

        target.refresh();
        
        
        // var graph = Lookup.getDefault().lookup(GraphModel.class).getUndirectedGraphVisible();

        // System.out.printf("%s %s%n",node.x(),node.y());
        try {
            // if user wants to use graph size in his expressions
            JsonObject boundsJsonObj = null;
            if (options.has("boundsMargin")) {
                var graphMargin = options.get("boundsMargin").getAsFloat();
                boundsJsonObj = GephiCommander.getGraphBounds(graphMargin);
                String json = boundsJsonObj.toString();
                System.out.printf("Bounds for margin=%s: %s%n",graphMargin,json);
                engine.eval("bounds = "+json);
                // engine.eval("print('from js!');print(bounds.yMax);");
            }

            

            // user can access last used scaling and translate
            // String prevExpr = "let prev";
            if (previousInfo != null) {
                String json = previousInfo.toString();
                engine.eval("prev = "+json);
                // prevExpr += " = "+json;
            }
            // engine.eval(prevExpr+";");
            
            
            // Integer stepsGlobal = GephiCommander.LayoutStatus.globalIterationsMax;
            Integer steps = GephiCommander.LayoutStatus.localIterationsMax;
            Integer exportEach = GephiCommander.LayoutStatus.localExportEach;
            Integer step = GephiCommander.LayoutStatus.localIteration;


            
            // engine.put("i", step);
            // engine.put("iMax", steps);
            // engine.put("exportEach", exportEach);
            // engine.put("iGlobal", GephiCommander.LayoutStatus.globalIterationsDone);
            // engine.put("iGlobalMax", GephiCommander.LayoutStatus.globalIterationsMax);
            
            engine.put("w", widthImg);
            engine.put("h", heightImg);
            
            

            float scaling = target.getScaling();
            if (options.has("scalingStart") || options.has("scalingEnd")) {
                String scalingStartExpr = options.has("scalingStart") ? 
                    options.get("scalingStart").getAsString() : "1";
                
                String scalingEndExpr = options.has("scalingEnd") ? 
                    options.get("scalingEnd").getAsString() : "1";
                
                float scalingStart = ((Number)engine.eval(scalingStartExpr)).floatValue();
                float scalingEnd = ((Number)engine.eval(scalingEndExpr)).floatValue();

                scaling = scalingStart+(step/(float)steps)*(scalingEnd-scalingStart);

                System.out.printf("Scaling(start=%s;end=%s). Current: %s%n",scalingStart,scalingEnd,scaling);
                target.setScaling(scaling);

                System.out.printf("iteration=%s,exportEach=%s,exportSteps=%s%n",step,exportEach,steps);
            }
            else if (options.has("scaling")) {
                String scalingExpr = options.get("scaling").getAsString();
                scaling = ((Number)engine.eval(scalingExpr)).floatValue();
                target.setScaling(scaling);
            }
            engine.put("sc", scaling);

            if (node != null) {
                var point = new Point2D.Float(node.x(),node.y());
                
                engine.put("nodeX",point.x);
                engine.put("nodeY",point.y);
            }
            if (options.has("centerOn")) {
                var el = options.get("centerOn");
                if (!el.isJsonArray() || el.getAsJsonArray().size() != 2)
                    throw new IllegalArgumentException("centerOn should be an array of 2 elements - x and y.");

                var point = el.getAsJsonArray();
                String exprX = point.get(0).getAsString();
                String exprY = point.get(1).getAsString();
                
                float x = ((Number)engine.eval(exprX)).floatValue();
                float y = ((Number)engine.eval(exprY)).floatValue();
                System.out.printf("centerOn evaluated to %s %s %n",x,y);
                var st = CoordUtils.getToCenterOn(widthImg, heightImg, target.getScaling(), new Point2D.Float(x, y));
                target.getTranslate().set(st.translateX, st.translateY);
            }
            if (options.has("centerOnStart") && options.has("centerOnEnd")) {
                var elStart = options.get("centerOnStart");
                var elEnd = options.get("centerOnEnd");
                if (!elStart.isJsonArray() || elStart.getAsJsonArray().size() != 2 ||
                    !elEnd.isJsonArray() || elEnd.getAsJsonArray().size() != 2)
                    throw new IllegalArgumentException("centerOnStart/End both should be an array of 2 elements - x and y.");
                
                Point2D.Float pointStart = evaluateAndGetPoint(elStart);
                Point2D.Float pointEnd = evaluateAndGetPoint(elEnd);

                // "0 + step / steps * (nodeX - 0)"
                float currentX = (float)(pointStart.getX() + (float)step / (float)steps * (pointEnd.getX() - pointStart.getX()));
                float currentY = (float)(pointStart.getY() + (float)step / (float)steps * (pointEnd.getY() - pointStart.getY()));

                var st = CoordUtils.getToCenterOn(widthImg, heightImg, target.getScaling(), new Point2D.Float(currentX, currentY));
                target.getTranslate().set(st.translateX, st.translateY);
                
            }

            if (options.has("centerOn") && options.has("translate")) 
                throw new IllegalArgumentException("PNGExporter.centerOn and translate cannot be used together.");
            
            
            if (options.has("translate")) {
                var el = options.get("translate");
                if (!el.isJsonArray() || el.getAsJsonArray().size() != 2)
                    throw new IllegalArgumentException("PNGExporter.translate should be an array of 2 elements - x and y.");
                
                String xExpression = el.getAsJsonArray().get(0).getAsString();
                String yExpression = el.getAsJsonArray().get(1).getAsString();

                Float xTranslate = ((Number)engine.eval(xExpression)).floatValue();
                Float yTranslate = ((Number)engine.eval(yExpression)).floatValue();

                target.getTranslate().set(xTranslate, yTranslate);
            }
            // engine.put("bounds", GephiStarter.getGraphBounds(0.01f).toString());
            

            target.refresh();
            

            // print useful info
            var info = new JsonObject();
            info.addProperty("scaling", scaling);
            info.addProperty("translateX", target.getTranslate().getX());
            info.addProperty("translateY", target.getTranslate().getY());
            System.out.println(info);
            previousInfo = info;

            Progress.switchToIndeterminate(progress);

            

            Image sourceImg = target.getImage();
            Graphics imgGraphics = sourceImg.getGraphics();

            // this always correct, for any scaling
            // imgGraphics.setColor(Color.PINK);
            // drawPointInModelCoords(imgGraphics,target,100,200);
            
            if (options.has("drawDebug") &&
                options.get("drawDebug").getAsBoolean() 
                ) {
                imgGraphics.setColor(Color.GRAY);
                // srcGraphics.drawLine(width/2, height/2, (int)pointTr.x, (int)pointTr.y);
                // srcGraphics.fillOval(0, 0, width/100, height/100);
                var str = String.format("sc=%s\ntr=%s",target.getScaling(),target.getTranslate());
                float fontSize = heightImg/18;  // Ok for any resolution
                var font = imgGraphics.getFont().deriveFont(fontSize);
                imgGraphics.setFont(font);
                imgGraphics.drawString(str,0,(int)(heightImg*0.95));
            }

            if (options.has("drawTranslate") &&
                options.get("drawTranslate").getAsBoolean() 
                ) {
                imgGraphics.setColor(Color.RED);
                
                // Why x negative? idk
                drawLineModel(imgGraphics, 0,0, (int)-target.getTranslate().x, (int)target.getTranslate().y);

            }
            
            BufferedImage img = new BufferedImage(widthImg, heightImg, BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().drawImage(sourceImg, 0, 0, null);
            ImageIO.write(img, "png", stream);
            stream.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        discardExportProperties(m);

        Progress.finish(progress);

        return !cancel;
    }
    private void drawLineModel(Graphics g2, int mx0, int my0, int mx1, int my1) {
        var start = CoordUtils.convertCoordModelToView(target, mx0, my0);
        var end = CoordUtils.convertCoordModelToView(target, mx1, my1);

        int x0 = (int)start.getX();
        int y0 = (int)start.getY();
        int x1 = (int)end.getX();
        int y1 = (int)end.getY();

        g2.drawLine(x0,y0,x1,y1);
        String s = String.format("%s %s (%s %s)", mx0, my0, x0, y0);
        g2.drawString(s, x0, y0);
        s = String.format("%s %s (%s %s)", mx1, my1, x1, y1);
        g2.drawString(s, x1, y1);
    }

    private Point2D.Float evaluateAndGetPoint(JsonElement el) throws ScriptException {
            if (!el.isJsonArray() || el.getAsJsonArray().size() != 2)
                throw new IllegalArgumentException("centerOnStart/End both should be an array of 2 elements - x and y.");
            
            var point = el.getAsJsonArray();
            String exprX = point.get(0).getAsString();
            String exprY = point.get(1).getAsString();
            
            
            float x = ((Number)engine.eval(exprX)).floatValue();
            float y = ((Number)engine.eval(exprY)).floatValue();

            return new Point2D.Float(x,y);
    }

    public int getHeight() {
        return heightImg;
    }

    public void setHeight(int height) {
        this.heightImg = height;
    }

    public int getWidth() {
        return widthImg;
    }

    public void setWidth(int width) {
        this.widthImg = width;
    }

    public int getMargin() {
        return margin;
    }

    public void setMargin(int margin) {
        this.margin = margin;
    }

    public boolean isTransparentBackground() {
        return transparentBackground;
    }

    public void setTransparentBackground(boolean transparentBackground) {
        this.transparentBackground = transparentBackground;
    }

    @Override
    public Workspace getWorkspace() {
        return workspace;
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public void setOutputStream(OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        if (target instanceof LongTask) {
            ((LongTask) target).cancel();
        }
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
        this.progress = progressTicket;
    }

    private synchronized void setExportProperties(PreviewModel m) {
        PreviewProperties props = m.getProperties();
        props.putValue(PreviewProperty.VISIBILITY_RATIO, 1.0F);
        props.putValue("width", widthImg);
        props.putValue("height", heightImg);
        oldColor = props.getColorValue(PreviewProperty.BACKGROUND_COLOR);
        if (transparentBackground) {
            props.putValue(
                PreviewProperty.BACKGROUND_COLOR,
                null); //Transparent
        }
        props.putValue(PreviewProperty.MARGIN, new Float(margin));
    }

    private synchronized void discardExportProperties(PreviewModel m) {
        PreviewProperties props = m.getProperties();
        props.removeSimpleValue("width");
        props.removeSimpleValue("height");
        props.removeSimpleValue(PreviewProperty.MARGIN);
        props.putValue(PreviewProperty.BACKGROUND_COLOR, oldColor);
    }
}