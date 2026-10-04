package app.sevacenter.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The backend never touches the filesystem from request handling. This is the premise of the DAST
 * acceptance for ZAP's path-traversal heuristic (rule 6) on the API (docs/security/dast.md): if
 * filesystem access is ever added, this fails and forces that acceptance to be re-reviewed.
 */
class NoFilesystemAccessTest {

    static final Pattern FILESYSTEM = Pattern.compile(
            "\\bjava\\.io\\.File\\b|\\bnew File\\(|\\bjava\\.nio\\.file\\b|\\bPaths\\.get\\(|\\bPath\\.of\\("
                    + "|\\bFile(Input|Output)Stream\\b|\\bRandomAccessFile\\b|\\bFileReader\\b|\\bFileWriter\\b"
                    + "|\\b(ClassPath|FileSystem|Url|FileUrl|PathResource)Resource\\b|\\bResourceLoader\\b"
                    + "|\\bgetResource(AsStream)?\\(");

    @Test
    void noMainSourceUsesTheFilesystem() throws IOException {
        Path main = Path.of("src/main/java");
        assertThat(main).isDirectory();
        try (Stream<Path> files = Files.walk(main)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    List<String> lines = Files.readAllLines(p);
                    return java.util.stream.IntStream.range(0, lines.size())
                            .filter(i -> FILESYSTEM.matcher(lines.get(i)).find())
                            .mapToObj(i -> main.relativize(p) + ":" + (i + 1) + ": " + lines.get(i).strip());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
            assertThat(offenders).as("filesystem access in main code: re-review the DAST rule-6 acceptance").isEmpty();
        }
    }

    @Test
    void thePatternCatchesTheUsualWays() {
        for (String line : List.of("import java.io.File;", "new File(name)", "import java.nio.file.Files;",
                "Paths.get(x)", "Path.of(x)", "new FileInputStream(f)", "new ClassPathResource(x)",
                "getClass().getResourceAsStream(n)", "ResourceLoader loader")) {
            assertThat(FILESYSTEM.matcher(line).find()).as(line).isTrue();
        }
        assertThat(FILESYSTEM.matcher("String profile = \"x\"; // a FileSystemX word in prose").find()).isFalse();
    }
}
