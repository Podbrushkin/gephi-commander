package gephicommander;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


public class CameraHandler {
    
    private static String[] scalingValues = null;
    private static float[] scalingPositions;
    private static String[] scalingPositionsExprs;

    private static String[] centerXExpressions;
    private static String[] centerYExpressions;
    private static float[] centerPositions;
    private static String[] centerPositionsExprs;
    

    public static boolean hasScaling() {
        return scalingValues != null;
    }
    public static boolean hasCenterOn() {
        return (centerXExpressions != null) &&
                (centerYExpressions != null);
    }

    public static void apply(JsonObject op) {
        if (op.has("scaling")) {
            var jsonEl = op.get("scaling");
            if (jsonEl.isJsonPrimitive())
                scalingValues = new String[]{ jsonEl.getAsString() };
            else if (jsonEl.isJsonArray()) {
                scalingValues = jsonEl.getAsJsonArray()
                    .asList()
                    .stream()
                    .map(JsonElement::getAsString)
                    .collect(Collectors.toList())
                    .toArray(new String[0]);
            } else 
                throw new IllegalArgumentException("Scaling should be json primitive or array.");
        }
        if (op.has("scalingPositions")) {
            int i = 0; 
            scalingPositionsExprs = new String[scalingValues.length];
            for ( var jsonEl : op.get("scalingPositions").getAsJsonArray()) {
                scalingPositionsExprs[i++] = jsonEl.getAsString();
            }
        }
        
        if (op.has("centerOn")) {
            JsonArray arr = op.get("centerOn").getAsJsonArray();
            
            // if single point instead of array of points
            if (arr.size() == 2 && arr.get(0).isJsonPrimitive()) {
                var pointArr = new JsonArray(2);
                pointArr.add(arr.get(0));
                pointArr.add(arr.get(1));
                arr = new JsonArray();
                arr.add(pointArr);
            }


            centerXExpressions = arr.getAsJsonArray()
                .asList()
                .stream()
                .map(JsonElement::getAsJsonArray)
                .map(jarr -> jarr.get(0).getAsString() )
                .collect(Collectors.toList())
                .toArray(new String[0]);
            
            centerYExpressions = arr.getAsJsonArray()
                .asList()
                .stream()
                .map(JsonElement::getAsJsonArray)
                .map(jarr -> jarr.get(1).getAsString() )
                .collect(Collectors.toList())
                .toArray(new String[0]);
        }
        if (op.has("centerOnPositions")) {
            int i = 0; 
            centerPositionsExprs = new String[centerXExpressions.length];
            for ( var jsonEl : op.get("centerOnPositions").getAsJsonArray()) {
                centerPositionsExprs[i++] = jsonEl.getAsString();
            }
        }
    }

    public static Point2D.Float getCenterForIteration(int iteration) {
        int iGlobalMax =  GephiCommander.LayoutStatus.globalIterationsMax;

        centerPositions = new float[centerPositionsExprs.length];
        for (int i = 0; i < centerPositionsExprs.length; i++) {
            centerPositions[i] = evaluateExpression(centerPositionsExprs[i]);
        }

        float x = centerPositions == null ?
            interpolate(centerXExpressions, iteration, iGlobalMax, CameraHandler::evaluateExpression) :
            interpolate(centerXExpressions, iteration, iGlobalMax, centerPositions, CameraHandler::evaluateExpression);
        
        float y = centerPositions == null ?
            interpolate(centerYExpressions, iteration, iGlobalMax, CameraHandler::evaluateExpression) :
            interpolate(centerYExpressions, iteration, iGlobalMax, centerPositions, CameraHandler::evaluateExpression);
        
        return new Point2D.Float(x, y);
    }
    public static float getScalingForIteration(int iteration) {
        int iGlobalMax =  GephiCommander.LayoutStatus.globalIterationsMax;

        scalingPositions = new float[scalingPositionsExprs.length];
        for (int i = 0; i < scalingPositionsExprs.length; i++) {
            scalingPositions[i] = evaluateExpression(scalingPositionsExprs[i]);
        }

        return scalingPositions == null ?
            interpolate(scalingValues, iteration, iGlobalMax, CameraHandler::evaluateExpression) :
            interpolate(scalingValues, iteration, iGlobalMax, scalingPositions, CameraHandler::evaluateExpression);
        
    }
    private static Float evaluateExpression(String expression) {
        try {
            return ((Number)GephiCommander.engine.eval(expression)).floatValue();
        } catch ( ScriptException se) {
            throw new IllegalArgumentException(se);
        }
    }

    public static float interpolate(String[] values, int i, int iMax, Function<String,Float> mapper) {
        float[] positions = new float[values.length];
        // if (values.length == 1) po
        for (int j = 0; j < values.length; j++) {
            positions[j] = (float)j/(values.length-1);
        }
        return interpolate(values, i, iMax, positions, mapper);
    }
    /**
     * Interpolates between values with optional custom positions
     * @param values Array of expressions to evaluate
     * @param i Current position index (0 to iMax)
     * @param iMax Maximum position index
     * @param positions Array of custom positions
     * @param mapper Function to evaluate necessary expressions
     * @return Interpolated value
     * @throws IllegalArgumentException if inputs are invalid
     */
    public static float interpolate(String[] values, int i, int iMax, float[] positions, Function<String,Float> mapper) {
        System.out.printf("interpolate> %s %s %s %s %n",Arrays.toString(values), i, iMax, Arrays.toString(positions));
        // Validate basic inputs
        if (i < 0 || i > iMax || iMax <= 0 || values == null || values.length == 0) {
            throw new IllegalArgumentException("Invalid input parameters");
        }
        
        // Validate positions array
        if (positions.length != values.length) {
            throw new IllegalArgumentException("Positions array must match values array length");
        }
        for (float pos : positions) {
            if (pos < 0 || pos > 1) {
                throw new IllegalArgumentException("Positions must be between 0 and 1");
            }
        }
        

        // Calculate target position (0-1)
        float targetPos = (float) i / iMax;
        
        // Find interpolation bounds
        int lowerIndex, upperIndex;
        float fraction;
        
        
        // Custom position-based spacing
        lowerIndex = 0;
        upperIndex = values.length - 1;
        
        // Find the interval containing targetPos
        for (int j = 0; j < positions.length; j++) {
            if (positions[j] <= targetPos && positions[j] > positions[lowerIndex]) {
                lowerIndex = j;
            }
            if (positions[j] >= targetPos && positions[j] < positions[upperIndex]) {
                upperIndex = j;
            }
        }
        
        // Calculate fraction within the interval
        if (lowerIndex == upperIndex) {
            fraction = 0;
        } else {
            fraction = (targetPos - positions[lowerIndex]) / 
                    (positions[upperIndex] - positions[lowerIndex]);
        }

        // Evaluate and interpolate
        
        float startValue = mapper.apply(values[lowerIndex]);
        
        // If at last element or positions match (no interpolation needed)
        if (lowerIndex == values.length - 1 || lowerIndex == upperIndex) {
            return startValue;
        }
        
        float endValue = mapper.apply(values[upperIndex]);
        return startValue + fraction * (endValue - startValue);
    }
}
