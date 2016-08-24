package io.foobot.maven.plugins.docker;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.DirectoryScanner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;

@Mojo(name = "build")
public class BuildMojo extends AbstractMojo {
    @Parameter(name = "${mojoExecution}", readonly = true)
    protected MojoExecution execution;

    @Parameter(name = "${session}", readonly = true)
    protected MavenSession session;

    @Parameter(name = "${settings}", readonly = true)
    protected Settings settings;

    @Parameter(property = "project.build.directory")
    protected File buildDirectory;

    @Parameter(property = "skipDocker", defaultValue = "false")
    private boolean skipDocker;

    @Parameter(property = "directory")
    private File directory;

    @Parameter(property = "forceRm", defaultValue = "false")
    private boolean forceRm;

    @Parameter(property = "noCache", defaultValue = "false")
    private boolean noCache;

    @Parameter(property = "pull", defaultValue = "false")
    private boolean pull;
    
    @Parameter(property = "imageName")
    private String imageName;
    
    @Parameter(property = "imageTags")
    private List<String> imageTags;
    
    @Parameter(property = "push", defaultValue = "false")
    private boolean push;
    
    @Parameter(property = "remove", defaultValue = "false")
    private boolean remove;

    @Parameter(property = "resources")
    private List<Resource> resources;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipDocker) {
            getLog().info("Skipping docker build");
            return;
        }

        validateParameters();
        
        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock").withDockerTlsVerify(false).build();
        DockerClient dockerClient = null;

        try {
            dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build();

            build(dockerClient);
        } catch (Exception e) {
            throw new MojoExecutionException("Error during plugin execution", e);
        } finally {
            if (dockerClient != null) {
                try {
                    dockerClient.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void validateParameters() throws MojoExecutionException {
        if (directory == null) {
            throw new MojoExecutionException("missing option 'directory'");
        }

        if (imageName == null) {
            throw new MojoExecutionException("missing option 'imageName'");
        }
    }

    private void build(DockerClient dockerClient) throws IOException {
        getLog().info("Building image ...");
        
        Resource dockerResource = new Resource();
        dockerResource.setDirectory(directory.toString());
        resources.add(dockerResource);

        Path buildPath = Paths.get(buildDirectory.toString(), "docker");

        for (Resource resource : resources) {
            List<String> includes = resource.getIncludes();
            List<String> excludes = resource.getExcludes();

            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(new File(resource.getDirectory()));
            scanner.setIncludes(includes.isEmpty() ? null : includes.toArray(new String[includes.size()]));
            scanner.setExcludes(excludes.isEmpty() ? null : excludes.toArray(new String[excludes.size()]));
            scanner.scan();

            String[] includedFiles = scanner.getIncludedFiles();
            if (includedFiles.length == 0)
                continue;

            boolean copyDirectory = includes.isEmpty() && excludes.isEmpty() && (resource.getTargetPath() != null);
            if (copyDirectory) {
                Path source = Paths.get(resource.getDirectory());
                Path destination = Paths.get(buildPath.toString(), resource.getTargetPath());

                Files.createDirectories(destination);
                Files.walkFileTree(source, new CopyDirectory(source, destination));
            } else {
                for (String file : includedFiles) {
                    Path source = Paths.get(resource.getDirectory()).resolve(file);
                    Path destination = Paths.get(buildPath.toString(),
                            (resource.getTargetPath() == null ? "" : resource.getTargetPath())).resolve(file);

                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }

        BuildImageResultCallback callback = new BuildImageResultCallback() {
            @Override
            public void onNext(BuildResponseItem item) {
                if (item.getStream() != null) {
                    getLog().info(item.getStream().trim());
                }

                super.onNext(item);
            }
        };

        String imageId = dockerClient.buildImageCmd(buildPath.toFile()).withForcerm(forceRm).withNoCache(noCache)
                .withPull(pull).exec(callback).awaitImageId();

        if (imageTags.isEmpty()) {
            dockerClient.tagImageCmd(imageId, imageName, "latest").exec();
        } else {
            for (String imageTag : imageTags) {
                dockerClient.tagImageCmd(imageId, imageName, imageTag).exec();
            }
        }
        
        if (push) {
            getLog().info("Pushing image ...");
            
            if (imageTags.isEmpty()) {
                PushImageCmd cmd = dockerClient.pushImageCmd(imageName);
                
                cmd.withTag("latest").exec(new PushImageResultCallback()).awaitSuccess();
            } else {
                for (String imageTag : imageTags) {
                    PushImageCmd cmd = dockerClient.pushImageCmd(imageName);
                    
                    cmd.withTag(imageTag).exec(new PushImageResultCallback()).awaitSuccess();
                }
            }
        }
        
        if (remove) {
            getLog().info("Removing image ...");
            
            RemoveImageCmd cmd = dockerClient.removeImageCmd(imageId);
            
            cmd.withForce(true).exec();
        }
    }

    private static class CopyDirectory extends SimpleFileVisitor<Path> {
        private Path sourcePath;
        private Path destinationPath;

        public CopyDirectory(Path sourcePath, Path destinationPath) {
            this.sourcePath = sourcePath;
            this.destinationPath = destinationPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path target = destinationPath.resolve(sourcePath.relativize(dir));

            if (Files.notExists(target)) {
                Files.createDirectories(destinationPath.resolve(sourcePath.relativize(dir)));
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.copy(file, destinationPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);

            return FileVisitResult.CONTINUE;
        }
    }
}
