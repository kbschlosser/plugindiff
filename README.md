# PluginDiff

PluginDiff is a Java program that serves two purposes:
1. Analyze a plugin file and generate a structured encoding of its contents.
2. Compare two encoded files and display their differences.

---

## Build

Make sure you have Java 16 or higher installed.

```bash
javac PluginDiff.java
```

---

## Run

### Analyze a file and print output to the console

```bash
java PluginDiff analyze <archive.zip/jar>
```

### Analyze a file and write the output to a file

```bash
java PluginDiff analyze <archive.zip/jar> > <output.txt>
```

### Compare two files

```bash
java PluginDiff compare <output1.txt> <output2.txt>
```

Replace the tags with your actual file paths.
