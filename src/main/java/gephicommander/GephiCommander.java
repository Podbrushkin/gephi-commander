package gephicommander;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.api.Partition;
import org.gephi.appearance.api.PartitionFunction;
import org.gephi.appearance.plugin.PartitionElementColorTransformer;
import org.gephi.appearance.plugin.RankingElementColorTransformer;
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
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.EdgeMergeStrategy;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.api.ImportUtils;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.force.yifanHu.YifanHuProportional;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.fruchterman.FruchtermanReingoldBuilder;
import org.gephi.layout.plugin.noverlap.NoverlapLayout;
import org.gephi.layout.plugin.noverlap.NoverlapLayoutBuilder;
import org.gephi.layout.plugin.openord.OpenOrdLayoutBuilder;
import org.gephi.layout.plugin.random.Random;
import org.gephi.layout.plugin.random.RandomLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutProperty;
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
import org.openide.nodes.Node.Property;
import org.openide.util.Lookup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

//https://github.com/KiranGershenfeld/VisualizingTwitchCommunities/blob/AutoAtlasGeneration/AtlasGeneration/Java/App.java
public class GephiCommander {
    
    public static void main(String[] args) {
        var options = JsonParser.parseReader(new InputStreamReader(System.in)).getAsJsonArray();
        Locale.setDefault(Locale.ENGLISH);  // Ignore Gephi localization
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
                    applyFilters(op.get("values").getAsJsonArray());
                    break;
                case "layouts":
                    applyLayouts(op.get("values").getAsJsonArray());
                    break;
                case "preview":
                    setGraphPreview(op);
                    break;
                case "colorNodesBy":
                    colorNodesByColumn(op);
                    break;
                case "colorEdgesBy":
                    colorEdgesByColumn(op.get("column").getAsString());
                    break;
                case "sizeNodesBy":
                    sizeNodesByColumn(op);
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
    
    private static void printCounts(Graph graph) {
        System.out.println("Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());
    }


    private static void importData(JsonObject options) {
        var file = new File(options.get("file").getAsString());
        
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        
        //Import file       
        Container container;
        try {
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDirectionDefault.DIRECTED);   //Force DIRECTED
            container.getLoader().setAllowParallelEdge(true);
            container.getLoader().setEdgesMergeStrategy(EdgeMergeStrategy.NO_MERGE);
            container.getLoader().setAutoScale(false);
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
        // filterController.filterVisible(queriesReversedOrder.get(0));
        var view = filterController.filter(queriesReversedOrder.get(0));
        var gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        gm.setVisibleView(view);
        System.out.println("COUNTS after filtering!!!");
        printCounts(gm.getGraphVisible());
        
        
    }
    private static void applyLayouts(JsonArray layouts) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        for (var layoutEl : layouts) {
            var options = layoutEl.getAsJsonObject();
            var name = options.get("name").getAsString();

            switch (name) {
                case "YifanHu" : {
                    applyYifanHu(graphModel, options); break;
                }
                case "YifanHuProportional" : {
                    applyYifanHuProportional(graphModel, options); break;
                }
                case "ForceAtlas2" : {
                    applyForceAtlas2(graphModel, options); break;
                }
                case "OpenOrd" : {
                    applyOpenOrd(graphModel, options); break;
                }
                case "RandomLayout" : {
                    applyRandomLayout(graphModel, options); break;
                }
                case "Noverlap" : {
                    applyNoverlapLayout(options); break;
                }
                case "FruchtermanReingold" : {
                    applyFruchtermanReingoldLayout(options); break;
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
                    // var attrs = graphModel.getGraphVisible().getNodes().iterator().next().getAttributes();

                    // System.out.println(Arrays.toString(attrs));
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
                case "nodeCoordinates":
                    var jsonObj = printNodeCoordinates();
                    System.out.println(jsonObj);
                    break;
                default:
                    System.out.println("No such printInfo: "+name);
                    break;
            }
        }
    }

    /*
     * With margin=0.1 you can find bounds in which 90% of nodes will fit
     */
    static JsonObject getGraphBounds(Graph graph, Float margin) {
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        for (var node : graph.getNodes()) {
            xs.add(node.x());
            ys.add(node.y());
        }
        Collections.sort(xs);
        Collections.sort(ys);
        
        int overallNodes = xs.size();
        int amountOfNodesToIgnore = (int)Math.floor(overallNodes*margin);
        xs = xs.subList(amountOfNodesToIgnore, overallNodes-amountOfNodesToIgnore);
        ys = ys.subList(amountOfNodesToIgnore, overallNodes-amountOfNodesToIgnore);
        // System.out.printf("overallNodes=%s, amountOfNodesToIgnore=%s%n",overallNodes,amountOfNodesToIgnore);


        float xMin = xs.get(0);
        float xMax = xs.get(xs.size()-1);
        float yMin = ys.get(0);
        float yMax = ys.get(ys.size()-1);
        float graphWidth = xMax-xMin;
        float graphHeight = yMax-yMin;
        var obj = new JsonObject();
        obj.addProperty("xMin", xMin);
        obj.addProperty("xMax", xMax);
        obj.addProperty("yMin", yMin);
        obj.addProperty("yMax", yMax);
        obj.addProperty("graphWidth", graphWidth);
        obj.addProperty("graphHeight", graphHeight);
        System.out.println("xMin="+xMin);
        return obj;
    }
    static JsonObject getGraphBounds(Float margin) {
        var graph =Lookup.getDefault().lookup(GraphController.class).getGraphModel().getGraphVisible();
        return getGraphBounds(graph, margin);
    }
    static JsonObject getGraphBounds(Graph graph){
        return getGraphBounds(graph, 0f);
    }

    static Node getNodeById(Object id) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        return graphModel.getGraph().getNode(id);
    }
    private static JsonObject printNodeCoordinates() {
        System.out.println("Entered printNodeCoordinates()...");
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var graph = graphModel.getUndirectedGraph();
        
        
        // System.out.println("bounds for 100% and 90% of nodes:");
        // System.out.println(getGraphBounds(graph));
        // System.out.println(getGraphBounds(graph,0.1f));
        

        /* // find bounds of 90% y-positive nodes
        var positiveYs = ys.stream().filter((y) -> y >= 0).collect(Collectors.toList());
        System.out.printf("There are %s positive y's%n",positiveYs.size());
        Float boundCount = positiveYs.size() * 0.9f;
        var boundY = positiveYs.stream().skip(boundCount.longValue()).findFirst().get();
        System.out.printf("%s %s %n",boundCount,boundY); */


        
        // float xMedian = xs.get(xs.size()/2);
        // float xGephiCenterRel = Math.abs(xMin)/(xMax-xMin);
        
        // double xMiddle = xs.stream().mapToDouble(Double::valueOf).sum() / xs.size();


        
        // float yMedian = ys.get(ys.size()/2);
        // float yGephiCenterRel = Math.abs(yMin)/(yMax-yMin);
        
        // double yMiddle = ys.stream().mapToDouble(Double::valueOf).sum() / ys.size();

        // var graphBounds = new Rectangle2D.Float(xMin,yMin,graphWidth,graphHeight);
        
        // System.out.println(graphBounds);

        var nodes = new ArrayList<Node>(graph.getNodes().toCollection());
        
        var fromLeftComp = Comparator.comparing(Node::x);
        var fromRightComp = Comparator.comparing(Node::x).reversed();
        var fromTopComp = Comparator.comparing(Node::y);
        var fromBottomComp = Comparator.comparing(Node::y).reversed();

        final int thresholdCountNodes = 1;
        
        
        //TODO: handle if threshold > nodes.size
        nodes.sort(fromLeftComp);
        Float fromLeftReachedAt = nodes.get(thresholdCountNodes-1).x();
        
        
        nodes.sort(fromRightComp);
        Float fromRightReachedAt = nodes.get(thresholdCountNodes-1).x();
        

        nodes.sort(fromTopComp);
        Float fromTopReachedAt = nodes.get(thresholdCountNodes-1).y();
        

        nodes.sort(fromBottomComp);
        Float fromBottomReachedAt = nodes.get(thresholdCountNodes-1).y();


        var threshRect = new Rectangle2D.Float(
            fromLeftReachedAt,
            fromTopReachedAt,
            fromRightReachedAt-fromLeftReachedAt,
            fromBottomReachedAt-fromTopReachedAt);
        // System.out.println(" "+getRelCoords(graphBounds, threshRect)); ;
        var threshJson = new JsonObject();
        threshJson.addProperty("threshold", thresholdCountNodes);
        // threshJson.add("relative", getRelativeCoords(graphBounds, threshRect, true));
        
        threshJson.addProperty("fromLeft", fromLeftReachedAt);
        threshJson.addProperty("fromRight", fromRightReachedAt);
        threshJson.addProperty("fromTop", fromTopReachedAt);
        threshJson.addProperty("fromBottom", fromBottomReachedAt);


        // var fromLeftRel = (fromLeftReachedAt-xMin)/graphWidth;
        // threshJson.addProperty("fromLeftRel", fromLeftRel);
        // var fromRightRel = (fromLeftReachedAt-xMin)/graphWidth;
        threshJson.addProperty("fromRight", fromRightReachedAt);
        threshJson.addProperty("fromTop", fromTopReachedAt);
        threshJson.addProperty("fromBottom", fromBottomReachedAt);
        
        var drawingHints = new JsonObject();
        var gephiCenter = new JsonObject();
        // gephiCenter.addProperty("x", xGephiCenterRel);
        // gephiCenter.addProperty("y", 1 - yGephiCenterRel);
        drawingHints.add("gephiCenter", gephiCenter);
        // drawingHints.add

        /* if (compEntry.getKey().equals("fromTop")) {
            var arr2d = new float[thresholdCountNodes][2];
            for (int i = 0; i < arr2d.length; i++) {
                arr2d[i][0] = nodes.get(i).x();
                arr2d[i][1] = nodes.get(i).y();
            }
            var jsonEl = new Gson().toJsonTree(arr2d, float[][].class);
            // threshJson.add("fromTopNodes", jsonEl);
        } */
        
        
        
        
        


        

        

        

        // System.out.printf("X. Min: %f, max: %f, median: %f%n",, ,);
        // System.out.printf("Y. Min: %f, max: %f, median: %f%n",xs.get(0), xs.get(xs.size()-1),xs.get(xs.size()/2));

        var root = new JsonObject();
        
        // xObj.addProperty("median", xMedian);
        // xObj.addProperty("gephiCenterRel", xGephiCenterRel);
        // xObj.addProperty("middle", xMiddle);
        // root.add("x", xObj);
        var yObj = new JsonObject();
        
        // yObj.addProperty("median", yMedian);
        // yObj.addProperty("gephiCenterRel", yGephiCenterRel);
        
        // yObj.addProperty("middle", yMiddle);
        root.add("y", yObj);
        root.add("threshold", threshJson);
        root.add("drawingHints", drawingHints);
        return root;
        // System.out.println(new Gson().toJson(root));
    }

    private static JsonObject getRelativeCoords(Rectangle2D outerRect, Rectangle2D innerRect, boolean mirrorVertically) {
        // Example outer rectangle
        // Rectangle2D.Float outerRect = new Rectangle2D.Float(100, 100, 300, 200);
        // Example inner rectangle
        // Rectangle2D.Float innerRect = new Rectangle2D.Float(150, 150, 100, 50);
    
        // Calculate the relative coordinates
        double topRel = (innerRect.getY() - outerRect.getY()) / outerRect.getHeight();
        double leftRel = (innerRect.getX() - outerRect.getX()) / outerRect.getWidth();
        double rightRel = (outerRect.getX() + outerRect.getWidth() - (innerRect.getX() + innerRect.getWidth())) / outerRect.getWidth();
        double bottomRel = (outerRect.getY() + outerRect.getHeight() - (innerRect.getY() + innerRect.getHeight())) / outerRect.getHeight();
    
        // Print the results
        var result = new JsonObject();
 
        result.addProperty("leftRel", leftRel);
        result.addProperty("rightRel", rightRel);
        if (!mirrorVertically) {
            result.addProperty("topRel", topRel);
            result.addProperty("bottomRel", bottomRel);
        } else {
            result.addProperty("topRel", bottomRel);
            result.addProperty("bottomRel", topRel);
        }
        return result;
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
        try {
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
        } catch (Exception e) {
            throw e;
            // e.
        }
        var columnType = column.getTypeClass();
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
        
        Column column = null;
        switch (nodeOrEdge) {
            case "node" : 
                column = graphModel.getNodeTable().getColumn(columnId);
                break;
            case "edge" : 
                column = graphModel.getEdgeTable().getColumn(columnId);
                break;
            default : {throw new IllegalStateException("Type should be node or edge, not "+nodeOrEdge);}
        };
        var columnType = column.getTypeClass();
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
            AttributeEqualBuilder.EqualStringFilter<?> filter = null;
            switch (nodeOrEdge) {
                case "node" : 
                    filter = new AttributeEqualBuilder.EqualStringFilter.Node(column);
                    break;
                case "edge" : 
                    filter =  new AttributeEqualBuilder.EqualStringFilter.Edge(column);
                    break;
                default : {throw new IllegalStateException("Type should be node or edge, not "+nodeOrEdge);}
            };
            filter.setUseRegex(false);
            filter.setPattern(value.getAsString());
            filterResult = filter;
        }
        filterResult.init(graphModel.getGraphVisible());
        var query = filterController.createQuery(filterResult);
        return query;
    }
    private static void applyForceAtlas2(GraphModel graphModel, JsonObject options) {
        ForceAtlas2 layout = new ForceAtlas2(null);
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        if (options.has("export")) {
            runAlgoWithExporting(layout,options);
        } else {
            runAlgoFor(layout, options);
        }
    }

