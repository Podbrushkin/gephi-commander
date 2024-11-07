import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.api.Partition;
import org.gephi.appearance.api.PartitionFunction;
import org.gephi.appearance.plugin.PartitionElementColorTransformer;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;
import org.gephi.appearance.plugin.palette.Palette;
import org.gephi.appearance.plugin.palette.PaletteManager;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.filters.plugin.graph.GiantComponentBuilder;
import org.gephi.filters.plugin.graph.KCoreBuilder;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.api.ImportUtils;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.force.yifanHu.YifanHuProportional;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.openord.OpenOrdLayoutBuilder;
import org.gephi.layout.plugin.random.Random;
import org.gephi.layout.plugin.random.RandomLayout;
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
import org.gephi.statistics.plugin.ConnectedComponents;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;
//https://github.com/KiranGershenfeld/VisualizingTwitchCommunities/blob/AutoAtlasGeneration/AtlasGeneration/Java/App.java

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//https://github.com/gephi/gephi-toolkit-demos/tree/master/src/main/java/org/gephi/toolkit/demos
public class GephiStarter {
    
    public static void main(String[] args) {
        var options = JsonParser.parseReader(new InputStreamReader(System.in)).getAsJsonObject();
        // System.out.println(options.toString());
        // System.out.println(options.keySet());
        var file = new File(options.get("InFile").getAsString());
        
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get models and controllers for this new workspace - will be useful later
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        
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

        for (String key : options.keySet()) {
            switch (key) {
                case "statistics":
                    var statsOpts = options.get("statistics").getAsJsonArray();
                    applyStatistics(statsOpts);
                    break;
                case "filters":
                    applyFilters(options.get("filters").getAsJsonArray());
                    break;
                case "layouts":
                    applyLayouts(options.get("layouts").getAsJsonArray());
                    break;
                case "preview":
                    setGraphPreview(options.getAsJsonObject("preview"));
                    break;
                case "colorNodesByColumn":
                    colorNodesByColumn(graphModel, options.get("colorNodesByColumn").getAsString());
                    break;
                case "sizeNodesByDegree":
                    sizeNodesByDegree(options.getAsJsonObject("sizeNodesByDegree"));
                    break;
                case "print":
                    printInfo(options.get("print").getAsJsonArray());
                    break;
                case "export":
                    export(options.getAsJsonObject("export"));
                    break;
                default:
                    System.out.println("Unknown root element "+key);
                    break;
            }
        }
        
    }

    private static void applyStatistics(JsonArray options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        for (var el : options) {
            var name = el.getAsString();
            switch (name) {
                case "Modularity" : {
                    var modularity = new Modularity();
                    modularity.execute(graphModel);
                    break;
                }
                case "ConnectedComponents" : {
                    var connectedComponents = new ConnectedComponents();
                    connectedComponents.setDirected(false);
                    connectedComponents.execute(graphModel);
                    break;
                }
                default : System.out.println("No such statistics: "+name);
            }
        }
    }

