package gephicommander;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JFrame;
import javax.swing.Timer;

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
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Element;
import org.gephi.graph.api.ElementIterable;
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
import org.gephi.preview.api.G2DTarget;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.RenderTarget;
import org.gephi.preview.types.DependantColor;
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.preview.types.EdgeColor;
import org.gephi.preview.types.EdgeColor.Mode;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.statistics.plugin.ConnectedComponents;
import org.gephi.statistics.plugin.EigenvectorCentrality;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Modularity;
import org.gephi.toolkit.demos.plugins.preview.PreviewSketch;
import org.openide.nodes.Node.Property;
import org.openide.util.Lookup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GephiCommander {
    static ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
    static JsonObject globalExport = new JsonObject();
    private final static String DELAYED_PROP = "delay";
    
    // static Map<Integer, JsonArray> iterToOperation = new HashMap<>();
    private static JsonArray delayedOperations = new JsonArray();
    
    public static void main(String[] args) throws ScriptException {
        JsonArray optionsGlobal = null;
        try (Reader reader = args[args.length-1].equals("-") ?
                new InputStreamReader(System.in) :
                new FileReader(args[0])) {
                    optionsGlobal = JsonParser.parseReader(reader).getAsJsonArray();
        } catch (Exception e) {
            System.err.println("Last arg should be a filepath or '-' to read from stdin.");
            e.printStackTrace();
            System.exit(1);
        }
        LayoutStatus.globalIterationsMax = countLayoutIterationsTotal(optionsGlobal);
        System.out.println("IterationsTotal: "+LayoutStatus.globalIterationsMax);

        Locale.setDefault(Locale.ENGLISH);  // Ignore Gephi localization
        
        for (int i = 0; i < optionsGlobal.size(); i++) {
            var obj = optionsGlobal.get(i).getAsJsonObject();
            if (obj.has(DELAYED_PROP)) {
                delayedOperations.add(obj);
            }
        }
        for (var op : delayedOperations) {
            optionsGlobal.remove(op);
        }
        

        processOperations(optionsGlobal);
        
    }
    private static void processOperations(JsonArray operations) {
        for (var opEl : operations) {
            var op = opEl.getAsJsonObject();
            System.out.println(">>>"+op.toString());
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
                case "disableFilters":
                    disableFilters();
                    break;
                case "setCamera":
                    CameraHandler.apply(op);
                    break;
                case "livePreview":
                    showLivePreview(op);
                    break;
                case "layouts":
                    applyLayouts(op.get("values").getAsJsonArray());
                    break;
                case "preview":
                    setGraphPreview(op);
                    break;
                case "labelNodesBy":
                    labelElementsByColumn(Node.class, op);
                    break;
                case "labelEdgesBy":
                    labelElementsByColumn(Edge.class, op);
                    break;
                case "colorNodesBy":
                    colorElementsByColumn(Node.class, op);
                    break;
                case "colorEdgesBy":
                    colorElementsByColumn(Edge.class, op);
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
                case "setExport":
                    globalExport = op;
                    break;
                default:
                    String msg = "Unknown root element "+opName;
                    throw new IllegalArgumentException(msg);
            }
        }
    }

    // private static void setGlobal(JsonObject op) {}

    private static int countLayoutIterationsTotal(JsonArray optionsGlobal) {
        return StreamSupport.stream(optionsGlobal.spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .filter(obj -> obj.get("op").getAsString().equals("layouts"))
            .flatMap(op -> StreamSupport.stream(op.get("values").getAsJsonArray().spliterator(),false))
            .map(JsonElement::getAsJsonObject)
            .map(l -> l.get("steps").getAsInt())
            .reduce((x,y) -> x+y)
            .orElse(0);
    }
    
    private static void showLivePreview(JsonObject op) {
        PreviewController previewController = Lookup.getDefault().lookup(PreviewController.class);
        G2DTarget target = (G2DTarget) previewController.getRenderTarget(RenderTarget.G2D_TARGET);
        PreviewSketch previewSketch = new PreviewSketch(target);
        previewController.refreshPreview();
        //Add the applet to a JFrame and display
        JFrame jframe = new JFrame("Preview JFrame");
        jframe.setLayout(new BorderLayout());

        jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jframe.add(previewSketch, BorderLayout.CENTER);
        jframe.setSize(1024, 768);
        
        //Wait for the frame to be visible before painting, or the result drawing will be strange
        jframe.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                previewSketch.resetZoom();
            }
        });
        
        int fps = op.has("fps") ? op.get("fps").getAsInt() : 30;
        int delay = op.has("delayMillis") ? op.get("delayMillis").getAsInt() : (int)(1.0/fps*1000);

        boolean doSaveFrames = op.has("saveFrames") && op.get("saveFrames").getAsBoolean();
        final List<BufferedImage> frames = new ArrayList<>();
        var refreshTimer = new Timer(delay, e -> {
            previewController.refreshPreview();
            previewSketch.refreshSketch();

            if (doSaveFrames) {
                BufferedImage frame = new BufferedImage(
                    previewSketch.getWidth(), 
                    previewSketch.getHeight(), 
                    BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = frame.createGraphics();
                previewSketch.paint(g2d);
                g2d.dispose();
                frames.add(frame);
            }
        });
        refreshTimer.start();
        
        jframe.setVisible(true);
        
        if (doSaveFrames) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("frames.size()="+frames.size());
                    saveFramesAsPNGs(frames);
                }
                
            });
        }
    }
    private static void saveFramesAsPNGs(List<BufferedImage> frames) {
        System.out.println("__frames.size()="+frames.size());
        File outputDir = new File("frames");
        outputDir.mkdirs();
        
        for(int i = 0; i < frames.size(); i++) {
            try {
                ImageIO.write(frames.get(i), "png", 
                    new File(outputDir, String.format("frame_%03d.png", i)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("images saved to "+outputDir);
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
                case "EigenvectorCentrality" : {
                    var stats = new EigenvectorCentrality();
                    stats.setDirected(true);
                    stats.execute(graphModel);
                    break;
                }
                case "GraphDistance" : {
                    var stats = new GraphDistance();
                    stats.setDirected(true);
                    stats.execute(graphModel);
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
                    String msg = String.format("Filter \"%s\" not found!%n", name);
                    throw new IllegalArgumentException(msg);
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
        System.out.println("COUNTS after filtering:");
        printCounts(gm.getGraphVisible());
        
    }

    private static void disableFilters() {
        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        GraphModel gm = graphController.getGraphModel();
        
        // Reset to the default unfiltered view
        gm.setVisibleView(gm.getGraph().getView());
    
        System.out.println("All filters have been disabled");
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
                case "NoOp" : {
                    var layout = new NoOpLayout();
                    runLayout(layout, options);
                    break;
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
                    System.out.println(getColumnsInfo(Node.class));
                    break;
                case "edgeColumns":
                    System.out.println("Edge columns:");
                    System.out.println(getColumnsInfo(Edge.class));
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
    private static String getColumnsInfo(Class<? extends Element> nodeOrEdgeClass) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        var sb = new StringBuilder();
        var table = nodeOrEdgeClass.equals(Node.class) ? 
            graphModel.getNodeTable() :
            graphModel.getEdgeTable();
        
        sb.append("id\ttitle\ttype\n");
        for (var col : table) {
            var s = String.format("%s\t%s\t%s%n",col.getId(),col.getTitle(),col.getTypeClass().getSimpleName());
            sb.append(s);
        }
        return sb.toString();
    }

    static void measureGraphBounds() {
        float[] leftToRightPercentiles = new float[101];
        float[] bottomToTopPercentiles = new float[101];

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
            yPositionsG2d[i] = -y; // drawing y coord = negative model y
            i++;
        }
        
        // Calculate percentiles
        Arrays.sort(xPositions);
        Arrays.sort(yPositions);//, Comparator.reverseOrder());
        Arrays.sort(yPositionsG2d, Comparator.reverseOrder());
        
        for (int p = 0; p <= 100; p++) {
            int index = (int) Math.round((p / 100.0) * (xPositions.length - 1));
            leftToRightPercentiles[p] = xPositions[index];
            // bottomToTopPercentiles[p] = yPositionsG2d[index];
            bottomToTopPercentiles[p] = yPositions[index];
        }

        engine.put("xPercentiles", leftToRightPercentiles);
        engine.put("yPercentiles", bottomToTopPercentiles);
        // var obj = new JsonObject();
        // obj.a
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

        var threshJson = new JsonObject();
        threshJson.addProperty("threshold", thresholdCountNodes);
        
        
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
        
        runLayout(layout,options);
    }

    private static void applyYifanHu(GraphModel graphModel, JsonObject options) {
        YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        
        runLayout(layout,options);
    }

    private static void applyYifanHuProportional(GraphModel graphModel, JsonObject options) {
        var layout =  new YifanHuProportional().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        
        runLayout(layout,options);
    }
    
    private static void applyOpenOrd(GraphModel graphModel, JsonObject options) {
        var layout =  new OpenOrdLayoutBuilder().buildLayout();
        layout.resetPropertiesValues();
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runLayout(layout, options);
    }
    private static void applyRandomLayout(GraphModel graphModel, JsonObject options) {
        var layout =  new RandomLayout(new Random(), 50);
        layout.setGraphModel(graphModel);
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runLayout(layout, options);
    }
    private static void applyNoverlapLayout(JsonObject options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // var layout = (NoverlapLayout)(new NoverlapLayoutBuilder().buildLayout());
        var layout = new NoverlapLayout(new NoverlapLayoutBuilder());
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runLayout(layout, options);
    }

    private static void applyFruchtermanReingoldLayout(JsonObject options) {
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // var layout = (NoverlapLayout)(new NoverlapLayoutBuilder().buildLayout());
        var layout = new FruchtermanReingoldBuilder().buildLayout();
        layout.setGraphModel(graphModel);
        layout.resetPropertiesValues();
        setLayoutProperties(layout, options);
        printLayoutProperties(layout);
        runLayout(layout, options);
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
        if (column == null) {
            String inf = String.format("Nodes don't have %s column, these exist: %s", desiredColumn, getColumnsInfo(Node.class));
            throw new IllegalArgumentException(inf);
        }
        return column;
    }
    
    private static void labelElementsByColumn(Class<? extends Element> elementType, JsonObject options) {
        // TODO: pls refactor
        var el = options.get("column");
        String columnName = el.isJsonNull() ? null : el.getAsString();
        
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        Workspace workspace = pc.getCurrentWorkspace();
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

        ElementIterable<?> iter = elementType.equals(Node.class) ?
                    graphModel.getDirectedGraph().getNodes() :
                    graphModel.getDirectedGraph().getEdges();
        
        
        for (Element nodeOrEdge : iter) {
            if (options.has("condition")) {
                String expr = options.get("condition").getAsString();
                boolean applyLabel = false;

                var map = getElementAsMap(graphModel, nodeOrEdge);
                
                engine.put("el", map);

                try {
                    applyLabel = (Boolean)engine.eval(expr); 
                } catch (ScriptException e) {}
                if (!applyLabel) continue;
            }
            if (columnName == null) {
                // nodeOrEdge.removeAttribute("Label");
                nodeOrEdge.setLabel(null);
                continue;
            }
            Column column = nodeOrEdge instanceof Node ? 
                getNodeColumnIncludingDefault(graphModel, columnName) :
                graphModel.getEdgeTable().getColumn(columnName);
            
            String newLabel = null;
            if (column != null && column.exists()) {
                // System.out.printf("column %s exists=%s %n",column,column.exists());
                Object value = nodeOrEdge.getAttribute(column);
                if (value == null) {
                    var elMap = getElementAsMap(graphModel, nodeOrEdge);
                    System.out.printf("node %s don't have column=%s, these attrs exist: %s%n",
                        elMap,column, Arrays.toString(nodeOrEdge.getAttributes()));
                    
                }
                newLabel = String.valueOf(value);
            } else {
                newLabel = getElementAsMap(graphModel, nodeOrEdge).get(columnName).toString();
            }
            nodeOrEdge.setLabel(newLabel);
        }
    }

    private static Map<String,Object> getElementAsMap(GraphModel graphModel, Element element) {
        var keys = element.getAttributeKeys();
        if (element instanceof Node) {
            keys.addAll(List.of("degree","inDegree","outDegree"));
        }
        var map = new HashMap<String,Object>();
        for (var key : keys) {
            switch (key) {
                case "degree":
                    var val = String.valueOf(graphModel.getDirectedGraphVisible().getDegree((Node)element));
                    map.put(key, val);
                    continue;
                case "inDegree":
                    val = String.valueOf(graphModel.getDirectedGraphVisible().getInDegree((Node)element));
                    map.put(key, val);
                    continue;
                case "outDegree":
                    val = String.valueOf(graphModel.getDirectedGraphVisible().getOutDegree((Node)element));
                    map.put(key, val);
                    continue;
            }


            Column column = element instanceof Node ? 
                getNodeColumnIncludingDefault(graphModel, key) :
                graphModel.getEdgeTable().getColumn(key);
            
            Object val = element.getAttribute(column);
            map.put(key, val);
        }
        return map;
    }

    static Partition partition = null;
    private static void colorElementsByColumn(Class<? extends Element> elementType, JsonObject options) {
        if (!elementType.equals(Node.class) && !elementType.equals(Edge.class))
            throw new IllegalArgumentException("Was expecting Node.class or Edge.class but got "+elementType);
        
        var graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        AppearanceModel appearanceModel = appearanceController.getModel();
        DirectedGraph graph = graphModel.getDirectedGraph();
        
        String desiredColumn = options.get("column").getAsString();

        Column column = elementType.equals(Node.class) ? 
            getNodeColumnIncludingDefault(graphModel, desiredColumn) :
            graphModel.getEdgeTable().getColumn(desiredColumn);
        
        if (column == null) {
             String msg = String.format("Such column haven't been found: %s. Check out existing %s columns: %n%s",
                desiredColumn,
                elementType.getSimpleName(),
                getColumnsInfo(elementType)
            );
            throw new IllegalArgumentException(msg);
        }
        System.out.printf("colorElementsByColumn: column %s exists=%s %n",column,column.exists());

        String mode = options.get("mode").getAsString().toLowerCase();

        switch (mode) {
            case "ranking" : {
                List<Color> colors = List.of(GephiCommander.parseColor("DeepSkyBlue"), Color.YELLOW, Color.RED);
                if (options.has("colors")) {
                    var spliter = options.get("colors").getAsJsonArray().spliterator();
                    colors = StreamSupport.stream(spliter,false)
                        .map(JsonElement::getAsString)
                        .map(GephiCommander::parseColor)
                        .collect(Collectors.toList());
                }
                
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
                //System.out.println("colorPositions="+colorPositions);
                float[] colorPositionsPrim = new float[colorPositions.size()];
                int i = 0;
                for (Float x : colorPositions) {
                    colorPositionsPrim[i++] = x;
                }
                System.out.println(column);
                Function transformingFunction = elementType.equals(Node.class) ?
                    appearanceModel.getNodeFunction(column, RankingElementColorTransformer.class) :
                    appearanceModel.getEdgeFunction(column, RankingElementColorTransformer.class);
                
                RankingElementColorTransformer transformer = transformingFunction.getTransformer();
                transformer.setColors(colors.toArray(new Color[0]));
                transformer.setColorPositions(colorPositionsPrim);

                appearanceController.transform(transformingFunction);
                break;
            } 
            case "partition" : {
                Function transformingFunction = elementType.equals(Node.class) ?
                    appearanceModel.getNodeFunction(column, PartitionElementColorTransformer.class) :
                    appearanceModel.getEdgeFunction(column, PartitionElementColorTransformer.class);
                
                partition = ((PartitionFunction) transformingFunction).getPartition();
                Palette palette = PaletteManager.getInstance().generatePalette(partition.size(graph));
                partition.setColors(graph, palette.getColors());

                appearanceController.transform(transformingFunction);
                break;
            } 
            case "value" : {
                ElementIterable<?> iter = elementType.equals(Node.class) ?
                    graphModel.getDirectedGraph().getNodes() :
                    graphModel.getDirectedGraph().getEdges();
                
                for (Element el : iter) {
                    try {
                        String colorValue = el.getAttribute(column).toString();
                        System.out.printf("each> column %s exists=%s %n",column,column.exists());
                        Color color = GephiCommander.parseColor(colorValue);
                        if (color != null) {
                            el.setColor(color);
                        }
                    } catch (Exception e) {}
                }
                break;
            }
            default : {
                String msg = "Bad color mode. Expected: ranking|partition|value. Got: "+mode;
                throw new IllegalArgumentException(msg);
            }
        }
        if (elementType.equals(Edge.class)) {
            // Otherwise edges are colored as their source node (default mode)
            var model = Lookup.getDefault().lookup(PreviewController.class).getModel();
            model.getProperties().putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(Mode.ORIGINAL));
        }
    }
    
    public static void setGraphPreview(JsonObject options) {
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        var modelProps = model.getProperties();

        // Don't do anything if not asked to
        if (options.has("usePreset") && 
            options.get("usePreset").getAsBoolean()) {

                modelProps.putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.TRUE);
                modelProps.putValue(PreviewProperty.NODE_LABEL_FONT, new Font("Arial", Font.PLAIN, 8));
                modelProps.putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.WHITE));
        
                modelProps.putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 4.0f);
                modelProps.putValue(PreviewProperty.NODE_LABEL_OUTLINE_OPACITY, 40);
                modelProps.putValue(PreviewProperty.NODE_LABEL_OUTLINE_COLOR, new DependantColor(Color.BLACK));
                
        }

        var notPreviewProperties = List.of("op",DELAYED_PROP,"usePreset");
        
        for (var entry : options.entrySet()) {
            String key = entry.getKey();
            String dotKey = convertCamelToDot(key);

            if (notPreviewProperties.contains(key)) continue;
            
            setPreviewProperty(model, dotKey, entry.getValue());
        }
    }
    public static String convertCamelToDot(String str) {
        return str.replaceAll("([a-z])([A-Z]+)", "$1\\.$2").toLowerCase();
    }
    
    public static String convertDotToCamel(String str) {
        return Pattern.compile("\\.([a-z])")
                     .matcher(str)
                     .replaceAll(mr -> mr.group(1).toUpperCase());
    }

    private static void setPreviewProperty(PreviewModel model, String propertyKey, JsonElement valueElement) {
        try {
            PreviewProperty property = model.getProperties().getProperty(propertyKey);
            Class<?> expectedType = null;
            if (property == null) {
                if (propertyKey.contains("color")) expectedType = Color.class;
                else {
                    String msg = "Property not found: " + propertyKey;
                    throw new IllegalArgumentException(msg);
                }
            } else {
                // Get the expected type using reflection
                Field typeField = PreviewProperty.class.getDeclaredField("type");
                typeField.setAccessible(true);
                expectedType = (Class<?>) typeField.get(property);
            }
            
            
            
            // Convert JSON value to the expected type
            Object value;
            if (expectedType == Boolean.class) {
                value = valueElement.getAsBoolean();
            } else if (expectedType == Float.class) {
                value = valueElement.getAsFloat();
            } else if (expectedType == Integer.class) {
                value = valueElement.getAsInt();
            } else if (expectedType == Color.class) {
                value = GephiCommander.parseColor(valueElement.getAsString());
            } else if (expectedType == String.class) {
                value = valueElement.getAsString();
            } else if (expectedType == DependantOriginalColor.class) {
                var color = GephiCommander.parseColor(valueElement.getAsString());
                value = new DependantOriginalColor(color);
            } else if (expectedType == DependantColor.class) {
                var color = GephiCommander.parseColor(valueElement.getAsString());
                value = new DependantColor(color);
            } else if (expectedType == EdgeColor.class) {
                var val = valueElement.getAsString();
                if (List.of("SOURCE", "TARGET", "MIXED", "ORIGINAL").contains(val) ) {
                    value = new EdgeColor(Mode.valueOf(val));
                } else {
                    var color = GephiCommander.parseColor(val);
                    value = new EdgeColor(color);
                }
            } else {
                String msg = "Unsupported type for property " + propertyKey + ": " + expectedType;
                throw new IllegalArgumentException(msg);
            }
            
            // Set the property value
            model.getProperties().putValue(propertyKey, value);
            
        } catch (Exception e) {
            System.out.println("Available properties:");
            for (var prop : model.getProperties().getProperties()) {
                String propLine = String.format("%s\t%s\t%s\t%s",
                prop.getName(), prop.getType().getSimpleName(), 
                prop.getValue(), prop.getDescription()
                );
                System.out.println(propLine);
            }
            String msg = "Error setting property " + propertyKey + ": " + e.getMessage();
            throw new IllegalArgumentException(msg);
        }
    }
    private static void export(JsonObject options) {
        System.out.println("Exporting...");
        //  replace local options with global
        // if (globalOptions.has("export"))
        //     options = globalOptions.get("export").getAsJsonObject();
        
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

    private static void runLayout(Layout layout, JsonObject layoutOptions) {
        String layoutName = layout.getClass().getSimpleName();
        
        // Local has more priority than global
        JsonObject exportOptions = layoutOptions.has("export") ? 
            layoutOptions.get("export").getAsJsonObject() : 
            globalExport;
        
        System.out.println("Active export options: "+exportOptions);
        
        LayoutStatus.localIterationsMax = layoutOptions.get("steps").getAsInt();

        
        if (exportOptions != null && exportOptions.has("exportEach")) {
            LayoutStatus.localExportEach = exportOptions.get("exportEach").getAsInt();
        }
        
        
        System.out.printf("Applying layout %s with %s steps...%n", layoutName, LayoutStatus.localIterationsMax);
        layout.initAlgo();

        
        var graph = Lookup.getDefault().lookup(GraphController.class).getGraphModel().getGraphVisible();
        engine.put("iGlobalMax", LayoutStatus.globalIterationsMax);
        engine.put("graph", graph); // this is used for getNode(id).x()
        
        

        for (LayoutStatus.localIteration = 0; LayoutStatus.localIteration < LayoutStatus.localIterationsMax; LayoutStatus.localIteration++) {
            layout.goAlgo();
            engine.put("i", LayoutStatus.localIteration);
            engine.put("iGlobal", LayoutStatus.globalIterationsDone++);
            // engine.put("sc", null);

            JsonArray opsToDo = new JsonArray();
            for (int i = 0; i < delayedOperations.size(); i++) {
                var op = delayedOperations.get(i);
                var obj = op.getAsJsonObject();
                String conditionalExpr = obj.get(DELAYED_PROP).getAsString();
                
                try {
                    float targetProgress = ((Number)engine.eval(conditionalExpr)).floatValue();
                    float currentProgress = (float)LayoutStatus.globalIterationsDone / LayoutStatus.globalIterationsMax;
                    if (currentProgress >= targetProgress) {
                        opsToDo.add(obj);
                        delayedOperations.remove(obj);
                        System.out.printf("At iGlobal=%s %s >= %s. Applying op=%s %n", 
                            LayoutStatus.globalIterationsDone, currentProgress, conditionalExpr, obj);
                    }
                } catch (ScriptException e) { throw new IllegalStateException(e);}
            }
            
            // JsonArray opsToDo = iterToOperation.get(currentIteration);
            if (opsToDo.size() != 0) {
                System.out.printf(">>At iGlobal=%s these ops are performed: %s%n",
                    LayoutStatus.globalIterationsDone,opsToDo.toString());
                processOperations(opsToDo);
            }

            if (LayoutStatus.localExportEach != null && 
                LayoutStatus.localExportEach != 0 &&
                LayoutStatus.localIteration % LayoutStatus.localExportEach == 0) {
                // measureGraphBounds();
                export(exportOptions);
            }
        }
        LayoutStatus.localIteration = null;
        LayoutStatus.localExportEach = null;
        LayoutStatus.localIterationsMax = null;

        layout.endAlgo();
        System.out.println("Applying "+ layoutName + " is finished.");
    }

    private static Color parseColor(String colorValue) {
        try {
            Color color = ImportUtils.parseColor(colorValue);
            if (color != null) return color;
            
            String[] rgb = colorValue.replaceAll("\\D+", " ").trim().split("\\s+");
            
            return new Color(
                Integer.parseInt(rgb[0]),
                Integer.parseInt(rgb[1]),
                Integer.parseInt(rgb[2])
            );
        } catch (Exception ignored) {}
        return null;
    }

    public static class LayoutStatus {
        static int globalIterationsDone = 0; // across all layouts
        static int globalIterationsMax = 0;
        static Integer localIteration = null; // in current layout
        static Integer localIterationsMax = null;
        static Integer localExportEach = null;
    }
}