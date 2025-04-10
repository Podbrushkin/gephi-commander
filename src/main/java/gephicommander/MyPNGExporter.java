package gephicommander;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.gephi.graph.api.Node;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.api.Vector;
import org.gephi.project.api.Workspace;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Lookup;

import com.google.gson.JsonObject;

class MyPNGExporter extends PNGExporter {
    
    private static ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");

    private JsonObject options = new JsonObject();
    private static int iteration = 0;
    private static String scalingExpr = null;
    private static String translateXExpr = null;
    private static String translateYExpr = null;

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

    private Float scaling = 1f;
    private static JsonObject previousInfo;
    private Node node;
    private Point2D.Float pointTr;

    public MyPNGExporter(){}
    public MyPNGExporter(JsonObject options) {
        super();
        this.options = options;
        if (options.has("scaling")) {
            scalingExpr = options.get("scaling").getAsString();
        }
        if (options.has("translateX")) {
            translateXExpr = options.get("translateX").getAsString();
        }
        if (options.has("translateY")) {
            translateYExpr = options.get("translateY").getAsString();
        }
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
            

            // Please change
            Integer exportSteps = GephiCommander.getCurrentAlgoSteps();
            Integer exportEach = GephiCommander.getCurrentAlgoEach();
            engine.put("steps", exportSteps);
            engine.put("each", exportEach);
            engine.put("i", iteration);
            engine.put("w", widthImg);
            engine.put("h", heightImg);
            
            

            if (options.has("scalingStart") && options.has("scalingEnd")) {
                var scalingStart = options.get("scalingStart").getAsFloat();
                var scalingEnd = options.get("scalingEnd").getAsFloat();
                // engine.put("scalingStart", scalingStart);
                // engine.put("scalingEnd", scalingEnd);
                // scalingExpr = "0.6+(i*each/steps)*(1-0.6)";
                scaling = scalingStart+(iteration*(float)exportEach/(float)exportSteps)*(scalingEnd-scalingStart);
                System.out.printf("Scaling: %s > %s. Current: %s%n",scalingStart,scalingEnd,scaling);
                target.setScaling(scaling);
            }
            else if (scalingExpr != null) {
                scaling = ((Number)engine.eval(scalingExpr)).floatValue();
                target.setScaling(scaling);
            }
            if (node != null) {
                var point = new Point2D.Float(node.x(),node.y());
                
                engine.put("nodeX",point.x);
                engine.put("nodeY",point.y);
            }
            if (options.has("centerOnX") && options.has("centerOnY")) {
                String exprX = options.get("centerOnX").getAsString();
                String exprY = options.get("centerOnY").getAsString();
                float x = ((Number)engine.eval(exprX)).floatValue();
                float y = ((Number)engine.eval(exprY)).floatValue();
                System.out.printf("centerOn evaluated to %s %s %n",x,y);
                centerOnModelCoord(target, x, y);
            }
            if (options.has("centerOnX") ^ options.has("centerOnY"))
                throw new IllegalArgumentException("centerOnX, centerOnY: either both or none.");

            if ((options.has("centerOnX") || options.has("centerOnY")) &&
                (options.has("translateX") || options.has("translateY"))) 
                throw new IllegalArgumentException("centerOnX/Y and translateX/Y cannot be used together.");
            
            
            var translateX = target.getTranslate().getX();
            var translateY = target.getTranslate().getY();
            if (translateXExpr != null) {
                String expressionLocal = translateXExpr
                    .replaceAll("\\bi\\b", String.valueOf(iteration))
                    .replaceAll("\\bw\\b", String.valueOf(widthImg))
                    .replaceAll("\\bh\\b", String.valueOf(heightImg))
                    .replaceAll("\\bsc\\b", String.valueOf(scaling));
                Number value = (Number)engine.eval(expressionLocal);
                translateX = value.floatValue();
            }
            if (translateYExpr != null) {
                String expressionLocal = translateYExpr
                    .replaceAll("\\bi\\b", String.valueOf(iteration))
                    .replaceAll("\\bw\\b", String.valueOf(widthImg))
                    .replaceAll("\\bh\\b", String.valueOf(heightImg))
                    .replaceAll("\\bsc\\b", String.valueOf(scaling));
                Number value = (Number)engine.eval(expressionLocal);
                translateY = value.floatValue();
            }
            target.getTranslate().set(translateX, translateY);
            // System.out.println("MyPNGExporter expressons finish");
            // engine.put("bounds", GephiStarter.getGraphBounds(0.01f).toString());
            
            


            target.refresh();
            
            
            
            
            
            
            // var scaling = target.getScaling();
            // target.setScaling(0.5f);
            // target.getTranslate().set(0, 0);
            /* if (scalingStart != null && scalingStep != null) {
                var newScaling = scalingStart+(scalingStep*scalingIter);
                System.out.println("Dynamic scaling:"+newScaling);
                target.setScaling(newScaling);
            }
            else if (options.has("scaling")) {
                target.setScaling(options.get("scaling").getAsFloat());
            } */
            /* if (options.has("translate")) {
                var transObj = options.get("translate").getAsJsonObject();
                target.getTranslate().set(transObj.get("x").getAsFloat(),transObj.get("y").getAsFloat());
            } */
            /* if (options.has("translateX")) {
                var transObj = options.get("translateX").getAsString();
                target.getTranslate().set(transObj.get("x").getAsFloat(),transObj.get("y").getAsFloat());
            } */
            // target.refresh();
            // System.out.printf("target.getHeight()=%s,%ntarget.getScaling()=%s,%ntarget.getTranslate()=%s%n",
            //     target.getHeight(),target.getScaling(),target.getTranslate()); 

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
                var font = imgGraphics.getFont().deriveFont(32f);
                imgGraphics.setFont(font);
                imgGraphics.drawString(str,0,(int)(heightImg*0.95));
            }
            
