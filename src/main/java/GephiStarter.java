import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.spi.Layout;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.types.DependantColor;
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.preview.types.EdgeColor;
import org.gephi.preview.types.EdgeColor.Mode;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
//https://github.com/KiranGershenfeld/VisualizingTwitchCommunities/blob/AutoAtlasGeneration/AtlasGeneration/Java/App.java

import com.google.gson.JsonParser;

//https://github.com/gephi/gephi-toolkit-demos/tree/master/src/main/java/org/gephi/toolkit/demos
public class GephiStarter {
    
    public static void main(String[] args) {
        var options = JsonParser.parseReader(new InputStreamReader(System.in)).getAsJsonObject();
        // System.out.println(json.get("layouts").getAsString());
        
        
        // System.exit(0);
        // GephiStarter.options = argsToMap(args);
        // System.out.println(options);
        var file = new File(options.get("InFile").getAsString());
        var outFile = new File(options.get("OutFile").getAsString());
        
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get models and controllers for this new workspace - will be useful later
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();

        //Import file       
        Container container;
        try {
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED);   //Force DIRECTED
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);

        //See if graph is well imported
        DirectedGraph graph = graphModel.getDirectedGraph();
        System.out.println("Nodes: " + graph.getNodeCount());
        System.out.println("Edges: " + graph.getEdgeCount());

        //Remove nodes with degree < 2
        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graphModel.getGraph());
        degreeFilter.setRange(new Range(2, Integer.MAX_VALUE));
        Query query = filterController.createQuery(degreeFilter);
        GraphView view = filterController.filter(query);
        graphModel.setVisibleView(view);
        

        var layouts = options.get("layouts").getAsJsonArray();
        for (var layoutEl : layouts) {
            var layout = layoutEl.getAsJsonObject();
            var name = layout.get("name").getAsString();
            var stepsEl = layout.get("steps");
            int steps = stepsEl == null ? stepsEl.getAsInt() : 200;

            switch (name) {
                case "YifanHu" : {
                    applyYifanHu(graphModel, steps); break;
                }
                case "ForceAtlas2" : {
                    applyForceAtlas2(graphModel, steps); break;
                }
                //default : applyLayout(graphModel);
    
            }
        }
        

        Function degreeRanking = appearanceModel.getNodeFunction(graphModel.defaultColumns()
            .degree(), RankingNodeSizeTransformer.class);
        RankingNodeSizeTransformer sizeTransformer = 
            (RankingNodeSizeTransformer) degreeRanking.getTransformer();

        int nodeMinSize = 5;
        sizeTransformer.setMinSize(5);
        sizeTransformer.setMaxSize(nodeMinSize*4);

        appearanceController.transform(degreeRanking);
        

        setGraphPreview();

        //Export
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
            
            ec.exportFile(outFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }
    private static void applyForceAtlas2(GraphModel graphModel, int steps) {
        //FORCE ATLAS INITIALIZATION
        ForceAtlas2 layout = new ForceAtlas2(null);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        
        Layout firstAlgo = layout;
        
        firstAlgo.initAlgo();
        
        System.out.println("FIRST PHASE EXECUTION");
        for (int k = 0; k < steps; k++) {
            firstAlgo.goAlgo();
        }

        layout.endAlgo();
        System.out.println("ENDING LAYOUT EXECUTION");
        return;
    }

    private static void applyYifanHu(GraphModel graphModel, int steps) {
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        
        
        for (var prop : layout.getProperties()) {
            try {
                var name = prop.getProperty().getName();
                var value = prop.getProperty().getValue();
                System.out.println(name+" = "+value);
            } catch (Exception e) {e.printStackTrace();}
        }
        runAlgoFor(layout,steps);
    }

    
    public static void setGraphPreview() {
        //Preview
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        //Node Label Properties
        model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN, 8));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.WHITE));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 4.0f);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_OPACITY, 40);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_COLOR, new DependantColor (Color.BLACK));

        //Edge Properties
        model.getProperties().putValue(PreviewProperty.SHOW_EDGES, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Mode.ORIGINAL));
        // model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Mode.SOURCE));
        // model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, 1.0);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, Boolean.TRUE);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MIN, 1);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MAX, 20);
        model.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 60);
        // model.getProperties().putValue(PreviewProperty.ARROW_SIZE, 5);
        


        //Image Properties
        model.getProperties().putValue(PreviewProperty.BACKGROUND_COLOR, Color.BLACK);

        return;
    }

    private static void runAlgoFor(Layout layout, int count) {
        layout.initAlgo();
        for (int k = 0; k < count; k++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }
}
