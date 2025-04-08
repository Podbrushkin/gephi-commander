package gephicommander;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Comparator;

import org.gephi.graph.api.GraphController;
import org.gephi.preview.api.CanvasSize;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.Item;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
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
    public static final String BOX_MARGIN = "bounds.margin";
    
    private float minX = Float.MAX_VALUE;
    private float maxX = Float.MIN_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxY = Float.MIN_VALUE;
    private float[] leftToRightPercentiles = new float[101];
    private float[] bottomToTopPercentiles = new float[101];

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
        Float[] yPositions = new Float[graph.getNodeCount()];
        Float[] yPositionsG2d = new Float[graph.getNodeCount()];
        var nodes = graph.getNodes();
        int i = 0;
        for (var node : nodes) {
            
            float x = node.x();
            float y = node.y();
            xPositions[i] = x;
            yPositions[i] = y;
            yPositionsG2d[i] = -y; // drawing y coord = negative data y
            i++;
            
            // Update bounds
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        
        // Calculate percentiles
        Arrays.sort(xPositions);
        Arrays.sort(yPositions);//, Comparator.reverseOrder());
        Arrays.sort(yPositionsG2d, Comparator.reverseOrder());
        
        
        // System.out.println(Arrays.toString(xPositions));
        // System.out.println(Arrays.toString(yPercentiles));
        // System.out.println(Arrays.toString(yPositions));
        for (int p = 0; p <= 100; p++) {
            int index = (int) Math.round((p / 100.0) * (xPositions.length - 1));
            leftToRightPercentiles[p] = xPositions[index];
            bottomToTopPercentiles[p] = yPositionsG2d[index];
        }
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        if (target instanceof G2DTarget && properties.getBooleanValue(ENABLE_DISTRIBUTION_BOX)) {
            renderJava2D((G2DTarget) target, properties);
        }
    }

    public void renderJava2D(G2DTarget target, PreviewProperties properties) {
        float margin = properties.hasProperty(BOX_MARGIN) ? 
            properties.getFloatValue(BOX_MARGIN) :
            100.0f;
        drawBounds(target,(int)margin,(int)margin);
    }

    private void drawBounds(G2DTarget target, int xPercentile, int yPercentile) {
        if (xPercentile <=50 || yPercentile <= 50) 
            throw new IllegalArgumentException("Percentile should be more than 50");
        Graphics2D g2 = target.getGraphics();
        g2.setColor(Color.GREEN);

        float width = (maxX - minX)/100*xPercentile;
        float height = (maxY - minY)/100*yPercentile;
        
        // float x = minX/100*xPercentile;
        // float y = -maxY/100*yPercentile;
        // g2.draw(new Rectangle2D.Float(
        //     x, 
        //     y, 
        //     width, 
        //     height
        // ));
        // String s = String.format("%s %s %s%% %s%%", x,y,xPercentile,yPercentile);
        // g2.drawString(s, x, y);

        // g2.setColor(Color.BLUE);
        // s = String.format("%s %s%%", leftToRightPercentiles[xPercentile], xPercentile);
        // g2.drawString(s, leftToRightPercentiles[xPercentile], xPercentile);

        var leftBound = leftToRightPercentiles[100-xPercentile];
        var rightBound = leftToRightPercentiles[xPercentile];
        var topBound = bottomToTopPercentiles[yPercentile];
        var bottomBound = bottomToTopPercentiles[100-yPercentile];
        width = Math.abs(rightBound-leftBound);
        height = Math.abs(bottomBound-topBound);
        // width = 200;
        // height = 200;
        g2.draw(new Rectangle2D.Float(
            leftBound,
            topBound,
            width,
            height
        ));
        // drawHorizontal(g2, (int)topBound);
        
        // drawHorizontal(g2, (int)bottomBound);
        // drawVertical(g2, (int)leftToRightPercentiles[0]);
        // drawHorizontal(g2, (int)bottomToTopPercentiles[0]);
        
        // g2.setColor(Color.YELLOW);
        // drawVertical(g2, (int)leftBound);
        // drawVertical(g2, (int)rightBound);
        // drawHorizontal(g2, (int)topBound);
        // drawHorizontal(g2, (int)bottomBound);
        // drawHorizontal(g2, (int)bottomToTopPercentiles[0]);

        // g2.setColor(Color.BLUE);
        // drawHorizontal(g2, (int)bottomToTopPercentiles[0]);
        // g2.setColor(Color.YELLOW);
        // drawHorizontal(g2, (int)bottomToTopPercentiles[yPercentile]);
        // g2.setColor(Color.RED);
        // drawHorizontal(g2, (int)bottomToTopPercentiles[100]);

        // g2.setColor(Color.BLUE);
        // drawVertical(g2, (int)leftToRightPercentiles[0]);
        // g2.setColor(Color.YELLOW);
        // drawVertical(g2, (int)leftToRightPercentiles[xPercentile]);
        // g2.setColor(Color.RED);
        // drawVertical(g2, (int)leftToRightPercentiles[100]);



        // g2.drawString("100,-100", 100,-100);
        // g2.drawOval(100,-100,10,10);
        // g2.drawString("-100,100", -100,100);
        // g2.drawOval(-100,100,10,10);
    }
    private void drawHorizontal(Graphics2D g2, int y) {
        g2.drawLine(-1000, y, 1000, y);
    }
    private void drawVertical(Graphics2D g2, int x) {
        g2.drawLine(x, -1000, x, 1000);
    }
    private void drawRect(Graphics2D g2, float x, float y, float x1, float y1) {
        g2.draw(new Rectangle2D.Float(
            x,
            y,
            x1-x,
            y1-y
        ));
        String s = String.format("%s %s", x, y);
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
                PreviewProperty.CATEGORY_NODES).setValue(new Color(255, 0, 0, 128)),
            PreviewProperty.createProperty(this, BOX_MARGIN, Float.class,
                "Bounds margin",
                "How many nodes will be inside of bounds, i.e. 95 - almost all.",
                PreviewProperty.CATEGORY_NODES).setValue(100)
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