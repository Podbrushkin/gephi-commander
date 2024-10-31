import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.api.Partition;
import org.gephi.appearance.api.PartitionFunction;
import org.gephi.appearance.plugin.PartitionElementColorTransformer;
import org.gephi.appearance.plugin.RankingElementColorTransformer;
import org.gephi.appearance.plugin.RankingLabelSizeTransformer;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;
import org.gephi.appearance.plugin.palette.Palette;
import org.gephi.appearance.plugin.palette.PaletteManager;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Graph;
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
import org.gephi.layout.plugin.labelAdjust.LabelAdjust;
import org.gephi.layout.plugin.scale.ExpandLayout;
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
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;
//https://github.com/KiranGershenfeld/VisualizingTwitchCommunities/blob/AutoAtlasGeneration/AtlasGeneration/Java/App.java

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;

//https://github.com/gephi/gephi-toolkit-demos/tree/master/src/main/java/org/gephi/toolkit/demos
public class GephiStarter {
    // private static Map<String,String> options;

    public static Map<String, String> argsToMap(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("-")) {
                map.put(args[i].substring(1), args[i + 1]);
                i++;
            }
            else {
                map.put(args[i], null);
            }
        }
        return map;
    }

    // public static String getFullStdin() {
    //     String input = null;
    //     try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
    //         input = reader.lines().reduce((x,y) -> x+"\n"+y).orElse(null);
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    //     return input;
    // }


    public static void main(String[] args) {
        // var jsonStr = getFullStdin();
        // System.out.println(jsonStr);
        
        var options = JsonParser.parseReader(new InputStreamReader(System.in)).getAsJsonObject();
        // System.out.println(json.get("layouts").getAsString());
        var layouts = options.get("layouts").getAsJsonArray();
        
        System.exit(0);
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
        
        
        var desiredLayout = options.get("Layout");
        switch (desiredLayout) {
            case "YifanHu" : {
                applyYifanHu(graphModel); break;
            }
            case "Twitch" : {
                layoutAsTwitchGraph(graphModel); break;
            }
            case "ForceAtlas2" : {
                applyForceAtlas2(graphModel, 200); break;
            }
            default : applyLayout(graphModel);

        }
        

        
        // applyForceAtlas(graphModel);
        

        
        
        // var fa2 = new ForceAtlas2(null);
        // fa2.setGraphModel(graphModel);
        // fa2.setNormalizeEdgeWeights(true);
        
        // var fa = new ForceAtlas().buildLayout();
        // fa.setGraphModel(graphModel);
        // fa.setRepulsionStrength(40000.0);
        // fa.initAlgo();
        // fa.setOutboundAttractionDistribution(true);
        // for (int i = 0; i < 100 && fa.canAlgo(); i++) {
        //     fa.goAlgo();
        // }
        // fa.endAlgo();

        

        
        

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

    private static void applyYifanHu(GraphModel graphModel) {
        //Run YifanHuLayout for 100 passes - The layout always takes the current visible view
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        
        // Arrays.toString());
        
        // layout.setOptimalDistance(200f);
        // layout.setConverged(false);
        layout.setConvergenceThreshold(1E-7f);
        System.out.println(layout.isConverged());
        layout.initAlgo();
        System.out.println(layout.isConverged());
        int i = 0; int ticks = 0;
        while (i++ < 10) {
            while (!layout.isConverged()) {
                // System.out.println(layout.isConverged());
                layout.goAlgo(); ticks++;
            }
        }
        System.out.println(ticks);
        layout.endAlgo();
        System.out.println(layout.isConverged());
        
        for (var prop : layout.getProperties()) {
            try {
                var name = prop.getProperty().getName();
                var value = prop.getProperty().getValue();
                System.out.println(name+" = "+value);
            } catch (Exception e) {e.printStackTrace();}
        }
        // runAlgoFor(layout,200);
    }

    private static void applyLayout(GraphModel graphModel) {
        Graph undirectedGraph = graphModel.getUndirectedGraph();
        Modularity modularity = new Modularity();
        modularity.setResolution(0.4);
        modularity.setUseWeight(true);
        modularity.execute(graphModel);

        //Partition with ‘modularity_class’, just created by Modularity algorithm
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();

        Column modColumn = graphModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
        
        Function func2 = appearanceModel.getNodeFunction(modColumn, PartitionElementColorTransformer.class);
        Partition partition2 = ((PartitionFunction) func2).getPartition();

        Palette palette2 = PaletteManager.getInstance().randomPalette(partition2.size(undirectedGraph));

        int i =0;
        for (Object o: partition2.getValues(undirectedGraph)){
            partition2.setColor(o,palette2.getColors()[i]);
            i++;
        }
        appearanceController.transform(func2);

        // appearanceModel.getNo

        // Column rankingCol = graphModel.getNodeTable().getColumn("weight");

        // Function rankingNodeSize = appearanceModel.getNodeFunction(rankingCol, RankingNodeSizeTransformer.class);
        // Function rankingLabelSize = appearanceModel.getNodeFunction(rankingCol, RankingLabelSizeTransformer.class);

        // RankingNodeSizeTransformer rankingNodeSizeTransformer = rankingNodeSize.getTransformer();
        // RankingLabelSizeTransformer rankingLabelSizeTransformer = rankingLabelSize.getTransformer();

        // rankingNodeSizeTransformer.setMinSize(10f);
        // rankingNodeSizeTransformer.setMaxSize(40f);

        // rankingLabelSizeTransformer.setMinSize(0.3f);
        // rankingLabelSizeTransformer.setMaxSize(0.4f);

        // appearanceController.transform(rankingNodeSize);
        // appearanceController.transform(rankingLabelSize);

        // Unused layouts
        

        
        // YifanHuLayout yifanHuStep = new YifanHuLayout(null, new StepDisplacement(1f));
        // yifanHuStep.setGraphModel(graphModel);
        // yifanHuStep.setOptimalDistance(1000f);

        // OpenOrdLayout openOrdLayout = new OpenOrdLayout(null);

        

        // FruchtermanReingold fruchtermanReingoldStep = new FruchtermanReingold(null);


        //FORCE ATLAS INITIALIZATION
        ForceAtlas2 forceAtlasStep = new ForceAtlas2(null);
        forceAtlasStep.resetPropertiesValues();
        forceAtlasStep.setGraphModel(graphModel);
        // forceAtlasStep.setBarnesHutOptimize(true);
        // forceAtlasStep.setBarnesHutTheta(-100d);
        forceAtlasStep.setAdjustSizes(true);
        forceAtlasStep.setLinLogMode(true);
        // forceAtlasStep.setOutboundAttractionDistribution(false);
        forceAtlasStep.setScalingRatio(5d);
        // forceAtlasStep.setGravity(2d);
        forceAtlasStep.setEdgeWeightInfluence(0.45d);

        //RUN FIRST PHASE
        Layout firstAlgo = forceAtlasStep;

        System.out.println("FIRST PHASE EXECUTION");
        
        runAlgoFor(firstAlgo,750);

        //CHANGE PARAMETERS AND RUN SECOND PHASE
        // forceAtlasStep.setScalingRatio(1.5d);
        // forceAtlasStep.setLinLogMode(false);
        // forceAtlasStep.setEdgeWeightInfluence(0.28d);
        // int secondAlgoSteps = 350;
        
        // System.out.println("SECOND PHASE EXECUTION");
        // for (int k = 0; k < secondAlgoSteps; k++)
        // {
        //     firstAlgo.goAlgo();
        // }

        //CHANGE PARAMETERS AND RUN THIRD PHASE
        // forceAtlasStep.setScalingRatio(1.6d);
        // forceAtlasStep.setLinLogMode(true);
        // forceAtlasStep.setEdgeWeightInfluence(0.45d);
        // int thirdAlgoSteps = 1100;
        
        // System.out.println("THIRD PHASE EXECUTION");
        // for (int k = 0; k < thirdAlgoSteps; k++)
        // {
        //     firstAlgo.goAlgo();
        // }

        ExpandLayout expandLayout = new ExpandLayout(null, 5.0);
        expandLayout.setGraphModel(graphModel);
        runAlgoFor(expandLayout, 1);

        LabelAdjust labelAdjustStep = new LabelAdjust(null);
        labelAdjustStep.setGraphModel(graphModel);
        labelAdjustStep.setAdjustBySize(true);
        labelAdjustStep.setSpeed(10.0);
        runAlgoFor(labelAdjustStep, 100);

        System.out.println("ENDING LAYOUT EXECUTION");
        return;
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

    public static void layoutAsTwitchGraph(GraphModel graphModel) {
        
        
        // Unused layouts
        // LabelAdjust labelAdjustStep = new LabelAdjust(null);
        // labelAdjustStep.setGraphModel(graphModel);
        // labelAdjustStep.setAdjustBySize(true);
        // labelAdjustStep.setSpeed(10.0);

        
        // YifanHuLayout yifanHuStep = new YifanHuLayout(null, new StepDisplacement(1f));
        // yifanHuStep.setGraphModel(graphModel);
        // yifanHuStep.setOptimalDistance(1000f);

        // OpenOrdLayout openOrdLayout = new OpenOrdLayout(null);

        // ExpandLayout expandLayout = new ExpandLayout(null, 1.05);

        // FruchtermanReingold fruchtermanReingoldStep = new FruchtermanReingold(null);


        //FORCE ATLAS INITIALIZATION
        ForceAtlas2 forceAtlasStep = new ForceAtlas2(null);
        forceAtlasStep.resetPropertiesValues();
        forceAtlasStep.setGraphModel(graphModel);
        // forceAtlasStep.setBarnesHutOptimize(true);
        // forceAtlasStep.setBarnesHutTheta(-100d);
        forceAtlasStep.setAdjustSizes(true);
        // forceAtlasStep.setLinLogMode(true);
        forceAtlasStep.setOutboundAttractionDistribution(false);
        forceAtlasStep.setScalingRatio(5d);
        forceAtlasStep.setGravity(3d);
        forceAtlasStep.setEdgeWeightInfluence(0.45d);

        //RUN FIRST PHASE
        Layout firstAlgo = forceAtlasStep;
        int firstAlgoSteps = 750;

        firstAlgo.initAlgo();
        
        System.out.println("FIRST PHASE EXECUTION");
        for (int k = 0; k < firstAlgoSteps; k++)
        {
            firstAlgo.goAlgo();
        }

        //CHANGE PARAMETERS AND RUN SECOND PHASE
        forceAtlasStep.setScalingRatio(1.5d);
        forceAtlasStep.setLinLogMode(false);
        forceAtlasStep.setEdgeWeightInfluence(0.28d);
        int secondAlgoSteps = 350;
        
        System.out.println("SECOND PHASE EXECUTION");
        for (int k = 0; k < secondAlgoSteps; k++)
        {
            firstAlgo.goAlgo();
        }

        //CHANGE PARAMETERS AND RUN THIRD PHASE
        forceAtlasStep.setScalingRatio(1.6d);
        // forceAtlasStep.setLinLogMode(true);
        forceAtlasStep.setEdgeWeightInfluence(0.45d);
        int thirdAlgoSteps = 1100;
        
        System.out.println("THIRD PHASE EXECUTION");
        for (int k = 0; k < thirdAlgoSteps; k++)
        {
            firstAlgo.goAlgo();
        }

        System.out.println("ENDING LAYOUT EXECUTION");
    }

    private static void runAlgoFor(Layout layout, int count) {
        layout.initAlgo();
        for (int k = 0; k < count; k++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }
}