            iteration++;
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
    

    private void drawPointByDrawingCoords(Graphics srcGraphics, int x, int y) {
        srcGraphics.fillOval(x, y, 5, 5);
        String str = String.format("%s %s",x,y);
        srcGraphics.drawString(str, x, y);
    }
    private void drawPointInModelCoords(Graphics srcGraphics, G2DTarget target, float modelX, float modelY) {
        // Convert model coordinates to view coordinates
        Point2D viewPoint = convertCoordModelToView(target,modelX,modelY);
        
        int x = (int) viewPoint.getX();
        int y = (int) viewPoint.getY();
        
        // Draw the point (now in view coordinates)
        srcGraphics.fillOval(x - 2, y - 2, 5, 5);  // Center the oval on the point
        
        // Draw coordinates label
        String str = String.format("model=%.1f, %.1f\nview=%s %s", modelX, modelY,x,y);
        srcGraphics.drawString(str, x, y);  // Offset the text slightly
    }
    private Point2D convertCoordModelToView(G2DTarget target, float x, float y) {
        Point2D modelPoint = new Point2D.Float(x, -y);
        return target.getGraphics().getTransform().transform(modelPoint, null);
    }
    /**
     * Centers the view on a specific model coordinate (e.g., node position).
     * @param target The G2DTarget to adjust.
     * @param modelX Model X-coordinate to center on.
     * @param modelY Model Y-coordinate to center on.
     */
    private void centerOnModelCoord(G2DTarget target, float modelX, float modelY) {
        float scaling = target.getScaling();
        System.out.printf("Centering on (%.1f, %.1f) with scaling=%.2f%n", 
                        modelX, modelY, scaling);

        // Correct for any scaling
        float translateX = -(modelX * scaling + (widthImg/2) * (1 - scaling) - widthImg/2) / scaling;
        float translateY = -(-modelY * scaling + (heightImg/2) * (1 - scaling) - heightImg/2) / scaling;
        
        target.getTranslate().set(translateX, translateY);
    }
    
    private Point2D.Float scaleAndTranslateToDrawingCoord(Point2D.Float point) {
        float scaling = target.getScaling();
        Vector transl = target.getTranslate();
        var x = (point.getX()  + transl.x)* scaling +widthImg/2*(1-scaling);
        var y = (-point.getY() + transl.y)* scaling +heightImg/2*(1-scaling);
        return new Point2D.Float((float)x, (float)y);
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