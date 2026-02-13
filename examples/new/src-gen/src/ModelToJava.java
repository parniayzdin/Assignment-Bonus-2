import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * This program is a small proof-of-concept generator that reads a draw.io XML diagram and treats
 * labeled rectangle as a Java class. It interprets arrow as either inheritance or association with
 * multiplicities like name (1) and name (N), then validates the model and writes one java file per class.
 */

public class ModelToJava {
    static final Pattern LABEL = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((1|N)\\)\\s*$");

    private static class Edge {
        String id, src, target, kind, label;
        Edge(String id, String s, String t, String k, String l) {
            this.id = id;
            src = s;
            target = t;
            kind = k;
            label = l;
        }
    }

    public static String cleanText(String s) {
        if (s == null) {
            return "";
        } else {
            s = s.replaceAll("<[^>]+>", ""); //Remove very basic HTML tags
            s = s.replace("&nbsp;", " ");
            return s.trim();
        }
    }

    public static String toClassName(String name) {
        String cleaned = cleanText(name);
        String[] parts = cleaned.split("[^A-Za-z0-9]+");
        StringBuffer sb = new StringBuffer();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }

        }
        if (sb.length() == 0) {
            return "Unnamed";
        }

        return sb.toString();
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

    private static String getEdgeLabel(String edgeId, NodeList cells) {
        for (int i = 0; i < cells.getLength(); i++) {
            Element c = (Element) cells.item(i);

            if (!"1".equals(c.getAttribute("vertex"))) continue;
            if (!edgeId.equals(c.getAttribute("parent"))) continue;

            String style = c.getAttribute("style");
            if (style == null || !style.contains("edgeLabel")) continue;

            String value = cleanText(c.getAttribute("value"));
            if (!value.isEmpty()) return value;
        }
        return "";
    }


    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return "field";
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String plural(String s) {
        if (s == null || s.isEmpty()){
            return "items";
        }
        s = s.trim();
        if(s.endsWith("s") || s.endsWith("S")){
            return s;
        }
        return s + "s";
    }

    public static void fail(String message){
        throw new RuntimeException(message);
    }

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        if (args.length != 2) {
            System.out.println("Usage: java ModelToJava <input.drawio> <outputDir>");
            return;
        }
        String xmlFile = args[0];
        String outDir = args[1];

        //Parse the XML file
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        File file = new File(xmlFile);
        Document doc = db.parse(file);
        NodeList cells = doc.getElementsByTagName("mxCell");

        //Identify the rectangles with text
        Map<String, String> things = new HashMap<>();
        for (int i = 0; i < cells.getLength(); i++) {
            Element c = (Element) cells.item(i);

            if (!"1".equals(c.getAttribute("vertex"))) {
                continue;
            }

            String style = c.getAttribute("style");
            if (style != null && style.contains("edgeLabel")) {
                continue;
            }
            if (style != null && style.contains("strokeColor=none")) {
                continue;
            }

            String value = cleanText(c.getAttribute("value"));
            if (value.isEmpty()) {
                continue;
            }

            String id = c.getAttribute("id");
            things.put(id, toClassName(value));

        }

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < cells.getLength(); i++) {
            Element e = (Element) cells.item(i);

            if (!"1".equals(e.getAttribute("edge"))) {
                continue;
            }

            String src = e.getAttribute("source");
            String target = e.getAttribute("target");
            String kind = matchArrow(e.getAttribute("style"));
            String label = cleanText(e.getAttribute("value"));
            if (label.isEmpty()) {
                label = getEdgeLabel(e.getAttribute("id"), cells);
            }
            String edgeId = e.getAttribute("id");
            edges.add(new Edge(edgeId, src, target, kind, label));

        }

        for (Edge e : edges) {
            if ((things.get(e.src) == null) || (things.get(e.target) == null)) {
                System.out.println("Arrow must connect Two Things");
                return;
            }
            if (e.kind.equals("ASSOCIATION")) {
                if (e.label.isEmpty()) {
                    System.out.println("Missing association label on edgeId=" + e.id + " src=" + things.get(e.src) + " target=" + things.get(e.target));
                    return;
                }
                if (!LABEL.matcher(e.label).matches()) {
                    System.out.println("Association label must match: name (1) or name (N). Found: " + e.label);
                    return;
                }
            }
        }
        //Specialization must not be circular
        Map<String, String> parent = new HashMap<>();
        for (Edge e: edges){
            if (e.kind.equals("SPECIALIZATION")){
                parent.put(e.src, e.target);
            }
        }
        for (String nodeId : things.keySet()){
            String current = nodeId;
            while (parent.containsKey(current)){
                current = parent.get(current);
                if(current.equals(nodeId)){
                    System.out.println("Specialization must not be circular");
                    return;
                }
            }
        }

        Files.createDirectories(Paths.get(outDir));
        for (String thingId : things.keySet()){
            String className = things.get(thingId);

            //Find superclass, if any
            String superClass = null;
            for (Edge e: edges){
                if (e.kind.equals("SPECIALIZATION") && e.src.equals(thingId)){
                    superClass = things.get(e.target);
                }
            }

            List<String> fieldLines = new ArrayList<>();
            boolean needList = false;

            for (Edge e : edges){
                if (e.kind.equals("ASSOCIATION") && e.src.equals(thingId)){
                    Matcher m = LABEL.matcher(e.label);

                    if (!m.matches()){
                        System.out.println("Bad association label: " + e.label + " (expected: name (1) or name (N))");
                        return;
                    }
                    String relationName = m.group(1);
                    String mult = m.group(2);
                    String targetClass = things.get(e.target);

                    String fieldName = relationName;
                    if (relationName.equalsIgnoreCase("has")) {
                        fieldName = lowerFirst(targetClass);
                    }

                    if (mult.equals("1")) {
                        fieldLines.add("  private " + targetClass + " " + fieldName + ";");
                    } else {
                        needList = true;
                        fieldLines.add("  private List<" + targetClass + "> " + plural(fieldName) + " = new ArrayList<>();");
                    }

                }
            }

            StringBuffer sb = new StringBuffer();
            if (needList){
                sb.append("import java.util.*; \n\n");
            }
            sb.append("public class ").append(className);
            if(superClass != null){
                sb.append(" extends ").append(superClass);
            }
            sb.append(" {\n");
            for (String line : fieldLines){
                sb.append(line).append("\n");
            }
            sb.append("}\n");
            String results = sb.toString();
            Path outPath = Paths.get(outDir, className + ".java");
            Files.write(outPath, results.getBytes());
        }
        System.out.println("Generated " + things.size() + " classes into " + outDir);
    }

}