    private static void applyYifanHu(GraphModel graphModel, JsonObject options) {
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        if (options.has("export")) {
            runAlgoWithExporting(layout,options);
        } else {
            runAlgoFor(layout, options);
        }
    }

    private static void applyYifanHuProportional(GraphModel graphModel, JsonObject options) {
        var layout =  new YifanHuProportional().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        if (options.has("export")) {
            runAlgoWithExporting(layout,options);
        } else {
            runAlgoFor(layout, options);
        }
    }
    
    private static void applyOpenOrd(GraphModel graphModel, JsonObject options) {
        var layout =  new OpenOrdLayoutBuilder().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runAlgoFor(layout, options);
    }
    private static void applyRandomLayout(GraphModel graphModel, JsonObject options) {
        var layout =  new RandomLayout(new Random(), 50);
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runAlgoFor(layout, options);
    }
    private static void applyNoverlapLayout(JsonObject options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // var layout = (NoverlapLayout)(new NoverlapLayoutBuilder().buildLayout());
        var layout = new NoverlapLayout(new NoverlapLayoutBuilder());
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runAlgoFor(layout, options);
    }

    private static void applyFruchtermanReingoldLayout(JsonObject options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // var layout = (NoverlapLayout)(new NoverlapLayoutBuilder().buildLayout());
        var layout = new FruchtermanReingoldBuilder().buildLayout();
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        if (options.has("export")) {
            runAlgoWithExporting(layout,options);
        } else {
            runAlgoFor(layout, options);
        }
    }

