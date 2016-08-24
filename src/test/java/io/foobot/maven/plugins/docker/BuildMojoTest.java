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
import java.util.List;
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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

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
        assertImageExists("test-build", "latest");
        
        removeImage("test-build", "latest");
    }
    
    @Test
    public void testBuildWithResources() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build-resources.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertFileExists("target/docker/root/resource");        
        assertImageExists("test-build-resources", "latest");
        
        removeImage("test-build-resources", "latest");
    }
    
    @Test
    public void testBuildWithTags() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build-tags.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertImageExists("test-build-tags", "latest");
        
        removeImage("test-build-tags", "latest");
    }
    
    @Test
    public void testBuildWithPush() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build-push.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertImageExists("127.0.0.1:5000/test-build-push", "latest");
        
        removeImage("test-build-push", "latest");
    }
    
    @Test
    public void testBuildWithRemove() throws Exception {
        File pom = getTestFile("src/test/resources/pom-build-remove.xml");
        assertNotNull("pom.xml is null", pom);
        assertTrue("pom.xml does not exist", pom.exists());

        BuildMojo mojo = setupMojo(pom);      
        mojo.execute();
        
        assertImageNotExists("test-build-remove", "latest");
        
        removeImage("test-build-remove", "latest");
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
    
    private static void assertImageExists(String imageName, String imageTag) {
        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock").withDockerTlsVerify(false).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build(); 
        
        List<Image> images = dockerClient.listImagesCmd().exec();        
        assertNotNull(images);
        
        String imageId = null;
        
        for (Image image : images) {
            for (String repoTag : image.getRepoTags()) {
                if (repoTag.equals(imageName + ":" + imageTag))
                    imageId = image.getId();
            }
        }
        
        assertNotNull(imageId);
    }
    
    private static void assertImageNotExists(String imageName, String imageTag) {
        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock").withDockerTlsVerify(false).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build(); 
        
        List<Image> images = dockerClient.listImagesCmd().exec();        
        assertNotNull(images);
        
        String imageId = null;
        
        for (Image image : images) {
            for (String repoTag : image.getRepoTags()) {
                if (repoTag.equals(imageName + ":" + imageTag))
                    imageId = image.getId();
            }
        }
        
        assertNull(imageId);
    }
    
    private static boolean removeImage(String imageName, String imageTag) {
        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock").withDockerTlsVerify(false).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build(); 
        
        List<Image> images = dockerClient.listImagesCmd().exec();        
        assertNotNull(images);
        
        String imageId = null;
        
        for (Image image : images) {
            for (String repoTag : image.getRepoTags()) {
                if (repoTag.equals(imageName + ":" + imageTag))
                    imageId = image.getId();
            }
        }
        
        if (imageId != null) {
            RemoveImageCmd cmd = dockerClient.removeImageCmd(imageId);
            
            cmd.withForce(true).exec();
            
            return true;
        }
        
        return false;
    }
}
