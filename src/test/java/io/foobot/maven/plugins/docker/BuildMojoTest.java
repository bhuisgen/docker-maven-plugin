package io.foobot.maven.plugins.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Objects;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.foobot.maven.plugins.docker.BuildMojo;

@RunWith(MockitoJUnitRunner.class)
public class BuildMojoTest extends AbstractMojoTestCase {
    @Mock
    private MavenSession session;

    @Mock
    private MojoExecution execution;

    @Mock
    private Settings settings;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/docker");
    }

    @Test
    public void testBuild() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertFileExists("target/docker/Dockerfile");
        assertFileExists("target/docker/root/test");
    }
    
    @Test
    public void testBuildWithResources() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build-resources.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertFileExists("target/docker/root/resource");
    }

    private BuildMojo setupMojo(final File pom) throws Exception {
        MavenProject project = new ProjectStub(pom);
        MavenSession session = newMavenSession(project);

        MojoExecution execution = newMojoExecution("build");
        BuildMojo mojo = (BuildMojo) this.lookupConfiguredMojo(session, execution);
        mojo.buildDirectory = new File("target");
        mojo.session = session;
        mojo.execution = execution;

        return mojo;
    }

    private void deleteDirectory(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.exists(path)) {
            Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileDeleter());
        }
    }

    private static class FileDeleter extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc == null) {
                Files.delete(dir);

                return FileVisitResult.CONTINUE;
            }

            throw exc;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Objects.requireNonNull(file);
            Files.delete(file);

            return FileVisitResult.CONTINUE;
        }
    }

    private static void assertFileExists(final String path) {
        assertTrue(path + " does not exist", new File(path).exists());
    }
}
