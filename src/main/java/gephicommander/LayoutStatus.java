package gephicommander;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.gephi.layout.spi.Layout;

/*
 * Keeps track of current layout being applied and all layouts.
 * Created for being used in PNGExporter.
 */
public class LayoutStatus {

    static int globalIterationsDone = 0; // across all layouts
    static int globalIterationsMax = 0;
    static List<LayoutStatus> layoutsApplied = new ArrayList<>();
    static Instant globalStartInstant = null;
    static int globalTimeElapsed = 0;   //sec

    Layout layout = null;
    Integer localIteration = null; // in current layout
    Integer localIterationsMax = null;
    Integer localExportEach = null;
    
}
