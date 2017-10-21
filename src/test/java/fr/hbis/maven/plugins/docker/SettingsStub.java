package fr.hbis.maven.plugins.docker;

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class SettingsStub extends Settings {
    private static final long serialVersionUID = 1L;

    public SettingsStub() {
        super();
        
        Server server = new Server();
        
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        server.setConfiguration(configuration);
        
        addServer(server);
    }
}