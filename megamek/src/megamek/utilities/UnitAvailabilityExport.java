package megamek.utilities;

import megamek.client.ratgenerator.AvailabilityRating;
import megamek.client.ratgenerator.ChassisRecord;
import megamek.client.ratgenerator.ModelRecord;
import megamek.client.ratgenerator.RATGenerator;
import megamek.common.loaders.MekSummary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class UnitAvailabilityExport {

    public static void main(String[] args) throws Exception {
        Path outDir = (args.length > 0)
                ? Path.of(args[0])
                : Path.of("build/availability-export");

        Files.createDirectories(outDir);

        RATGenerator ratGen = RATGenerator.getInstance();
        waitForRatGenerator(ratGen);

        exportModelAvailability(outDir.resolve("model_availability.tsv"), ratGen);
        exportChassisAvailability(outDir.resolve("chassis_availability.tsv"), ratGen);
    }

    private static void exportModelAvailability(Path outFile, RATGenerator ratGen) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            writer.write(String.join("\t",
                    "Unit Name",
                    "Chassis",
                    "Model",
                    "MUL ID",
                    "Intro Year",
                    "Era",
                    "Faction",
                    "Availability",
                    "Start Year"));
            writer.newLine();

            Set<Integer> eras = new TreeSet<>(ratGen.getEraSet());
            List<ModelRecord> models = new ArrayList<>(ratGen.getModelList());

            int rows = 0;

            for (Integer era : eras) {
                for (ModelRecord modelRecord : models) {
                    Collection<AvailabilityRating> ratings = ratGen.getModelFactionRatings(era, modelRecord.getKey());
                    if (ratings == null || ratings.isEmpty()) {
                        continue;
                    }

                    MekSummary ms = modelRecord.getMekSummary();
                    String unitName = modelRecord.getKey();
                    String chassis = (ms == null) ? modelRecord.getChassis() : ms.getChassis();
                    String model = (ms == null) ? "" : ms.getModel();
                    String mulId = (ms == null) ? "" : String.valueOf(ms.getMulId());

                    for (AvailabilityRating ar : new ArrayList<>(ratings)) {
                        writer.write(tsv(
                              unitName,
                              chassis,
                              model,
                              mulId,
                              String.valueOf(modelRecord.getIntroYear()),
                              String.valueOf(era),
                              ar.getFactionCode(),
                              String.valueOf(ar.getAvailability()),
                              String.valueOf(ar.getStartYear())
                        ));
                        writer.newLine();
                        rows++;
                    }
                }
            }

            System.out.println("Wrote " + rows + " model availability rows to " + outFile.toAbsolutePath());
        }
    }

    private static void exportChassisAvailability(Path outFile, RATGenerator ratGen) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            writer.write(String.join("\t",
                    "Chassis",
                    "Era",
                    "Faction",
                    "Availability",
                    "Start Year"));
            writer.newLine();

            Set<Integer> eras = new TreeSet<>(ratGen.getEraSet());
            List<ChassisRecord> chassisRecords = new ArrayList<>(ratGen.getChassisList());

            int rows = 0;

            for (Integer era : eras) {
                for (ChassisRecord chassisRecord : chassisRecords) {
                    Collection<AvailabilityRating> ratings = ratGen.getChassisFactionRatings(era,
                          chassisRecord.getKey());
                    if (ratings == null || ratings.isEmpty()) {
                        continue;
                    }

                    for (AvailabilityRating ar : new ArrayList<>(ratings)) {
                        writer.write(tsv(
                              chassisRecord.getChassis(),
                              String.valueOf(era),
                              ar.getFactionCode(),
                              String.valueOf(ar.getAvailability()),
                              String.valueOf(ar.getStartYear())
                        ));
                        writer.newLine();
                        rows++;
                    }
                }
            }

            System.out.println("Wrote " + rows + " chassis availability rows to " + outFile.toAbsolutePath());
        }
    }

    private static void waitForRatGenerator(RATGenerator ratGen) throws InterruptedException {
        int tries = 0;
        int stableTicks = 0;
        int lastEraCount = -1;
        int lastModelCount = -1;
        int lastChassisCount = -1;

        while (true) {
            int eraCount = ratGen.getEraSet().size();
            int modelCount = ratGen.getModelList().size();
            int chassisCount = ratGen.getChassisList().size();

            boolean ready = ratGen.isInitialized() && (eraCount > 0) && (modelCount > 0) && (chassisCount > 0);
            boolean sameAsLast = (eraCount == lastEraCount)
                  && (modelCount == lastModelCount)
                  && (chassisCount == lastChassisCount);

            if (ready && sameAsLast) {
                stableTicks++;
                if (stableTicks >= 8) {
                    return;
                }
            } else {
                stableTicks = 0;
            }

            lastEraCount = eraCount;
            lastModelCount = modelCount;
            lastChassisCount = chassisCount;

            Thread.sleep(250);
            tries++;
            if (tries > 1200) {
                throw new IllegalStateException("RATGenerator did not finish loading in time.");
            }
        }
    }

    private static String tsv(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append('\t');
            }
            sb.append(clean(values[i]));
        }
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
    }
}