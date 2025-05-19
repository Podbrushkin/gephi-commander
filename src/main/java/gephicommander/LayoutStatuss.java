package gephicommander;

import java.time.Instant;
import java.util.List;

import org.gephi.layout.spi.Layout;

public class LayoutStatuss {
    static List<Layout> layoutsApplied = null;
    static Instant globalStartInstant = null;
    static int globalTimeElapsed = 0;   //sec

    static Integer localIteration = null; // in current layout
    static Integer localIterationsMax = null;
    static Integer localExportEach = null;

    
    
}