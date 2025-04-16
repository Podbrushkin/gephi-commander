package gephicommander;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Partition;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.preview.api.CanvasSize;
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.Item;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.plugin.builders.NodeBuilder;
import org.gephi.preview.spi.ItemBuilder;
import org.gephi.preview.spi.Renderer;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Renderer.class, position = 301)
public class CustomRenderer implements Renderer {

    // Existing properties
    public static final String ENABLE_DISTRIBUTION_BOX = "bounds.show";
    public static final String BOX_COLOR = "bounds.color";
    public static final String BOX_MARGIN = "bounds.margin";
    
    // New properties for partition info
    public static final String SHOW_PARTITION_INFO = "partition.show";
    public static final String PARTITION_COLUMN = "partition.column";
    public static final String PARTITION_FONT_SIZE = "partition.font.size";
    public static final String PARTITION_FONT_COLOR = "partition.font.color";
    
    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(CustomRenderer.class, "BoundsRenderer.name");
    }

    @Override
    public void render(Item item, RenderTarget target, PreviewProperties properties) {
        if (target instanceof G2DTarget) {
            G2DTarget g2dTarget = (G2DTarget) target;
            Graphics2D g2d = g2dTarget.getGraphics();
            
            if (properties.getBooleanValue(ENABLE_DISTRIBUTION_BOX)) {
                renderBounds(g2dTarget, properties);
            }
            
            if (properties.getBooleanValue(SHOW_PARTITION_INFO)) {
                renderPartitionInfo(g2dTarget, properties);
            }
        }
    }

    private void renderPartitionInfo(G2DTarget target, PreviewProperties properties) {
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        GraphModel graphModel = graphController.getGraphModel();
        
        
        
        try {
            AppearanceModel appearanceModel = appearanceController.getModel();
            // GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

            

            // Get the partition function for this column
            
            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    

    private void renderBounds(G2DTarget target, PreviewProperties properties) {
        // Your existing bounds rendering logic
        Graphics2D g2d = target.getGraphics();
        g2d.setColor(properties.getColorValue(BOX_COLOR));
        // ... rest of your bounds drawing code
    }

    @Override
    public PreviewProperty[] getProperties() {
        return new PreviewProperty[]{
            // Existing properties
            PreviewProperty.createProperty(this, ENABLE_DISTRIBUTION_BOX, Boolean.class,
                "Show node distribution box",
                "Shows rectangle containing nodes",
                PreviewProperty.CATEGORY_NODES).setValue(false),
            PreviewProperty.createProperty(this, BOX_COLOR, Color.class,
                "Box color",
                "Color of the distribution box",
                PreviewProperty.CATEGORY_NODES).setValue(new Color(255, 0, 0, 128)),
            PreviewProperty.createProperty(this, BOX_MARGIN, Float.class,
                "Bounds margin",
                "How many nodes will be inside of bounds",
                PreviewProperty.CATEGORY_NODES).setValue(100f),
                
            // New partition info properties
            PreviewProperty.createProperty(this, SHOW_PARTITION_INFO, Boolean.class,
                "Show partition info",
                "Display partition statistics on the visualization",
                PreviewProperty.CATEGORY_NODES).setValue(true),
            PreviewProperty.createProperty(this, PARTITION_FONT_SIZE, Integer.class,
                "Font size",
                "Partition info font size",
                PreviewProperty.CATEGORY_NODES).setValue(12)
        };
    }

    @Override
    public void preProcess(PreviewModel previewModel) {
        
    }

    @Override
    public void postProcess(PreviewModel previewModel, RenderTarget target, PreviewProperties properties) {
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'postProcess'");
    }

    @Override
    public boolean isRendererForitem(Item item, PreviewProperties properties) {
         return true;
    }

    @Override
    public boolean needsItemBuilder(ItemBuilder itemBuilder, PreviewProperties properties) {
        return itemBuilder instanceof NodeBuilder;
    }

    @Override
    public CanvasSize getCanvasSize(Item item, PreviewProperties properties) {
        return new CanvasSize();
    }

    
}