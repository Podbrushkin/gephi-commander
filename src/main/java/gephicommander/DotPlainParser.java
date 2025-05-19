package gephicommander;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.gephi.graph.api.Graph;

/*
 * Import node coordinates from dot plain file.
 * dot graph.dot -Nshape=point -Ksfdp -Tplain -o dotplain.txt
 */
public class DotPlainParser {
    
    public void importNodeCoordinates(Graph graph, Path dotPlain) {
        Map<String,float[]> idToCoord = new HashMap<>();
        try (var sc = new Scanner(dotPlain)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (!line.startsWith("node")) continue;
                String[] arr = tokenize(line);
                float x = Float.parseFloat(arr[2]);
                float y = Float.parseFloat(arr[3]);
                idToCoord.put(arr[1], new float[]{x,y});
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read dot plain file", e);
        }
        for (var node : graph.getNodes()) {
            float[] coord = idToCoord.get(node.getId());
            if (coord != null) {
                node.setX(coord[0]);
                node.setY(coord[1]);
            }
        }
    }
    
    private String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentToken = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes) {
                    // Closing quote - add the token
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                    inQuotes = false;
                } else {
                    // Opening quote
                    inQuotes = true;
                }
            } else if (Character.isWhitespace(c) && !inQuotes) {
                // Whitespace outside quotes - finish current token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                // Regular character
                currentToken.append(c);
            }
        }

        // Add the last token if any
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }
}
