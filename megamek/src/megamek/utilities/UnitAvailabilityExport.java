package megamek.utilities;

import megamek.client.ratgenerator.AvailabilityRating;
import megamek.client.ratgenerator.ChassisRecord;
import megamek.client.ratgenerator.ModelRecord;
import megamek.client.ratgenerator.RATGenerator;
import megamek.common.eras.Eras;
import megamek.common.loaders.MekSummary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
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
            List<ModelRecord> models = safeSnapshot(ratGen.getModelList());

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

                    for (AvailabilityRating ar : ratings) {
                        writer.write(tsv(
                              unitName,
                              chassis,
                              model,
                              mulId,
                              String.valueOf(modelRecord.getIntroYear()),
                              getEraCode(era),
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
            List<ChassisRecord> chassisRecords = safeSnapshot(ratGen.getChassisList());

            int rows = 0;

            for (Integer era : eras) {
                for (ChassisRecord chassisRecord : chassisRecords) {
                    Collection<AvailabilityRating> ratings = ratGen.getChassisFactionRatings(era,
                          chassisRecord.getKey());
                    if (ratings == null || ratings.isEmpty()) {
                        continue;
                    }

                    for (AvailabilityRating ar : ratings) {
                        writer.write(tsv(
                              chassisRecord.getChassis(),
                              getEraCode(era),
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
        int stableTicks = 0;
        int lastEras = -1, lastModels = -1, lastChassis = -1;
        int tries = 0;
        while (stableTicks < 8) {
            Thread.sleep(500);
            if (!ratGen.isInitialized()
                    || ratGen.getEraSet().isEmpty()
                    || ratGen.getModelList().isEmpty()
                    || ratGen.getChassisList().isEmpty()) {
                stableTicks = 0;
            } else {
                int eras = ratGen.getEraSet().size();
                int models = ratGen.getModelList().size();
                int chassis = ratGen.getChassisList().size();
                if (eras == lastEras && models == lastModels && chassis == lastChassis) {
                    stableTicks++;
                } else {
                    stableTicks = 0;
                    lastEras = eras;
                    lastModels = models;
                    lastChassis = chassis;
                }
            }
            tries++;
            if (tries > 600) {
                throw new IllegalStateException("RATGenerator did not finish loading in time.");
            }
        }
    }

    /** Snapshot a live collection, retrying if the backing map is concurrently modified. */
    private static <T> List<T> safeSnapshot(Collection<T> collection) {
        while (true) {
            try {
                return new ArrayList<>(collection);
            } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException ignored) {
                // backing HashMap was modified mid-copy; retry
            }
        }
    }

    private static String getEraCode(int eraYear) {
        return Eras.getEra(eraYear).code();
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