    private static void printLayoutProperties(Layout layout) {
        System.out.println(layout.getClass().getSimpleName()+" properties:");
        for (var prop : layout.getProperties()) {
            try {
                var name = prop.getProperty().getName();
                var value = prop.getProperty().getValue();
                System.out.println(name+" = "+value);
            } catch (Exception e) {e.printStackTrace();}
        }
    }

    private static void setLayoutProperties(Layout layout, JsonObject options) {
        var supportedPropertyNames = new ArrayList<String>();
        for (LayoutProperty lProp : layout.getProperties()) {
            var prop = lProp.getProperty();
            var name = prop.getName();
            // System.out.println(prop.attributeNames());
            // System.out.println(lProp.getCategory());
            // System.out.println(lProp.getCanonicalName());
            
            supportedPropertyNames.add(name);
            var type = prop.getValueType();
            if (prop.canWrite() && options.has(name)) {
                try {
                    var valueEl = options.get(name);
                    
                    switch (type.getSimpleName()) {
                        case "Integer":
                            @SuppressWarnings("unchecked")
                            var propInt = (Property<Integer>) prop;
                            propInt.setValue(valueEl.getAsInt());
                            break;
                        case "Float":
                            @SuppressWarnings("unchecked")
                            var propF = (Property<Float>) prop;
                            propF.setValue(valueEl.getAsFloat());
                            break;
                        case "Double":
                            @SuppressWarnings("unchecked")
                            var propD = (Property<Double>) prop;
                            propD.setValue(valueEl.getAsDouble());
                            break;
                        case "Boolean":
                            @SuppressWarnings("unchecked")
                            var propB = (Property<Boolean>) prop;
                            propB.setValue(valueEl.getAsBoolean());
                            break;
                    
                        default:
                            var msg = String.format("Unknown property type: %s, TODO",type);
                            throw new IllegalStateException(msg);
                    }
                } catch (IllegalAccessException|IllegalArgumentException|InvocationTargetException  e) {
                    System.out.println("ERROR: Failed to set "+prop.getName());
                    e.printStackTrace();
                }
            }
            //System.out.printf("%s\t%s%n",name,type);
        }
        Set<String> predefinedOptionNames = Set.of("name","steps","maxSteps","export","exportEach");
        Set<String> userOpts = options.keySet();
        var unknownUserOpts = new HashSet<String>(userOpts);
        unknownUserOpts.removeAll(predefinedOptionNames);
        unknownUserOpts.removeAll(supportedPropertyNames);
        if (unknownUserOpts.size() > 0) {
            var msg = String.format("%s doesn't support these options: %s. These are supported: %s%n",
                layout.getClass().getSimpleName(),unknownUserOpts,supportedPropertyNames);
            throw new IllegalStateException(msg);
        }
    }

