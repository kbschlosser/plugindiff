import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginDiff {

  record EntryInfo(String path, String method, String hash, String metadata) {}

  public static void main(String[] args) {
    if (args.length < 2) {
      printUsage();
      return;
    }

    var command = args[0];
    var file1 = Paths.get(args[1]);
    if (!Files.exists(file1)) {
      System.err.println("File not found: " + args[1]);
      return;
    }

    if (command.equals("analyze") && args.length == 2) {
      analyze(file1);
    } else if (command.equals("compare") && args.length == 3) {
      var file2 = Paths.get(args[2]);
      if (!Files.exists(file2)) {
        System.err.println("File not found: " + args[2]);
        return;
      }
      compare(file1, file2);
    } else {
      printUsage();
    }
  }

  private static void analyze(Path file) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to get SHA-256 instance: " + e.getMessage());
    }

    List<EntryInfo> entryInfos = new ArrayList<>();

    try (var zip = new ZipFile(file.toFile())) {
      final var finalDigest = digest;
      zip.stream().forEach(zipEntry -> {
        String hash = "";
        if (!zipEntry.isDirectory()) {
          try (var is = zip.getInputStream(zipEntry)) {
            hash = bytesToHex(finalDigest.digest(is.readAllBytes()));
          } catch (IOException e) {
            throw new RuntimeException("Failed to read zip entry " + zipEntry.getName() + ": " + e.getMessage());
          }
        }
        String method = zipEntry.getMethod() == ZipEntry.STORED ? "S" : "D";
        entryInfos.add(new EntryInfo(zipEntry.getName(), method, hash,
            zipEntry.getExtra() == null ? "" : bytesToHex(zipEntry.getExtra())));
      });
    } catch (IOException e) {
      throw new RuntimeException("Failed to open zip file: " + e.getMessage());
    }

    entryInfos.sort(Comparator.comparing(EntryInfo::path));

    String prevPath = "";
    for (var entry : entryInfos) {
      String path = entry.path();
      int prefixLen = sharedPrefixLength(prevPath, path);
      prevPath = path;

      System.out.printf(
          "%s%s:%s:%s:%s%n",
          prefixLen > 0 ? prefixLen + ":" : "",
          path.substring(prefixLen),
          entry.method(),
          entry.hash(),
          entry.metadata()
      );
    }
  }

  private static void compare(Path file1, Path file2) {
    var map1 = parse(file1);
    var map2 = parse(file2);
    var all = new TreeSet<String>();
    all.addAll(map1.keySet());
    all.addAll(map2.keySet());

    var only1 = new ArrayList<String>();
    var only2 = new ArrayList<String>();
    var diffContent = new ArrayList<String>();
    var diffMeta = new ArrayList<String>();

    for (var p : all) {
      var e1 = map1.get(p);
      var e2 = map2.get(p);
      if (e1 == null) only2.add(p);
      else if (e2 == null) only1.add(p);
      else {
        if (!e1.method().equals(e2.method()) && e1.hash().equals(e2.hash())) diffContent.add(p);
        else if (!e1.metadata().equals(e2.metadata())) diffMeta.add(p);
      }
    }

    System.out.printf("Comparing %s and %s%n", file1.getFileName(), file2.getFileName());
    if (only1.isEmpty() && only2.isEmpty() && diffContent.isEmpty() && diffMeta.isEmpty()) {
      System.out.println("No differences found.");
      return;
    }

    if (!only1.isEmpty()) {
      System.out.printf("%nExists in %s but not in %s:%n", file1.getFileName(), file2.getFileName());
      only1.forEach(p -> System.out.println("  " + p));
    }
    if (!only2.isEmpty()) {
      System.out.printf("%nExists in %s but not in %s:%n", file2.getFileName(), file1.getFileName());
      only2.forEach(p -> System.out.println("  " + p));
    }
    if (!diffContent.isEmpty()) {
      System.out.println("\nDifferent content (method/hash):");
      diffContent.forEach(p -> System.out.println("  " + p));
    }
    if (!diffMeta.isEmpty()) {
      System.out.println("\nDifferent metadata:");
      diffMeta.forEach(p -> System.out.println("  " + p));
    }
  }


  private static Map<String, EntryInfo> parse(Path file) {
    Map<String, EntryInfo> map = new LinkedHashMap<>();
    String prev = "";
    try {
      for (String line : Files.readAllLines(file)) {
        if (line.isBlank()) continue;

        int prefix = 0;
        String[] info;
        String[] parts = line.split(":", 5);

        if (parts.length == 5) {
          prefix = Integer.parseInt(parts[0]);
          info = Arrays.copyOfRange(parts, 1, 5);
        } else {
          info = parts;
        }

        String path = prev.substring(0, prefix) + info[0];
        map.put(path, new EntryInfo(path, info[1], info[2], info.length > 3 ? info[3] : ""));
        prev = path;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse file " + file + ": " + e.getMessage());
    }

    return map;
  }

  private static int sharedPrefixLength(String a, String b) {
    int minLen = Math.min(a.length(), b.length());
    for (int i = 0; i < minLen; i++) {
      if (a.charAt(i) != b.charAt(i)) {
        return i;
      }
    }
    return minLen;
  }

  private static String bytesToHex(byte[] bytes) {
    var hex = new StringBuilder();
    for (byte b : bytes) hex.append("%02x".formatted(b));
    return hex.toString();
  }

  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("java PluginDiff analyze <file.zip/jar>");
    System.err.println("java PluginDiff compare <file1> <file2>");
  }

}