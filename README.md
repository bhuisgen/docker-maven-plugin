# docker-maven-plugin

Maven plugin to build Docker images of your Java project.

## Usage

To build a docker image of your project, add the plugin in your POM file:

    </plugins>
        <plugin>
            <groupId>fr.hbis.maven.plugins</groupId>
            <artifactId>docker-maven-plugin</artifactId>
            <version>0.2.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>build</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <directory>src/main/docker</directory>
                <imageName>registry.host.io/group/${project.artifactId}</imageName>
                <imageTags>
                    <imageTag>latest</imageTag>
                </imageTags>
                <push>false</push>
                <remove>false</remove>
            </configuration>
        </plugin>
    </plugins>

Create the directory *src/main/docker* in your project and add it your custom
*Dockerfile*:

    FROM bhuisgen/alpine-java:latest
    COPY /root /

Build your project:

    $ mvn clean install
