package dev.zeli.cleanbill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CleanConfig {
    public static final long DEFAULT_INTERVAL = 20 * 60L;
    public static final long DEFAULT_COUNTDOWN = 5L;
    public static final long DEFAULT_MIN_AGE = 30L;
    public static final long DEFAULT_WASH = 60 * 60L;
    public static final List<Long> DEFAULT_ALERTS = List.of(10 * 60L, 60L, 30L);
    public static final String DEFAULT_ALERT_MESSAGE = "<#D0D0D0>Cleanup in <#D98C8C>{time}";
    public static final String DEFAULT_COUNTDOWN_MESSAGE = "<#D0D0D0>Cleanup in <#D98C8C>{time}";
    public static final String DEFAULT_CLEAR_MESSAGE = "<#D0D0D0>Cleared <#91C788>{count} <#D0D0D0>items. Next: <#D98C8C>{next}";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern DURATION = Pattern.compile("^([1-9][0-9]*)([smSM])$");
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final int MAX_BACKUPS = 5;

    public static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("cleanbill.json");
    public static final Path BACKUPS = FMLPaths.CONFIGDIR.get().resolve("cleanbill-backups");
    private static final Path LEGACY_FILE = FMLPaths.CONFIGDIR.get().resolve("quackyclean.json");
    private static volatile Values values = new Values();

    private CleanConfig() {}

    public static Values get() {
        return values;
    }

    public static synchronized void load() throws IOException {
        if (!Files.exists(FILE) && Files.exists(LEGACY_FILE)) {
            Files.copy(LEGACY_FILE, FILE);
        }
        if (!Files.exists(FILE)) {
            values = new Values();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Values loaded = GSON.fromJson(reader, Values.class);
            if (loaded == null) throw new JsonParseException("Empty configuration");
            loaded.validate();
            values = loaded;
        }
    }

    public static synchronized void save() throws IOException {
        values.validate();
        Files.createDirectories(FILE.getParent());
        Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) {
            GSON.toJson(values, writer);
        }
        try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static synchronized Path resetWithBackup() throws IOException {
        Path backup = backupCurrent();
        values = new Values();
        save();
        return backup;
    }

    public static synchronized Path restoreLatest() throws IOException {
        List<Path> backups = backupsNewestFirst();
        if (backups.isEmpty()) return null;
        Path target = backups.getFirst();
        backupCurrent();
        Files.copy(target, FILE, StandardCopyOption.REPLACE_EXISTING);
        load();
        return target;
    }

    private static Path backupCurrent() throws IOException {
        Files.createDirectories(BACKUPS);
        if (!Files.exists(FILE)) save();
        Path backup = BACKUPS.resolve("cleanbill-" + BACKUP_TIME.format(Instant.now()) + ".json");
        Files.copy(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
        List<Path> backups = backupsNewestFirst();
        for (int i = MAX_BACKUPS; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
        return backup;
    }

    private static List<Path> backupsNewestFirst() throws IOException {
        if (!Files.isDirectory(BACKUPS)) return List.of();
        try (var stream = Files.list(BACKUPS)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(CleanConfig::modified).reversed())
                    .toList();
        }
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    public static long parseDuration(String input) {
        Matcher matcher = DURATION.matcher(input.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Use a positive whole number followed by s or m, such as 30s or 25m.");
        long number;
        try { number = Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("That duration is too large."); }
        try {
            long seconds = Math.multiplyExact(number, matcher.group(2).equalsIgnoreCase("m") ? 60L : 1L);
            if (seconds > Long.MAX_VALUE / 20L) throw new IllegalArgumentException("That duration is too large.");
            return seconds;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("That duration is too large."); }
    }

    public static String formatDuration(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (remainder > 0 || parts.isEmpty()) parts.add(remainder + "s");
        return String.join(" ", parts);
    }

    public static final class Values {
        public long intervalSeconds = DEFAULT_INTERVAL;
        public long countdownSeconds = DEFAULT_COUNTDOWN;
        public List<Long> alertWhenSeconds = new ArrayList<>(DEFAULT_ALERTS);
        public String alertShow = "chat";
        public String alertSound = "jukebox";
        public boolean countdownShow = true;
        public String buttonAccess = "all";
        public boolean minAgeEnabled = false;
        public long minAgeSeconds = DEFAULT_MIN_AGE;
        public boolean itemPondEnabled = true;
        public String itemPondAccess = "ops";
        public boolean itemPondWashEnabled = true;
        public long itemPondWashSeconds = DEFAULT_WASH;
        public String alertMessage = DEFAULT_ALERT_MESSAGE;
        public String countdownMessage = DEFAULT_COUNTDOWN_MESSAGE;
        public String clearMessage = DEFAULT_CLEAR_MESSAGE;

        public void validate() {
            intervalSeconds = positive(intervalSeconds, DEFAULT_INTERVAL);
            countdownSeconds = positive(countdownSeconds, DEFAULT_COUNTDOWN);
            minAgeSeconds = positive(minAgeSeconds, DEFAULT_MIN_AGE);
            itemPondWashSeconds = positive(itemPondWashSeconds, DEFAULT_WASH);
            alertShow = choice(alertShow, "chat", "actionbar", "none");
            alertSound = choice(alertSound, "jukebox", "exp", "none");
            buttonAccess = choice(buttonAccess, "ops", "all");
            itemPondAccess = choice(itemPondAccess, "ops", "all");
            if (alertWhenSeconds == null) alertWhenSeconds = new ArrayList<>();
            LinkedHashSet<Long> unique = new LinkedHashSet<>();
            alertWhenSeconds.stream().filter(value -> value != null && value > 0)
                    .sorted(Comparator.reverseOrder()).forEach(unique::add);
            alertWhenSeconds = new ArrayList<>(unique);
            alertMessage = migrateMessage(message(alertMessage, DEFAULT_ALERT_MESSAGE),
                    "&7Clearing ground items in: &c{time}", DEFAULT_ALERT_MESSAGE);
            countdownMessage = migrateMessage(message(countdownMessage, DEFAULT_COUNTDOWN_MESSAGE),
                    "&7Clearing ground items in: &c{time}", DEFAULT_COUNTDOWN_MESSAGE);
            clearMessage = migrateMessage(message(clearMessage, DEFAULT_CLEAR_MESSAGE),
                    "&7Cleared &c{count} &7ground items. Stored &a{stacks} &7stacks in the Item Pond. Next cleanup in &c{next}&7.",
                    DEFAULT_CLEAR_MESSAGE);
        }

        private static long positive(long value, long fallback) { return value > 0 ? value : fallback; }
        private static String choice(String value, String... allowed) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            for (String option : allowed) if (option.equals(normalized)) return normalized;
            return allowed[0];
        }
        private static String message(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
        private static String migrateMessage(String value, String oldDefault, String newDefault) {
            return value.equals(oldDefault) ? newDefault : value;
        }
    }
}
