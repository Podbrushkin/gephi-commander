import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
import org.gephi.filters.plugin.attribute.AttributeEqualBuilder;
import org.gephi.filters.plugin.graph.DegreeRangeBuilder.DegreeRangeFilter;
import org.gephi.filters.plugin.graph.GiantComponentBuilder;
import org.gephi.filters.plugin.graph.KCoreBuilder;
import org.gephi.filters.plugin.partition.PartitionBuilder;
import org.gephi.filters.plugin.partition.PartitionBuilder.EdgePartitionFilter;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.filters.spi.ElementFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.api.ImportUtils;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.force.yifanHu.YifanHuProportional;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.noverlap.NoverlapLayout;
import org.gephi.layout.plugin.noverlap.NoverlapLayoutBuilder;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//https://github.com/gephi/gephi-toolkit-demos/tree/master/src/main/java/org/gephi/toolkit/demos
public class GephiStarter {
    
    public static void main(String[] args) {
        var options = JsonParser.parseReader(new InputStreamReader(System.in)).getAsJsonArray();
        // System.out.println(options.toString());
        // System.out.println(options.keySet());
        

        for (var opEl : options) {
            var op = opEl.getAsJsonObject();
            var opName = op.get("op").getAsString();
            switch (opName) {
                case "import":
                    importData(op);
                    break;
                case "statistics":
                    var statsOpts = op.get("values").getAsJsonArray();
                    applyStatistics(statsOpts);
                    break;
                case "filters":
                    // tryFilters();
                    applyFilters(op.get("values").getAsJsonArray());
                    break;
                case "layouts":
                    applyLayouts(op.get("values").getAsJsonArray());
                    break;
                case "preview":
                    setGraphPreview(op);
                    break;
                case "colorNodesBy":
                    colorNodesByColumn(op.get("columnName").getAsString());
                    break;
                case "colorEdgesBy":
                    colorEdgesByColumn(op.get("columnName").getAsString());
                    break;
                case "sizeNodesByDegree":
                    sizeNodesByDegree(op);
                    break;
                case "print":
                    printInfo(op.get("values").getAsJsonArray());
                    break;
                case "export":
                    export(op);
                    break;
                default:
                    System.out.println("Unknown root element "+opName);
                    break;
            }
        }
        
    }
    private static void tryFilters() {
        var file = new File ("C:\\Users\\user\\AppData\\Local\\Programs\\neo4j-community-5.22.0\\dataMoviesBuiltin\\graphviz.dot");

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
        
        printCounts(graphModel.getDirectedGraph());






        
        
        // FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
		// DirectedGraph graph = graphModel.getDirectedGraphVisible();
        
        System.out.println("Before filtering\nNodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        // Keep only nodes with group=Person
        // AppearanceModel appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
        NodePartitionFilter partitionFilter = new NodePartitionFilter(appearanceModel,appearanceModel.getNodePartition(graphModel.getNodeTable().getColumn("group")));
        partitionFilter.unselectAll();
        partitionFilter.addPart("Person");
        Query query2 = filterController.createQuery(partitionFilter);
        // GraphView view2 = filterController.filter(query2);
        // graphModel.setVisibleView(view2);    //Set the filter result as the visible view
        // graph = graphModel.getDirectedGraphVisible();   // Update var to latest
        System.out.println("After PartitionFilter\nNodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());
        
        
        // K-core
        var filter = new KCoreBuilder.KCoreFilter();
        filter.filter(graph);
        filter.setK(1);
        var query0 = filterController.createQuery(filter);
        // GraphView view0 = filterController.filter(query0);
        // graphModel.setVisibleView(view0);
        System.out.println("After K-Core\nNodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        filterController.setSubQuery(query0, query2);
        filterController.filterVisible(query0);
        System.out.println("After subquery\nNodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

        /* //Combine two filters with AND - Set query and query2 as sub-query of AND
        IntersectionOperator intersectionOperator = new IntersectionOperator();
        Query query3 = filterController.createQuery(intersectionOperator);
        filterController.setSubQuery(query3, query2);
        filterController.setSubQuery(query3, query0);
        GraphView view3 = filterController.filter(query3);
        graphModel.setVisibleView(view3);    //Set the filter result as the visible view */

        try {

            /* ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            var exporter = (GraphExporter) ec.getExporter("png");
            exporter.setExportVisible(true);
            // exporter.setWorkspace(workspace);
            ec.exportFile(new File("delme.png"), exporter); */

            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            ec.exportFile(new File("delme.pdf"));
        
        // graph.

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        /* System.out.println("How do we get this graph again?");
        GraphModel graphModelx = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        graphModelx.setVisibleView(view0);
        printCounts(graphModelx.getDirectedGraphVisible());
        printCounts(graphModelx.getGraphVisible());
        printCounts(graphModelx.getGraph(graphModelx.getVisibleView()));
        printCounts(graphModel.getDirectedGraphVisible());
        printCounts(graphModel.getGraphVisible());
        printCounts(graphModel.getGraph(graphModel.getVisibleView()));
        // var filterControllerx = Lookup.getDefault().lookup(FilterController.class); */
        

    }
    private static void printCounts(Graph graph) {
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());
    }


    private static void importData(JsonObject options) {
        var file = new File(options.get("file").getAsString());
        
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
            container.getLoader().setAllowParallelEdge(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);
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
        if (filters.size() == 0) {return;}
        var queriesReversedOrder = new ArrayList<Query>();
        
        // for (var el : filters) {
        for (int i = filters.size()-1; i >= 0; i--) {
            var el = filters.get(i);
            var filterOptions = el.getAsJsonObject();
            var name = filterOptions.get("name").getAsString();
            switch (name) {
                case "GiantComponent":
                    queriesReversedOrder.add(getFilterGiantComponent());
                    break;
                case "Degree":
                    queriesReversedOrder.add(getFilterDegree(filterOptions));
                    break;
                case "K-core":
                    queriesReversedOrder.add(getKcore(filterOptions));
                    break;
                case "Partition":
                    queriesReversedOrder.add(getPartitionFilter(filterOptions));
                    break;
                case "AttributeEquals":
                    queriesReversedOrder.add(getAttributeEqualsFilter(filterOptions));
                    break;
                    
                default:
                    System.out.printf("Filter \"%s\" not found!%n", name);
                    break;
            }
        }
        var filterController = Lookup.getDefault().lookup(FilterController.class);
        // if (queries.size() >= 1) {
        // Last filter you want to apply should be root filter, first filter should be deepest
        var filterTree = queriesReversedOrder.stream().map(Query::getName).collect(Collectors.joining(" -> "));
        System.out.println(filterTree);
            
        // for (int i = queries.size()-1; i > 0; i--) {
        for (int i = 0; i < queriesReversedOrder.size()-1; i++) {
            var parentQuery = queriesReversedOrder.get(i);
            var subQuery = queriesReversedOrder.get(i+1);
            filterController.setSubQuery(parentQuery,subQuery);
            // System.out.printf("Now %s has a subquery %s%n",parentQuery.getName(),subQuery.getName());
        }
        // filterController.add(queries.get(0));
        filterController.filterVisible(queriesReversedOrder.get(0));
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
                    applyYifanHu(graphModel, layout); break;
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
                case "Noverlap" : {
                    applyNoverlapLayout(layout); break;
                }
                default : System.out.println("No such layout: "+name);
    
            }
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
                    graphVis = graphModel.getGraph();
                    System.out.println("Nodes: " + graphVis.getNodeCount() + " Edges: " + graphVis.getEdgeCount());
                    graphVis = graphModel.getDirectedGraphVisible();
                    System.out.println("Nodes: " + graphVis.getNodeCount() + " Edges: " + graphVis.getEdgeCount());
                    graphVis = graphModel.getGraph(graphModel.getVisibleView());
                    System.out.println("Nodes: " + graphVis.getNodeCount() + " Edges: " + graphVis.getEdgeCount());
                    break;
                case "nodeColumns":
                    System.out.println("Node columns:");
                    System.out.println("id\ttitle\ttype");
                    for (var col : graphModel.getNodeTable()) {
                        System.out.printf("%s\t%s\t%s%n",col.getId(),col.getTitle(),col.getTypeClass().getSimpleName());
                    }
                    break;
                case "edgeColumns":
                    System.out.println("Edge columns:");
                    System.out.println("id\ttitle\ttype");
                    for (var col : graphModel.getEdgeTable()) {
                        System.out.printf("%s\t%s\t%s%n",col.getId(),col.getTitle(),col.getTypeClass().getSimpleName());
                    }
                    break;
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

    private static Query getKcore(JsonObject filterOptions) {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel().getGraphVisible();
        int minDegree = filterOptions.has("minDegree") ? filterOptions.get("minDegree").getAsInt() : 2;
        var filter = new KCoreBuilder.KCoreFilter();
        filter.filter(graphModel);
        filter.setK(minDegree);
        var query = filterController.createQuery(filter);
        return query;
    }

    private static Query getPartitionFilter(JsonObject options) {
        
        var type = options.get("type").getAsString();
        var columnId = options.get("columnId").getAsString();
        // var values =  options.get("values").getAsJsonArray();
        var values = options.has("values") ? 
            options.get("values").getAsJsonArray() : new JsonArray();
        var indices = new ArrayList<Integer>();
        if (options.has("indices")) {
             options.get("indices").getAsJsonArray().iterator().forEachRemaining((el)->indices.add(el.getAsInt()));
        }
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        var appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
        // Column column = graphModel.getNodeTable().getColumn(columnId);
        /* Column column = switch (type) {
            case "node" -> graphModel.getNodeTable().getColumn(columnId);
            case "edge" -> graphModel.getEdgeTable().getColumn(columnId);
            default -> {throw new IllegalStateException("Type should be node or edge, not "+type);}
        }; */

        // var nodePartition = appearanceModel.getNodePartition(column);
        // printPartitionInfo(nodePartition);
        PartitionBuilder.PartitionFilter filter = null;
        Column column = null;
        switch (type.toLowerCase()) {
            case "node":
                column = graphModel.getNodeTable().getColumn(columnId);
                filter = new NodePartitionFilter(appearanceModel,appearanceModel.getNodePartition(column));
                break;
            case "edge":
                column = graphModel.getEdgeTable().getColumn(columnId);
                filter = new EdgePartitionFilter(appearanceModel,appearanceModel.getEdgePartition(column));
                break;
            default:
                throw new IllegalStateException("Type should be node or edge, not "+type);
        }
        Class columnType = column.getTypeClass();
        /* BiFunction<Class,JsonElement,Object> getValFromJsonEl = (targetType, jsonEl) -> {
            if (Number.class.isAssignableFrom(targetType)) {
                return jsonEl.getAsNumber();
            }
            if (String.class.isAssignableFrom(targetType)) {
                return jsonEl.getAsString();
            }
            if (Boolean.class.isAssignableFrom(targetType)) {
                return jsonEl.getAsBoolean();
            }
            throw new IllegalStateException("Unknown type: "+targetType);
        }; */
        // 
        printPartitionInfo(filter.getPartition());
        filter.unselectAll();
        if (indices.size() > 0) {
            var sortedValuesColl = filter.getPartition().getSortedValues(graphModel.getGraphVisible());
            @SuppressWarnings("unchecked")
            var sortedValues = new ArrayList<Integer>(sortedValuesColl);
            for (int i = 0; i < sortedValues.size(); i++) {
                if (indices.contains(i)) {
                    filter.addPart(sortedValues.get(i));
                }
            }

        }
        
        for (var p : values) {
            if (Number.class.isAssignableFrom(columnType)) {
                filter.addPart(p.getAsInt());
            }
            if (String.class.isAssignableFrom(columnType)) {
                filter.addPart(p.getAsString());
            }
            if (Boolean.class.isAssignableFrom(columnType)) {
                filter.addPart(p.getAsBoolean());
            }
            
        }
        System.out.printf("partitionFilter.getParts(): %s%n",filter.getParts());
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        var query = filterController.createQuery(filter);
        return query;
    }

    private static void printPartitionInfo(Partition partition) {
        var graph = Lookup.getDefault().lookup(GraphController.class).getGraphModel().getGraphVisible();
        var coll = partition.getSortedValues(graph);
        var columnId = partition.getColumn().getId();
        // nodePartition.percentage(filterOptions, graph);
        System.out.printf("Distinct values of column %s:%n",columnId);
        System.out.println("value\tpercentage");
        int i = 0;
        for (var el : coll) {
            float perc = partition.percentage(el, graph);
            System.out.printf("%s\t%s%n",el,perc);
            if (i++ == 20) {
                System.out.println("...and more");
                break;
            }
        }
    }

    private static Query getAttributeEqualsFilter(JsonObject options) {
        String nodeOrEdge = options.get("type").getAsString();
        String columnId = options.get("columnId").getAsString();
        JsonElement value = options.get("value");

        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        // Column column = type.equals("node") ? 
        //     graphModel.getNodeTable().getColumn(columnId) : 
        //     graphModel.getEdgeTable().getColumn(columnId);
        
        
        var filterController = Lookup.getDefault().lookup(FilterController.class);
        
        Column column = switch (nodeOrEdge) {
            case "node" -> graphModel.getNodeTable().getColumn(columnId);
            case "edge" -> graphModel.getEdgeTable().getColumn(columnId);
            default -> {throw new IllegalStateException("Type should be node or edge, not "+nodeOrEdge);}
        };
        Class columnType = column.getTypeClass();
        System.out.println("columnType="+columnType);
        ElementFilter filterResult = null;
        if (Number.class.isAssignableFrom(columnType)) {
            System.out.println("Its number");
            
            var filter = new AttributeEqualBuilder.EqualNumberFilter.Node(column);
            switch (columnType.getSimpleName()) {
                case "Integer":
                    filter.setMatch(value.getAsInt());
                    break;
                case "Double":
                    filter.setMatch(value.getAsDouble());
                    break;
            
                default:
                    throw new IllegalStateException("Unknown column type: "+columnType);
            }

            
            
            filterResult = filter;
            
        }
        else if (String.class.isAssignableFrom(columnType)) {
            System.out.println("Its string");
            var filter = switch (nodeOrEdge) {
                case "node" -> new AttributeEqualBuilder.EqualStringFilter.Node(column);
                case "edge" -> new AttributeEqualBuilder.EqualStringFilter.Edge(column);
                default -> {throw new IllegalStateException("Type should be node or edge, not "+nodeOrEdge);}
            };
            filter.setUseRegex(false);
            filter.setPattern(value.getAsString());
            filterResult = filter;
        }
        filterResult.init(graphModel.getGraphVisible());
        var query = filterController.createQuery(filterResult);
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

    private static void applyYifanHu(GraphModel graphModel, JsonObject layoutOptions) {
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
        if (layoutOptions.has("steps")) {
            var steps = layoutOptions.get("steps").getAsInt();
            runAlgoFor(layout,steps);
        } else {
            int maxSteps = layoutOptions.has("maxSteps") ? layoutOptions.get("maxSteps").getAsInt() : 600;
            runAlgoForMaximum(layout, maxSteps);
        }
        
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
    private static void applyNoverlapLayout(JsonObject props) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // var layout = (NoverlapLayout)(new NoverlapLayoutBuilder().buildLayout());
        var layout = new NoverlapLayout(new NoverlapLayoutBuilder());
        layout.setGraphModel(graphModel);
        if (props.has("margin")) {
            double margin = props.get("margin").getAsDouble();
            layout.setMargin(margin);
        }
        runAlgoForMaximum(layout, 600);
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
    private static void colorNodesByColumn(String columnName) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
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
    private static void colorEdgesByColumn(String columnName) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        DirectedGraph graph = graphModel.getDirectedGraph();

        Column column = graphModel.getEdgeTable().getColumn(columnName);
        Function func = appearanceModel.getEdgeFunction(column, PartitionElementColorTransformer.class);
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
    private static void export(JsonObject options) {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        var pc = Lookup.getDefault().lookup(ProjectController.class);
        var workspace = pc.getCurrentWorkspace();
        
        String filename = options.has("file") ? options.get("file").getAsString() : "gephi.pdf";
        File outFile = new File(filename);
        String extension = outFile.getName().replaceAll("^.*\\.","");
        try {
            
            if (extension.equals("png") &&
                options.has("resolution")) {
                
                var res = options.getAsJsonArray("resolution");
                int x = res.get(0).getAsInt();
                int y = res.size() == 2 ? res.get(1).getAsInt() : x;
                var pngExporter = new PNGExporter();
                pngExporter.setWorkspace(workspace);
                pngExporter.setWidth(x);
                pngExporter.setHeight(y);
                ec.exportFile(outFile, pngExporter);
            } else if (extension.equals("pdf")) {
                ec.exportFile(outFile);
            } else {
                var exporter = (GraphExporter) ec.getExporter(extension);
                exporter.setWorkspace(workspace);
                exporter.setExportVisible(true);
                ec.exportFile(outFile, exporter);
            }
            System.out.println("Exported to "+outFile);
            
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
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
