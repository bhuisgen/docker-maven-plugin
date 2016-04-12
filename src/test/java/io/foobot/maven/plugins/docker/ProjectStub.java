package io.foobot.maven.plugins.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.codehaus.plexus.util.ReaderFactory;

public class ProjectStub extends MavenProjectStub {

    public ProjectStub(File pom) {
        MavenXpp3Reader pomReader = new MavenXpp3Reader();
        Model model;
        try {
            model = pomReader.read(ReaderFactory.newXmlReader(pom));
            setModel(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        setGroupId(model.getGroupId());
        setArtifactId(model.getArtifactId());
        setVersion(model.getVersion());
        setName(model.getName());
        setUrl(model.getUrl());
        setPackaging(model.getPackaging());
        setBuild(model.getBuild());

        List<String> compileSourceRoots = new ArrayList<>();
        compileSourceRoots.add(getBasedir() + "/src/main/java");
        setCompileSourceRoots(compileSourceRoots);

        List<String> testCompileSourceRoots = new ArrayList<>();
        testCompileSourceRoots.add(getBasedir() + "/src/test/java");
        setTestCompileSourceRoots(testCompileSourceRoots);

        getBuild().setDirectory("${project.basedir}/target");
        getBuild().setTestOutputDirectory(new File(getBasedir(), "target/classes").getAbsolutePath());
    }
}
