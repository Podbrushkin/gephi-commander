package gephicommander;

import org.gephi.graph.api.GraphModel;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;

/**
 * A dummy layout implementation that does nothing.
 */
public class NoOpLayout implements Layout {

    private final LayoutBuilder builder;
    private GraphModel graphModel;
    private boolean converged;

    public NoOpLayout(LayoutBuilder builder) {
        this.builder = builder;
    }
    public NoOpLayout() {
        this.builder = null;
    }

    @Override
    public void initAlgo() {
        converged = false;
    }

    @Override
    public void goAlgo() {
        // Do nothing
    }

    @Override
    public void endAlgo() {
        converged = true;
    }

    @Override
    public boolean canAlgo() {
        return !converged;
    }

    @Override
    public LayoutProperty[] getProperties() {
        return new LayoutProperty[0]; // No properties
    }

    @Override
    public void resetPropertiesValues() {
        // No properties to reset
    }

    @Override
    public void setGraphModel(GraphModel graphModel) {
        this.graphModel = graphModel;
    }

    

    @Override
    public LayoutBuilder getBuilder() {
        return builder;
    }
}