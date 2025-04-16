package gephicommander;

import java.awt.geom.Point2D;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class CameraHandler {
    private static float[] scalingFloats = null;
    private static String[] scalingExpressions = null;

    private static String[] centerXExpressions;
    private static float[] centerXFloats;
    private static String[] centerYExpressions;
    private static float[] centerYFloats;
    

    public static boolean hasScaling() {
        return scalingFloats != null || scalingExpressions != null;
    }
    public static boolean hasCenterOn() {
        return (centerXFloats != null || centerXExpressions != null) &&
                (centerYFloats != null || centerYExpressions != null);   
    }

    public static void apply(JsonObject op) {
        if (op.has("scaling")) {
            ParsedArrayWrapper paw = processProvidedValue(op.get("scaling"));
            if (paw.floats != null)
                scalingFloats = paw.floats;
            else
                scalingExpressions = paw.strings;
        }
        
        if (op.has("centerOn")) {
            centerXExpressions = op.get("centerOn").getAsJsonArray()
                .asList()
                .stream()
                .map(JsonElement::getAsJsonArray)
                .map(jarr -> jarr.get(0).getAsString() )
                .collect(Collectors.toList())
                .toArray(new String[0]);
            
            centerXFloats = new float[centerXExpressions.length];
            try {
                for (int i = 0; i < centerXExpressions.length; i++) 
                    centerXFloats[i] = Float.parseFloat(centerXExpressions[i]);
            } catch (Exception e) {
                centerXFloats = null;
            }

            centerYExpressions = op.get("centerOn").getAsJsonArray()
                .asList()
                .stream()
                .map(JsonElement::getAsJsonArray)
                .map(jarr -> jarr.get(1).getAsString() )
                .collect(Collectors.toList())
                .toArray(new String[0]);
            
            centerYFloats = new float[centerYExpressions.length];
            try {
                for (int i = 0; i < centerYExpressions.length; i++) 
                    centerYFloats[i] = Float.parseFloat(centerYExpressions[i]);
            } catch (Exception e) {
                centerYFloats = null;
            }
        }
    }

    private static ParsedArrayWrapper processProvidedValue(JsonElement userValue) {
        // el.getAsJsonArray().asList().stream().map(JsonElement::getAsJsonPrimitive).allMatch(JsonPrimitive::isj)
        var paw = new ParsedArrayWrapper();
        if (userValue.isJsonPrimitive()) {
            String expr = userValue.getAsString();
            paw.strings = new String[] {expr};
        }
        else if (userValue.isJsonArray()) {
            var arr = userValue.getAsJsonArray();
            try {
                paw.floats = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) 
                    paw.floats[i] = arr.get(i).getAsFloat();
            } catch (Exception e) {
                System.out.println("Scaling array is not float constants");
                paw.floats = null;
                paw.strings = new String[arr.size()];
                for (int i = 0; i < arr.size(); i++) 
                    paw.strings[i] = arr.get(i).getAsString();
            }
        }
        return paw;
    }

    public static Point2D.Float getCenterForIteration(int iteration) {
        int iGlobalMax =  GephiCommander.LayoutStatus.globalIterationsMax;

        float x = centerXFloats != null ?
            interpolate(centerXFloats, iteration, iGlobalMax) :
            interpolate(GephiCommander.engine, centerXExpressions, iteration, iGlobalMax);
        
        float y = centerYFloats != null ?
            interpolate(centerYFloats, iteration, iGlobalMax) :
            interpolate(GephiCommander.engine, centerYExpressions, iteration, iGlobalMax);
        
        return new Point2D.Float(x, y);
    }
    public static float getScalingForIteration(int iteration) {
        int iGlobalMax =  GephiCommander.LayoutStatus.globalIterationsMax;
        
        return scalingFloats != null ? 
            interpolate(scalingFloats, iteration, iGlobalMax) :
            interpolate(GephiCommander.engine, scalingExpressions, iteration, iGlobalMax);
        
    }
    public static float interpolate(ScriptEngine engine, String[] arr, int i, int iMax) {
        if (i < 0 || i > iMax || iMax <= 0 || arr == null || arr.length == 0)
            throw new IllegalArgumentException("Invalid input parameters");
        
        float ratio = (float) i / iMax;
        float exactPos = ratio * (arr.length - 1);
        int lowerIndex = (int) exactPos;
        float fraction = exactPos - lowerIndex;

        
        
        float start,end;
        try {
            start = ((Number)engine.eval(arr[lowerIndex])).floatValue();
            if (lowerIndex == arr.length - 1) return start;
            
            end = ((Number)engine.eval(arr[lowerIndex+1])).floatValue();
        } catch (ScriptException e) {
            String msg = String.format("Failed to evaluate %s in ScriptEngine", arr[lowerIndex]);
            throw new RuntimeException(msg,e);
        }
        

        

        return start + fraction * (end - start);
    }
    public static float interpolate(float[] arr, int i, int iMax) {
        if (i < 0 || i > iMax || iMax <= 0 || arr == null || arr.length == 0)
            throw new IllegalArgumentException("Invalid input parameters");
        
        
        float ratio = (float) i / iMax;
        float exactPos = ratio * (arr.length - 1);
        int lowerIndex = (int) exactPos;
        float fraction = exactPos - lowerIndex;
    
        // If i == iMax, return the last element to avoid index issues
        if (lowerIndex == arr.length - 1) {
            return arr[lowerIndex];
        }
    
        // Linear interpolation between arr[lowerIndex] and arr[lowerIndex + 1]
        return arr[lowerIndex] + fraction * (arr[lowerIndex + 1] - arr[lowerIndex]);
    }

    // private static float interpolate(String start, String end, int i, int iMax) {
    //     return start+((float)i/iMax)*(end-start);
    // }
    // private static float interpolate(float start, float end, int i, int iMax) {
    //     return start+((float)i/iMax)*(end-start);
    // }
    private static class ParsedArrayWrapper {
        float[] floats;
        String[] strings;
    }
}
