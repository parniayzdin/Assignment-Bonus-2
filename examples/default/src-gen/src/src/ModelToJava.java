import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ModelToJava {
    static final Pattern LABEL = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((1|N)\\)\\s*$");

    private class Edge {
        String src, target, kind, label;
        Edge(String s, String t, String k, String l){
            src = s;
            target = t;
            kind = k;
            label = l;
        }
    }

    public static String cleanText(String s){
        if (s == null){
            return "";
        }else{
            s = s.replaceAll("<[^>]+>", ""); //Remove very basic HTML tags
            s = s.replace("&nbsp;", " ");
            return s.trim();
        }
    }

    public static String toClassName(String name){
        String cleaned = cleanText(name);
        String[] parts = cleaned.split("[^A-Za-z0-9]+");
        StringBuffer sb = new StringBuffer();
        for (String p : parts){
            if (p.isEmpty()){
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if(p.length() > 1){
                sb.append(p.substring(1));
            }
            return sb.length == 0;
        }
    }
    public static String matchArrow(String style){
        if (style == null){
            style = "";
        }
        if (style.contains("endArrow=block") && style.contains("endFill=0")){
            return "SPECIALIZATION";
        }
        return "ASSOCIATION";
    }

    public static void fail(String message){
        throw new RuntimeException(message);
    }

    public static void main(String[] args){
        if (args.length != 2){
            System.out.println("Usage: java ModelToJava <input.drawio> <outputDir>");
        }
    }

}