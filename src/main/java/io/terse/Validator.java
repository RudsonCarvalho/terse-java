package io.terse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Temporary validation utility: reads all .terse files from a directory,
 * parses and re-serializes each one, and reports OK/DIFF/FAIL.
 */
public final class Validator {

    private Validator() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: Validator <directory>");
            System.exit(1);
        }

        Path dir = Paths.get(args[0]);
        List<Path> files = Files.list(dir)
                .filter(p -> p.toString().endsWith(".terse"))
                .sorted()
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            System.out.println("No .terse files found in " + dir);
            return;
        }

        int ok = 0, diff = 0, fail = 0;

        for (Path file : files) {
            String name = file.getFileName().toString();
            String raw = Files.readString(file);

            // Strip comment lines (lines starting with //)
            List<String> lines = new ArrayList<>();
            for (String line : raw.split("\n", -1)) {
                if (!line.startsWith("//")) {
                    lines.add(line);
                }
            }
            String stripped = String.join("\n", lines);

            try {
                Map<String, Object> doc = Terse.parseDocument(stripped);
                String reserialized = Terse.serializeDocument(doc);

                // Normalize: trim trailing newline from both for comparison
                String strippedNorm = stripped.trim();
                String reserNorm = reserialized.trim();

                if (strippedNorm.equals(reserNorm)) {
                    System.out.println("OK   " + name);
                    ok++;
                } else {
                    System.out.println("DIFF " + name);
                    // Show first differing line
                    String[] origLines = strippedNorm.split("\n", -1);
                    String[] newLines = reserNorm.split("\n", -1);
                    int maxLines = Math.max(origLines.length, newLines.length);
                    for (int i = 0; i < maxLines; i++) {
                        String origLine = i < origLines.length ? origLines[i] : "<missing>";
                        String newLine  = i < newLines.length  ? newLines[i]  : "<missing>";
                        if (!origLine.equals(newLine)) {
                            System.out.println("     line " + (i + 1) + " orig: " + origLine);
                            System.out.println("     line " + (i + 1) + " got:  " + newLine);
                        }
                    }
                    diff++;
                }
            } catch (TerseException e) {
                System.out.println("FAIL " + name + " — " + e.getMessage());
                fail++;
            } catch (Exception e) {
                System.out.println("FAIL " + name + " — unexpected: " + e.getMessage());
                fail++;
            }
        }

        System.out.println();
        System.out.println("Results: " + ok + " OK, " + diff + " DIFF, " + fail + " FAIL  (total " + files.size() + ")");
    }
}
