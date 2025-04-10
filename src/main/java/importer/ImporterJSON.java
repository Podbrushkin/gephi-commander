package importer;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.gephi.io.importer.api.ContainerLoader;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.EdgeDraft;
import org.gephi.io.importer.api.Issue;
import org.gephi.io.importer.api.NodeDraft;
import org.gephi.io.importer.api.Report;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.NbBundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ImporterJSON implements FileImporter, LongTask {

    private Reader reader;
    private ContainerLoader container;
    private Report report;
    private ProgressTicket progressTicket;
    private boolean cancel = false;

    @Override
    public boolean execute(ContainerLoader container) {
        this.container = container;
        this.report = new Report();
        container.setEdgeDefault(EdgeDirectionDefault.DIRECTED);

        try {
            importData();
            return true;
        } catch (Exception e) {
            report.logIssue(new Issue(NbBundle.getMessage(getClass(), "importerJSON_error_parsing"), Issue.Level.SEVERE));
            return false;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    private void importData() throws IOException {
        Progress.start(progressTicket);
        
        // Use Gson's JsonParser.parse() instead of deprecated constructor
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

        // Import nodes
        if (json.has("nodes")) {
            JsonArray nodesArray = json.getAsJsonArray("nodes");
            Progress.setDisplayName(progressTicket, "Importing nodes");
            Progress.switchToDeterminate(progressTicket, nodesArray.size());
            
            Map<String, NodeDraft> nodeMap = new HashMap<>();
            int nodeCount = 0;
            
            for (JsonElement nodeElement : nodesArray) {
                if (cancel) {
                    return;
                }
                
                JsonObject nodeObject = nodeElement.getAsJsonObject();
                String id = nodeObject.has("id") ? nodeObject.get("id").getAsString() : "node_" + nodeCount;
                
                NodeDraft nodeDraft = container.factory().newNodeDraft(id);
                
                if (nodeObject.has("label")) {
                    nodeDraft.setLabel(nodeObject.get("label").getAsString());
                }
                
                // Handle node attributes
                for (Map.Entry<String, JsonElement> entry : nodeObject.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("id") && !key.equals("label")) {
                        JsonElement value = entry.getValue();
                        if (value.isJsonPrimitive()) {
                            if (value.getAsJsonPrimitive().isString()) {
                                nodeDraft.setValue(key, value.getAsString());
                            } else if (value.getAsJsonPrimitive().isBoolean()) {
                                nodeDraft.setValue(key, value.getAsBoolean());
                            } else if (value.getAsJsonPrimitive().isNumber()) {
                                nodeDraft.setValue(key, value.getAsDouble());
                            }
                        }
                    }
                }
                
                nodeMap.put(id, nodeDraft);
                container.addNode(nodeDraft);
                nodeCount++;
                Progress.progress(progressTicket);
            }
            
            // Import edges
            if (json.has("edges") || json.has("relationships")) {
                JsonArray edgesArray = json.has("edges") ? json.getAsJsonArray("edges") : json.getAsJsonArray("relationships");
                Progress.setDisplayName(progressTicket, "Importing edges");
                Progress.switchToDeterminate(progressTicket, edgesArray.size());
                
                int edgeCount = 0;
                
                for (JsonElement edgeElement : edgesArray) {
                    if (cancel) {
                        return;
                    }
                    
                    JsonObject edgeObject = edgeElement.getAsJsonObject();
                    String sourceId = edgeObject.has("source") ? edgeObject.get("source").getAsString() : 
                                    edgeObject.has("from") ? edgeObject.get("from").getAsString() : null;
                    String targetId = edgeObject.has("target") ? edgeObject.get("target").getAsString() : 
                                    edgeObject.has("to") ? edgeObject.get("to").getAsString() : null;
                    
                    if (sourceId != null && targetId != null && nodeMap.containsKey(sourceId) && nodeMap.containsKey(targetId)) {
                        EdgeDraft edgeDraft = container.factory().newEdgeDraft();
                        edgeDraft.setSource(nodeMap.get(sourceId));
                        edgeDraft.setTarget(nodeMap.get(targetId));
                        
                        if (edgeObject.has("label")) {
                            edgeDraft.setLabel(edgeObject.get("label").getAsString());
                        }
                        
                        // Handle edge weight
                        if (edgeObject.has("weight") && edgeObject.get("weight").isJsonPrimitive() && 
                            edgeObject.get("weight").getAsJsonPrimitive().isNumber()) {
                            edgeDraft.setWeight(edgeObject.get("weight").getAsDouble());
                        }
                        
                        // Handle edge attributes
                        for (Map.Entry<String, JsonElement> entry : edgeObject.entrySet()) {
                            String key = entry.getKey();
                            if (!key.equals("source") && !key.equals("target") && !key.equals("from") && 
                                !key.equals("to") && !key.equals("label") && !key.equals("weight")) {
                                JsonElement value = entry.getValue();
                                if (value.isJsonPrimitive()) {
                                    if (value.getAsJsonPrimitive().isString()) {
                                        edgeDraft.setValue(key, value.getAsString());
                                    } else if (value.getAsJsonPrimitive().isBoolean()) {
                                        edgeDraft.setValue(key, value.getAsBoolean());
                                    } else if (value.getAsJsonPrimitive().isNumber()) {
                                        edgeDraft.setValue(key, value.getAsDouble());
                                    }
                                }
                            }
                        }
                        
                        container.addEdge(edgeDraft);
                        edgeCount++;
                    } else {
                        report.logIssue(new Issue(
                            NbBundle.getMessage(getClass(), "importerJSON_error_missingnodes", sourceId, targetId),
                            Issue.Level.WARNING));
                    }
                    Progress.progress(progressTicket);
                }
            }
        }
        
        Progress.finish(progressTicket);
    }

    @Override
    public void setReader(Reader reader) {
        this.reader = reader;
    }

    @Override
    public ContainerLoader getContainer() {
        return container;
    }

    @Override
    public Report getReport() {
        return report;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
        this.progressTicket = progressTicket;
    }
}