package gephicommander;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.Collection;

import org.gephi.appearance.api.Partition;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.openide.util.Lookup;

public class PartitionRenderer {
    private static int lastY = 0;

    public static void draw(Graphics g2d, Partition partition, Graph graph, int fontSize, Point2D topLeft) {
        if (graph == null) {
            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            GraphModel graphModel = graphController.getGraphModel();
            graph = graphModel.getGraphVisible();
        }

        Font originalFont = g2d.getFont();
        g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        g2d.setColor(Color.GRAY);

        // Calculate position (top-left corner with margin)
        // int margin = 20;
        int x = (int)topLeft.getX();
        int y = (int)topLeft.getY()+fontSize;
        
        String columnId = partition.getColumn().getId();
        // Draw header
        g2d.drawString(String.format("Partition by %s:", columnId), x, y);
        y += fontSize * 1.5;

        // Draw column headers
        g2d.drawString(String.format("%-20s %8s %10s",
                "Value", "Count", "Percent"), x, y);
        y += fontSize * 1.2;

        // Draw partition items
        int maxItems = 10; // Limit number of items shown
        int count = 0;

        Collection values = partition.getSortedValues(graph);
        // for (int i = 0; i < values.size(); i++) {
            
        // }
        for (Object value : values) {
            if (count++ >= maxItems) {
                int remaining = values.size()-count;
                String str = String.format("... (%s more)", remaining);
                g2d.drawString(str, x, y);
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
            if (value == null) value = "<null>";
            g2d.drawString(String.format("%-20s %8d %9.1f%%",
                    truncate(value.toString(), 20),
                    valueCount,
                    percent),
                    x + fontSize + 5, y);

            y += fontSize * 1.2;
        }
        lastY = y;
        g2d.setFont(originalFont);
    }

    public static void drawMultilineString(Graphics g2d, String string, int fontSize) {
        String[] lines = string.split("\r?\n", -1);
        int x = fontSize;   // left margin
        
        Font originalFont = g2d.getFont();
        g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize));

        for (var line : lines) {
            lastY += fontSize * 1.2;
            g2d.drawString(line, x, lastY);
        }
        g2d.setFont(originalFont);
    }
    public static String humanReadable(Integer number) {
        if (number == null) return "null";
        if (number < 1000) return String.valueOf(number);
        int exp = (int) (Math.log(number) / Math.log(1000));
        char suffix = "KMBT".charAt(exp - 1);
        return String.format("%.1f%c", number / Math.pow(1000, exp), suffix);
    }
    private static String truncate(String str, int length) {
        return str.length() > length ? str.substring(0, length-3) + "..." : str;
    }
}
