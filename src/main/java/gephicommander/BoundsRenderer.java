package gephicommander;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import org.gephi.graph.api.GraphController;
import org.gephi.preview.api.CanvasSize;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.Item;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.api.Vector;
import org.gephi.preview.plugin.builders.NodeBuilder;
import org.gephi.preview.plugin.items.NodeItem;
import org.gephi.preview.spi.ItemBuilder;
import org.gephi.preview.spi.Renderer;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Renderer.class, position = 10)
public class BoundsRenderer implements Renderer {

    public static final String ENABLE_DISTRIBUTION_BOX = "bounds.show";
    public static final String BOX_COLOR = "bounds.color";
    
    private float minX = Float.MAX_VALUE;
    private float maxX = Float.MIN_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxY = Float.MIN_VALUE;
    private float[] xPercentiles = new float[101];
    private float[] yPercentiles = new float[101];

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(BoundsRenderer.class, "BoundsRenderer.name");
    }

    @Override
    public void preProcess(PreviewModel pm) {
        if (!pm.getProperties().getBooleanValue(ENABLE_DISTRIBUTION_BOX)) {
            return;
        }
        
        // Reset bounds
        minX = Float.MAX_VALUE;
        maxX = Float.MIN_VALUE;
        minY = Float.MAX_VALUE;
        maxY = Float.MIN_VALUE;
        
        // Collect all node positions
        var graph = Lookup.getDefault().lookup(GraphController.class).getGraphModel().getGraphVisible();
        
        float[] xPositions = new float[graph.getNodeCount()];
        float[] yPositions = new float[graph.getNodeCount()];
        var nodes = graph.getNodes();
        int i = 0;
        for (var node : nodes) {
            
            float x = node.x();
            float y = node.y();
            xPositions[i] = x;
            yPositions[i] = y;
            i++;
            
            // Update bounds
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        
        // Calculate percentiles
        Arrays.sort(xPositions);
        Arrays.sort(yPositions);
        
        // System.out.println(Arrays.toString(xPositions));
        // System.out.println(Arrays.toString(xPercentiles));
        // System.out.println(Arrays.toString(yPositions));
        for (int p = 0; p <= 100; p++) {
            int index = (int) Math.round((p / 100.0) * (xPositions.length - 1));
            xPercentiles[p] = xPositions[index];
            yPercentiles[p] = yPositions[index];
        }
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        if (target instanceof G2DTarget && properties.getBooleanValue(ENABLE_DISTRIBUTION_BOX)) {
            renderJava2D((G2DTarget) target, properties);
        }
    }

    public void renderJava2D(G2DTarget target, PreviewProperties properties) {
        
        drawBounds(target,90,90);
        
        /*
        g2.drawString("100,-100", 100,-100);
        g2.drawOval(100,-100,10,10);
        g2.drawString("-100,100", -100,100);
        g2.drawOval(-100,100,10,10); */
    }

    private void drawBounds(G2DTarget target, int xPercentile, int yPercentile) {
        Graphics2D g2 = target.getGraphics();
        g2.setColor(Color.GREEN);

        final float width = (maxX - minX)/100*xPercentile;
        final float height = (maxY - minY)/100*yPercentile;
        
        float x = minX/100*xPercentile;
        float y = -maxY/100*yPercentile;
        g2.draw(new Rectangle2D.Float(
            x, 
            y, 
            width, 
            height
        ));
        String s = ""+xPercentile+"% "+yPercentile+"%";
        g2.drawString(s, x, y);
    }

    @Override
    public PreviewProperty[] getProperties() {
        return new PreviewProperty[]{
            PreviewProperty.createProperty(this, ENABLE_DISTRIBUTION_BOX, Boolean.class,
                "Show node distribution box",
                "Shows rectangle containing 90% of nodes",
                PreviewProperty.CATEGORY_NODES).setValue(false),
            PreviewProperty.createProperty(this, BOX_COLOR, Color.class,
                "Box color",
                "Color of the distribution box",
                PreviewProperty.CATEGORY_NODES).setValue(new Color(255, 0, 0, 128))
        };
    }

    @Override
    public boolean isRendererForitem(Item item, PreviewProperties properties) {
        // Return true for the model item (null check for headless mode)
        return item == null || (properties.getBooleanValue(ENABLE_DISTRIBUTION_BOX) && item instanceof NodeItem);
    }

    @Override
    public boolean needsItemBuilder(ItemBuilder itemBuilder, PreviewProperties properties) {
        return itemBuilder instanceof NodeBuilder && properties.getBooleanValue(ENABLE_DISTRIBUTION_BOX);
    }

    @Override
    public CanvasSize getCanvasSize(Item item, PreviewProperties properties) {
        return new CanvasSize();
    }

    @Override
    public void postProcess(PreviewModel previewModel, RenderTarget target, PreviewProperties properties) {
    }
}