    private static void applyFilters(JsonArray filters) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var queries = new ArrayList<Query>();
        for (var el : filters) {
            var filterOptions = el.getAsJsonObject();
            var name = filterOptions.get("name").getAsString();
            switch (name) {
                case "GiantComponent":
                    queries.add(getFilterGiantComponent());
                    break;
                case "Degree":
                    queries.add(getFilterDegree(filterOptions));
                    break;
                case "K-core":
                    queries.add(getKcore(graphModel,filterOptions));
                    break;
                case "Partition":
                    queries.add(getPartitionFilter(graphModel,filterOptions));
                    break;
                    
                default:
                    System.out.printf("Filter \"%s\" not found!%n", name);
                    break;
            }
        }
        var filterController = Lookup.getDefault().lookup(FilterController.class);
        // if (queries.size() >= 1) {
        for (int i = 1; i < queries.size(); i++) {
            var q = queries.get(i);
            var prevQ = queries.get(i-1);
            filterController.setSubQuery(prevQ,q);
            System.out.printf("Set %s as subquery of %s%n",q.getName(),prevQ.getName());
        }
        filterController.add(queries.get(0));
        filterController.filterVisible(queries.get(0));
    }
    private static void applyLayouts(JsonArray layouts) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        for (var layoutEl : layouts) {
            var layout = layoutEl.getAsJsonObject();
            // System.out.println(layout.getAsString());
            var name = layout.get("name").getAsString();
            var stepsEl = layout.get("steps");
            int steps = stepsEl == null ? 200 : stepsEl.getAsInt();

            switch (name) {
                case "YifanHu" : {
                    applyYifanHu(graphModel, steps); break;
                }
                case "YifanHuProportional" : {
                    applyYifanHuProportional(graphModel, steps); break;
                }
                case "ForceAtlas2" : {
                    applyForceAtlas2(graphModel, layout); break;
                }
                case "OpenOrd" : {
                    applyOpenOrd(graphModel, layout); break;
                }
                case "RandomLayout" : {
                    applyRandomLayout(graphModel, layout); break;
                }
                //default : applyLayout(graphModel); 
    
            }
        }
    }

    private static void export(JsonObject options) {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        var pc = Lookup.getDefault().lookup(ProjectController.class);
        var workspace = pc.getCurrentWorkspace();
        String filename = options.has("file") ? options.get("file").getAsString() : "gephi.pdf";
        File outFile = new File(filename);
        try {
            
            if (outFile.getName().endsWith(".png") &&
                options.has("resolution")) {
                
                var res = options.getAsJsonArray("resolution");
                int x = res.get(0).getAsInt();
                int y = res.size() == 2 ? res.get(1).getAsInt() : x;
                var pngExporter = new PNGExporter();
                pngExporter.setWorkspace(workspace);
                pngExporter.setWidth(x);
                pngExporter.setHeight(y);
                ec.exportFile(outFile, pngExporter);

            } else {
                ec.exportFile(outFile);
            }
            System.out.println("Exported to "+outFile);
            
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    private static void printInfo(JsonArray options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        for (var el : options) {
            var name = el.getAsString();
            switch (name) {
                case "count":
                    var graphVis = graphModel.getGraphVisible();
                    System.out.println("Nodes: " + graphVis.getNodeCount() + " Edges: " + graphVis.getEdgeCount());
                    break;
                case "nodeColumns":
                    System.out.println("Node columns:");
                    System.out.println("id\ttitle\ttype");
                    for (var col : graphModel.getNodeTable()) {
                        System.out.printf("%s\t%s\t%s%n",col.getId(),col.getTitle(),col.getTypeClass().getSimpleName());
                    }

                default:
                    System.out.println("No such printInfo: "+name);
                    break;
            }
        }
    }

    private static Query getFilterGiantComponent() {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var giantComponent = new GiantComponentBuilder.GiantComponentFilter();
        
        giantComponent.init(graphModel.getGraphVisible());
        var query = filterController.createQuery(giantComponent);
        // filterController.filterVisible(query);
        var view = filterController.filter(query);
        graphModel.setVisibleView(view);
        return query;
    }
    private static Query getFilterDegree(JsonObject filterOptions) {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        DegreeRangeFilter degreeFilter = new DegreeRangeFilter();
        degreeFilter.init(graphModel.getGraphVisible());

        int minDegree = filterOptions.has("minDegree") ? filterOptions.get("minDegree").getAsInt() : 2;
        degreeFilter.setRange(new Range(minDegree, Integer.MAX_VALUE));
        Query query = filterController.createQuery(degreeFilter);
        var view = filterController.filter(query);
        graphModel.setVisibleView(view);
        return query;
    }

    private static Query getKcore(GraphModel graphModel, JsonObject filterOptions) {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        
        int minDegree = filterOptions.has("minDegree") ? filterOptions.get("minDegree").getAsInt() : 2;
        var filter = new KCoreBuilder.KCoreFilter();
        filter.filter(graphModel.getUndirectedGraphVisible());
        filter.setK(minDegree);
        var query = filterController.createQuery(filter);
        return query;
    }

    private static Query getPartitionFilter(GraphModel graphModel, JsonObject filterOptions) {
        var columnId = filterOptions.get("columnId").getAsString();
        var partitions =  filterOptions.get("partitions").getAsJsonArray();

        var graph = graphModel.getUndirectedGraphVisible();

        var appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
        var column = graphModel.getNodeTable().getColumn(columnId);
        var nodePartition = appearanceModel.getNodePartition(column);
        var coll = nodePartition.getSortedValues(graph);
        // nodePartition.percentage(filterOptions, graph);
        System.out.printf("Distinct values of column %s:%n",columnId);
        System.out.println("value\tpercentage");
        int i = 0;
        for (var el : coll) {
            float perc = nodePartition.percentage(el, graph);
            System.out.printf("%s\t%s%n",el,perc);
            if (i++ == 20) {
                System.out.println("...and more");
                break;
            }
        }
        
        var partitionFilter = new NodePartitionFilter(appearanceModel,appearanceModel.getNodePartition(column));
        System.out.printf("partitionFilter.getParts(): %s%n",partitionFilter.getParts());
        partitionFilter.unselectAll();
        for (var p : partitions) {
            if (p.isJsonPrimitive()) {
                partitionFilter.addPart(p.getAsInt());
            } else {
                partitionFilter.addPart(p.getAsString());
            }
        }
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        var query = filterController.createQuery(partitionFilter);
        return query;
    }
    private static void applyForceAtlas2(GraphModel graphModel, JsonObject layoutOptions) {
        int steps = layoutOptions.has("steps") ? layoutOptions.get("steps").getAsInt() : 200;
        
        ForceAtlas2 layout = new ForceAtlas2(null);
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        if (layoutOptions.has("scaling")) {
            var scaling = layoutOptions.get("scaling").getAsDouble();
            layout.setScalingRatio(scaling);
        }
        
        runAlgoFor(layout, steps);
        return;
    }

    private static void applyYifanHu(GraphModel graphModel, int steps) {
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        // layout.setOptimalDistance(100f);
        
        for (var prop : layout.getProperties()) {
            try {
                var name = prop.getProperty().getName();
                var value = prop.getProperty().getValue();
                System.out.println(name+" = "+value);
            } catch (Exception e) {e.printStackTrace();}
        }
        runAlgoFor(layout,steps);
    }

    private static void applyYifanHuProportional(GraphModel graphModel, int steps) {
        var layout =  new YifanHuProportional().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        runAlgoFor(layout, steps);
    }
    
    private static void applyOpenOrd(GraphModel graphModel, JsonObject props) {
        var layout =  new OpenOrdLayoutBuilder().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        if (props.has("steps")) {
            runAlgoFor(layout, props.get("steps").getAsInt());
        }
        else {
            int maxSteps = props.has("maxSteps") ? props.get("maxSteps").getAsInt() : Integer.MAX_VALUE;
            runAlgoForMaximum(layout, maxSteps);
        }
        
    }
    private static void applyRandomLayout(GraphModel graphModel, JsonObject props) {
        int size = props.has("size") ? props.get("size").getAsInt() : 200;
        var layout =  new RandomLayout(new Random(), size);
        layout.setGraphModel(graphModel);
        // layout.resetPropertiesValues();
        if (props.has("steps")) {
            runAlgoFor(layout, props.get("steps").getAsInt());
        }
        else {
            int maxSteps = props.has("maxSteps") ? props.get("maxSteps").getAsInt() : Integer.MAX_VALUE;
            runAlgoForMaximum(layout, maxSteps);
        }
    }


    private static void sizeNodesByDegree(JsonObject rankingOptions) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();

        Function degreeRanking = appearanceModel.getNodeFunction(graphModel.defaultColumns()
            .degree(), RankingNodeSizeTransformer.class);
        RankingNodeSizeTransformer sizeTransformer = 
            (RankingNodeSizeTransformer) degreeRanking.getTransformer();

        int nodeMinSize = rankingOptions.has("minSize") ? rankingOptions.get("minSize").getAsInt() : 5;
        int nodeMaxSize = rankingOptions.has("maxSize") ? rankingOptions.get("maxSize").getAsInt() : nodeMinSize * 4;
        
        sizeTransformer.setMinSize(nodeMinSize);
        sizeTransformer.setMaxSize(nodeMaxSize);

        appearanceController.transform(degreeRanking);
    }
    private static void colorNodesByColumn(GraphModel graphModel, String columnName) {
        
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        DirectedGraph graph = graphModel.getDirectedGraph();

        Column column = graphModel.getNodeTable().getColumn(columnName);
        Function func = appearanceModel.getNodeFunction(column, PartitionElementColorTransformer.class);
        Partition partition = ((PartitionFunction) func).getPartition();
        Palette palette = PaletteManager.getInstance().generatePalette(partition.size(graph));
        partition.setColors(graph, palette.getColors());
        appearanceController.transform(func);

    }

    
    public static void setGraphPreview(JsonObject previewOptions) {
        //Preview
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        //Node Label Properties
        model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN, 8));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.WHITE));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 4.0f);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_OPACITY, 40);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_COLOR, new DependantColor(Color.BLACK));
        
        //Edge Properties
        model.getProperties().putValue(PreviewProperty.SHOW_EDGES, Boolean.TRUE);
        
        var edgeColorEl = previewOptions.get("edgeColor");
        if (edgeColorEl != null) {
            Mode mode = Mode.valueOf(edgeColorEl.getAsString().toUpperCase());
            model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(mode));
        } else {
            model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new DependantOriginalColor(Color.WHITE));
        }


        // model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Mode.SOURCE));
        // model.getProperties().putValue(PreviewProperty.EDGE_THICKNESS, 1.0);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, Boolean.TRUE);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MIN, 1);
        // model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MAX, 20);
        model.getProperties().putValue(PreviewProperty.EDGE_OPACITY, 60);
        // model.getProperties().putValue(PreviewProperty.ARROW_SIZE, 5);
        


        //Image Properties
        if (previewOptions.has("bgColor")) {
            var colorName = previewOptions.get("bgColor").getAsString();
            var color = ImportUtils.parseColor(colorName);
            if (color != null) {
                model.getProperties().putValue(PreviewProperty.BACKGROUND_COLOR, color);
            } else {
                System.out.println("Such color haven't been found: "+colorName);
            }
        }
    }

    private static void runAlgoFor(Layout layout, int steps) {
        System.out.printf("Applying layout %s with %s steps %n",layout.getClass().getSimpleName(), steps); 
        layout.initAlgo();
        for (int k = 0; k < steps; k++) {
            layout.goAlgo();
        }
        layout.endAlgo();
    }
    private static void runAlgoForMaximum(Layout layout, int maxSteps) {
        System.out.printf("Applying layout %s with no more than %s steps...%n",layout.getClass().getSimpleName(), maxSteps); 
        layout.initAlgo();
        int stepCount = 1;
        for (; stepCount <= maxSteps && layout.canAlgo(); stepCount++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        System.out.printf("It was %s steps.%n",stepCount);
    }
}
