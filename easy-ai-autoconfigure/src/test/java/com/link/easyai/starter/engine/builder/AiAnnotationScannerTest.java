package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.builder.fixtures.NoAnnotationTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiAnnotationScanner}: finds every @AiTask class under the
 * scanned packages (including intentionally broken declarations), deduplicates,
 * and never returns unannotated classes.
 */
class AiAnnotationScannerTest {

    private static final String FIXTURES_ROOT = "com.link.easyai.starter.engine.builder.fixtures";

    private final AiAnnotationScanner scanner = new AiAnnotationScanner();

    private List<Class<?>> scan(String... packages) {
        return scanner.scan(new StandardEnvironment(),
                Thread.currentThread().getContextClassLoader(), packages);
    }

    @Test
    @DisplayName("递归扫描找到全部 7 个 @AiTask 类（含 broken/duplicate 声明）")
    void findsAllAnnotatedClassesRecursively() {
        List<Class<?>> classes = scan(FIXTURES_ROOT);

        Set<String> names = classes.stream().map(Class::getSimpleName).collect(Collectors.toSet());
        assertEquals(Set.of(
                "FixtureTaskA", "FixtureTaskB",
                "DuplicateTaskOne", "DuplicateTaskTwo",
                "BrokenTask", "UnsupportedTypeTask", "BlankTask"), names);
    }

    @Test
    @DisplayName("未标注 @AiTask 的类不会被返回")
    void skipsUnannotatedClasses() {
        List<Class<?>> classes = scan(FIXTURES_ROOT);

        assertFalse(classes.stream().anyMatch(c -> c == NoAnnotationTask.class));
        assertFalse(classes.stream().anyMatch(c -> c.getSimpleName().endsWith("Test")));
    }

    @Test
    @DisplayName("重叠包扫描按类名去重")
    void deduplicatesOverlappingPackages() {
        List<Class<?>> classes = scan(FIXTURES_ROOT, FIXTURES_ROOT + ".valid");

        long taskACount = classes.stream()
                .filter(c -> c.getSimpleName().equals("FixtureTaskA")).count();
        assertEquals(1, taskACount);
    }

    @Test
    @DisplayName("无 @AiTask 的包返回空列表")
    void returnsEmptyForPackageWithoutTasks() {
        List<Class<?>> classes = scan("com.link.easyai.starter.engine.premise");
        assertTrue(classes.isEmpty());
    }

    @Test
    @DisplayName("空/null 输入返回空列表")
    void returnsEmptyForNoPackages() {
        assertTrue(scan().isEmpty());
        assertTrue(scan((String[]) null).isEmpty());
        assertTrue(scan("  ", null).isEmpty());
    }
}
