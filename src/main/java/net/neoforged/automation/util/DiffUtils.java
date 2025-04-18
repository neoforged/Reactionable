package net.neoforged.automation.util;

import java.util.ArrayList;
import java.util.List;

public class DiffUtils {
    private static final String DEV_NULL = "/dev/null";

    public static List<String> detectChangedFiles(String[] diffByLine) {
        final List<String> modified = new ArrayList<>();
        for (int i = 0; i < diffByLine.length - 1; i++) {
            final String line = diffByLine[i];
            if (line.startsWith("--- ")) {
                if (diffByLine[i + 1].startsWith("+++ ")) {
                    var removedName = line.substring(4).trim();

                    if (removedName.equals(DEV_NULL)) {
                        modified.add(diffByLine[i + 1].substring(4).trim());
                    } else {
                        modified.add(removedName);
                    }

                    i++; // Skip the add marker
                }
            }
        }
        return modified;
    }
}