    private static void sizeNodesByColumn(JsonObject rankingOptions) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        
        String desiredColumn = rankingOptions.get("column").getAsString();
        Column column = getNodeColumnIncludingDefault(graphModel,desiredColumn);
        
        Function columnRanking = appearanceModel.getNodeFunction(column, 
            RankingNodeSizeTransformer.class);
        RankingNodeSizeTransformer sizeTransformer = 
            (RankingNodeSizeTransformer) columnRanking.getTransformer();

        int nodeMinSize = rankingOptions.has("minSize") ? rankingOptions.get("minSize").getAsInt() : 5;
        int nodeMaxSize = rankingOptions.has("maxSize") ? rankingOptions.get("maxSize").getAsInt() : nodeMinSize * 4;
        
        sizeTransformer.setMinSize(nodeMinSize);
        sizeTransformer.setMaxSize(nodeMaxSize);

        appearanceController.transform(columnRanking);
    }

    private static Column getNodeColumnIncludingDefault(GraphModel graphModel, String desiredColumn) {
        Column column = null;
        switch (desiredColumn) {
            case "degree" : {column = graphModel.defaultColumns().degree(); break;}
            case "inDegree" : {column = graphModel.defaultColumns().inDegree(); break;}
            case "outDegree" : {column = graphModel.defaultColumns().outDegree(); break;}
            default : {column = graphModel.getNodeTable().getColumn(desiredColumn); break;}
        }
        return column;
    }
    private static void colorNodesByColumn(JsonObject options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        DirectedGraph graph = graphModel.getDirectedGraph();

        Column column = getNodeColumnIncludingDefault(graphModel, options.get("column").getAsString());
        
        String mode = options.get("mode").getAsString().toLowerCase();

        switch (mode) {
            case "ranking" : {
                var spliter = options.get("colors").getAsJsonArray().spliterator();
                List<Color> colors = StreamSupport.stream(spliter,false)
                    .map(JsonElement::getAsString)
                    .map(GephiCommander::parseColor)
                    .collect(Collectors.toList());
                
                List<Float> colorPositions = new ArrayList<Float>();
                if (options.has("colorPositions")) {
                    var spliterPos = options.get("colorPositions").getAsJsonArray().spliterator();
                    colorPositions = StreamSupport.stream(spliterPos,false)
                        .map(JsonElement::getAsFloat)
                        .collect(Collectors.toList());
                }
                if ((colorPositions.size() != 0) && 
                    (colors.size() != colorPositions.size())
                    ) {
                    var msg = "colorPositions.count should be either same as colors.count or 0";
                    throw new IllegalArgumentException(msg);
                }
                if (colorPositions.size() == 0) {
                    for (int i = 0; i < colors.size(); i++) {
                        colorPositions.add(1.0f/(colors.size()-1)*i);
                    }
                }
                System.out.println("colorPositions="+colorPositions);
                float[] colorPositionsPrim = new float[colorPositions.size()];
                int i = 0;
                for (Float x : colorPositions) {
                    colorPositionsPrim[i++] = x;
                }

                Function transformingFunction = appearanceModel.getNodeFunction(column, RankingElementColorTransformer.class);
                RankingElementColorTransformer transformer = transformingFunction.getTransformer();
                transformer.setColors(colors.toArray(new Color[0]));
                transformer.setColorPositions(colorPositionsPrim);

                appearanceController.transform(transformingFunction);
                break;
            } 
            case "partition" : {
                Function transformingFunction = appearanceModel.getNodeFunction(column, PartitionElementColorTransformer.class);
                Partition partition = ((PartitionFunction) transformingFunction).getPartition();
                Palette palette = PaletteManager.getInstance().generatePalette(partition.size(graph));
                partition.setColors(graph, palette.getColors());

                appearanceController.transform(transformingFunction);
                break;
            } 
            case "value" : {
                for (Node node : graphModel.getDirectedGraph().getNodes()) {
                    try {
                        String colorValue = node.getAttribute(column).toString();
                        Color color = GephiCommander.parseColor(colorValue);
                        if (color != null) {
                            node.setColor(color);
                        }
                    } catch (Exception e) {}
                }
                break;
            }
            default : {
                String msg = "Allowed color modes are: ranking, partition, value";
                throw new IllegalArgumentException(msg);
            }
        }
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
        if (previewOptions.has("showNodeLabels")) {
            boolean show = previewOptions.get("showNodeLabels").getAsBoolean();
            model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, show);
        }
        
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN, 8));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.WHITE));

        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 4.0f);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_OPACITY, 40);
        model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_COLOR, new DependantColor(Color.BLACK));
        
        if (previewOptions.has("edgeRescaleWeight")) {
            var obj = previewOptions.get("edgeRescaleWeight").getAsJsonObject();
            model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT, Boolean.TRUE);
            if (obj.has("min")) {
                var min = obj.get("min").getAsInt();
                model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MIN, min);
                
            }
            if (obj.has("max")) {
                var max = obj.get("max").getAsInt();
                model.getProperties().putValue(PreviewProperty.EDGE_RESCALE_WEIGHT_MAX, max);
                
            }
        }
        
        
        //Edge Properties
        model.getProperties().putValue(PreviewProperty.SHOW_EDGES, Boolean.TRUE);
        
        var edgeColorEl = previewOptions.get("edgeColor");
        if (edgeColorEl != null) {
            Mode mode = Mode.valueOf(edgeColorEl.getAsString().toUpperCase());
            model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(mode));
        } else {
            // when no Preview op
            // DependantOriginalColor cannot be cast to class org.gephi.preview.types.EdgeColor
            //model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new DependantOriginalColor(Color.WHITE));
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
            var color = GephiCommander.parseColor(colorName);
            if (color != null) {
                model.getProperties().putValue(PreviewProperty.BACKGROUND_COLOR, color);
            } else {
                System.out.println("Such color haven't been found: "+colorName);
            }
        }
    }
    private static void export(JsonObject options) {
        System.out.println("Exporting...");
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        var pc = Lookup.getDefault().lookup(ProjectController.class);
        var workspace = pc.getCurrentWorkspace();
        
        String filename = options.has("file") ? options.get("file").getAsString() : "gephi.pdf";
        if (options.has("timestamp") && options.get("timestamp").getAsBoolean()) {
            var now = LocalDateTime.now();
            var formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");
            String ts = now.format(formatter);

            String extension = filename.substring(filename.lastIndexOf("."));
            String filenameWithoutExtension = filename.substring(0, filename.lastIndexOf("."));
            filename = filenameWithoutExtension + ts + extension;
        }
        File outFile = new File(filename);
        String extension = outFile.getName().replaceAll("^.*\\.","");
        
        try {
            var exporter = ec.getExporter(extension);
            /* if (exporter instanceof ExporterGEXF) {
                ExporterGEXF graphExporter = (ExporterGEXF) exporter;
                graphExporter.
            } */
        //    GexfExporter
            if (exporter instanceof GraphExporter) {
                GraphExporter graphExporter = (GraphExporter) exporter;
                graphExporter.setWorkspace(workspace);
                if (options.has("exportVisible")) {
                    graphExporter.setExportVisible(options.get("exportVisible").getAsBoolean());
                } else {
                    graphExporter.setExportVisible(true);
                }
                ec.exportFile(outFile, graphExporter);

            } else if (extension.equals("png")) {
                


                PNGExporter pngExporter = null;
                if (options.has("PNGExporter")) {
                    var pngOpts = options.get("PNGExporter").getAsJsonObject();
                    pngExporter = new MyPNGExporter(pngOpts);
                } else {
                    pngExporter = new MyPNGExporter();
                }
                
                pngExporter.setWorkspace(workspace);
                if (options.has("resolution")) {
                    var res = options.getAsJsonArray("resolution");
                    int x = res.get(0).getAsInt();
                    int y = res.size() == 2 ? res.get(1).getAsInt() : x;
                    pngExporter.setWidth(x);
                    pngExporter.setHeight(y);
                }
                pngExporter.setMargin(0);
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

    private static void runAlgoFor(Layout layout, int steps) {
        System.out.printf("Applying layout %s with %s steps... ",layout.getClass().getSimpleName(), steps); 
        layout.initAlgo();
        for (int k = 0; k < steps; k++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        System.out.println("Done.");
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
    private static void runAlgoFor(Layout layout, JsonObject options) {
        if (options.has("steps")) {
            runAlgoFor(layout, options.get("steps").getAsInt());
        }
        else {
            int maxSteps = options.has("maxSteps") ? options.get("maxSteps").getAsInt() : Integer.MAX_VALUE;
            runAlgoForMaximum(layout, maxSteps);
        }
    }
    private static void runAlgoWithExporting(Layout layout, JsonObject layoutOptions) {
        String layoutName = layout.getClass().getSimpleName();
        var exportOptions = layoutOptions.get("export").getAsJsonObject();
        int each = layoutOptions.get("exportEach").getAsInt();
        int steps = layoutOptions.get("steps").getAsInt();

        System.out.printf("Applying layout %s with %s steps...%n", layoutName, steps);
        layout.initAlgo();
        //export(exportOptions);
        for (int k = 1; k <= steps; k++) {
            layout.goAlgo();
            if (k % each == 0 || k == steps) {
                export(exportOptions);
            }
        }
        layout.endAlgo();
        System.out.println("Applying "+ layoutName + " is finished.");
    }

    private static Color parseColor(String colorValue) {
        try {
            Color color = ImportUtils.parseColor(colorValue);
            if (color != null) return color;
            
            // Fallback to RGB parsing (e.g., "255 0 0" or "rgb(255,0,0)")
            String[] rgb = colorValue.replaceAll("\\D+", " ").trim().split("\\s+");
            
            return new Color(
                Integer.parseInt(rgb[0]),
                Integer.parseInt(rgb[1]),
                Integer.parseInt(rgb[2])
            );
        } catch (Exception ignored) {}
        return null;
    }
}