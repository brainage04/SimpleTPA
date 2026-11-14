package io.github.brainage04.simpletpa;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.Test;

class SimpleTPAMetadataTest {
    @Test
    void fabricLoaderBootsInServerModeForTests() {
        assertEquals(EnvType.SERVER, FabricLoader.getInstance().getEnvironmentType());
    }

    @Test
    void fabricLoaderCanResolveModMetadata() {
        ModContainer mod = simpleTpa();
        ModMetadata metadata = mod.getMetadata();

        assertAll(
                () -> assertEquals(SimpleTPA.MOD_ID, metadata.getId()),
                () -> assertEquals(SimpleTPA.MOD_NAME, metadata.getName()),
                () -> assertTrue(metadata.getLicense().contains("MIT")),
                () -> assertTrue(mod.findPath("fabric.mod.json").isPresent()));
    }

    @Test
    void brainageLibDependencyStopsBelowTheNextMajorVersion() throws Exception {
        ModDependency brainageLib =
                simpleTpa().getMetadata().getDependencies().stream()
                        .filter(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS)
                        .filter(dependency -> dependency.getModId().equals("brainagelib"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "SimpleTPA does not depend on BrainageLib."));
        Version built =
                FabricLoader.getInstance()
                        .getModContainer("brainagelib")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion();

        assertAll(
                () -> assertTrue(brainageLib.matches(built)),
                () -> assertFalse(brainageLib.matches(Version.parse("3.0.0"))));
    }

    private static ModContainer simpleTpa() {
        return FabricLoader.getInstance()
                .getModContainer(SimpleTPA.MOD_ID)
                .orElseThrow(
                        () -> new AssertionError("Expected SimpleTPA to be loaded for tests."));
    }
}
