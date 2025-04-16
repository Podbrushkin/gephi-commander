package gephicommander;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import org.gephi.appearance.api.Partition;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.openide.util.Lookup;

public class PartitionRenderer {

    public static void draw(Graphics g2d, Partition partition, Graph graph, int fontSize, Point2D topLeft) {
        if (graph == null) {
            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            GraphModel graphModel = graphController.getGraphModel();
            graph = graphModel.getGraphVisible();
        }

        Font originalFont = g2d.getFont();

        // int fontSize = 72;
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        g2d.setColor(Color.BLACK);

        // Calculate position (top-left corner with margin)
        // int margin = 20;
        int x = (int)topLeft.getX();
        int y = (int)topLeft.getY();
        
        String columnId = partition.getColumn().getId();
        // Draw header
        g2d.drawString(String.format("Partition: %s (Nodes)", columnId), x, y);
        y += fontSize * 1.5;

        // Draw column headers
        g2d.drawString(String.format("%-20s %8s %10s",
                "Value", "Count", "Percent"), x, y);
        y += fontSize * 1.2;

        // Draw partition items
        int maxItems = 10; // Limit number of items shown
        int count = 0;

        for (Object value : partition.getSortedValues(graph)) {
            if (count++ >= maxItems) {
                g2d.drawString("... (more items not shown)", x, y);
                break;
            }

            int valueCount = partition.count(value, graph);
            float percent = partition.percentage(value, graph);
            Color color = partition.getColor(value);

            // Draw color swatch
            g2d.setColor(color);
            g2d.fillRect((int) x, (int) (y - fontSize + 2), fontSize, fontSize - 4);

            // Draw text info
            // g2d.setColor(fontColor != null ? fontColor : Color.BLACK);
            g2d.drawString(String.format("%-20s %8d %9.1f%%",
                    truncate(value.toString(), 20),
                    valueCount,
                    percent),
                    x + fontSize + 5, y);

            y += fontSize * 1.2;
        }

        g2d.setFont(originalFont);
    }
    private static String truncate(String str, int length) {
        return str.length() > length ? str.substring(0, length-3) + "..." : str;
    }
}
