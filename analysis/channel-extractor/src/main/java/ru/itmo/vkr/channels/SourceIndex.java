package ru.itmo.vkr.channels;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The parsed sources of every Maven/Gradle module under a root directory. A module is a
 * <em>service</em> when it contains a {@code @SpringBootApplication}; other modules (shared
 * libraries) are indexed only so that their constants and helper methods can be resolved.
 */
final class SourceIndex {

    record Module(String name, Path dir, boolean service, List<CompilationUnit> units) {
    }

    private static final Set<String> SKIPPED_DIRS = Set.of("target", "build", "node_modules", ".git", ".idea", ".mvn");
    private static final YAMLMapper YAML = new YAMLMapper();

    private final List<Module> modules;
    private final Map<String, List<TypeDeclaration<?>>> typesBySimpleName = new HashMap<>();
    private final List<String> warnings;

    private SourceIndex(List<Module> modules, List<String> warnings) {
        this.modules = modules;
        this.warnings = warnings;
        for (Module module : modules) {
            for (CompilationUnit unit : module.units()) {
                for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
                    typesBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new ArrayList<>()).add(type);
                }
            }
        }
    }

    static SourceIndex load(Path root) throws IOException {
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        List<String> warnings = new ArrayList<>();
        List<Module> modules = new ArrayList<>();
        for (Path dir : moduleDirs(root)) {
            List<CompilationUnit> units = new ArrayList<>();
            for (Path file : javaFiles(dir.resolve("src/main/java"))) {
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    units.add(result.getResult().get());
                } else {
                    warnings.add("Cannot parse " + root.relativize(file) + ": " + result.getProblems());
                }
            }
            boolean service = units.stream()
                    .flatMap(unit -> unit.findAll(ClassOrInterfaceDeclaration.class).stream())
                    .anyMatch(type -> Ast.hasAnnotation(type, "SpringBootApplication"));
            modules.add(new Module(serviceName(dir), dir, service, List.copyOf(units)));
        }
        return new SourceIndex(List.copyOf(modules), warnings);
    }

    List<Module> modules() {
        return modules;
    }

    List<Module> services() {
        return modules.stream().filter(Module::service).toList();
    }

    List<String> warnings() {
        return warnings;
    }

    /**
     * The type with this simple name. When several modules declare one, the first is returned:
     * acceptable for constant holders and helpers, which are shared through a common library.
     */
    Optional<TypeDeclaration<?>> type(String simpleName) {
        List<TypeDeclaration<?>> types = typesBySimpleName.get(simpleName);
        return types == null ? Optional.empty() : Optional.of(types.getFirst());
    }

    private static List<Path> moduleDirs(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root, 4)) {
            return paths.filter(Files::isDirectory)
                    .filter(dir -> !isSkipped(root, dir))
                    .filter(dir -> Files.isDirectory(dir.resolve("src/main/java")))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> javaFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isSkipped(Path root, Path dir) {
        for (Path part : root.relativize(dir)) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code spring.application.name}, which is also the name other services use to reach it
     * through discovery; the directory name when it is not set literally.
     */
    private static String serviceName(Path moduleDir) {
        Path resources = moduleDir.resolve("src/main/resources");
        for (String file : List.of("application.yml", "application.yaml", "bootstrap.yml", "bootstrap.yaml")) {
            Path config = resources.resolve(file);
            if (Files.isRegularFile(config)) {
                try {
                    JsonNode name = YAML.readTree(config.toFile()).path("spring").path("application").path("name");
                    if (name.isTextual() && !name.asText().contains("${")) {
                        return name.asText();
                    }
                } catch (IOException ignored) {
                    // unreadable configuration: fall back to the directory name
                }
            }
        }
        Path properties = resources.resolve("application.properties");
        if (Files.isRegularFile(properties)) {
            try (InputStream in = Files.newInputStream(properties)) {
                Properties loaded = new Properties();
                loaded.load(in);
                String name = loaded.getProperty("spring.application.name");
                if (name != null && !name.contains("${")) {
                    return name.trim();
                }
            } catch (IOException ignored) {
                // unreadable configuration: fall back to the directory name
            }
        }
        return moduleDir.getFileName().toString();
    }
}
