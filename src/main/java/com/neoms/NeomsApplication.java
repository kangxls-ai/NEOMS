package com.neoms;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;

@SpringBootApplication
public class NeomsApplication {
    public static void main(String[] args) throws Exception {
        Path location = Path.of(NeomsApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path home = Files.isDirectory(location) ? Path.of("").toAbsolutePath() : location.getParent();
        // Anchor external resources to the JAR, including launches from another directory.
        System.setProperty("neoms.home", home.toString().replace('\\', '/'));
        if (System.getProperty("os.name", "").startsWith("Windows")
                && System.getProperty("jdk.net.unixdomain.tmpdir") == null) {
            String temporaryDirectory = System.getenv("NEOMS_JAVA_TMP");
            if (temporaryDirectory == null || temporaryDirectory.isBlank()) {
                temporaryDirectory = Path.of(System.getenv().getOrDefault("SystemRoot", "C:/Windows"), "Temp").toString();
            }
            System.setProperty("jdk.net.unixdomain.tmpdir", temporaryDirectory);
        }
        SpringApplication application = new SpringApplication(NeomsApplication.class);
        application.setDefaultProperties(Map.of("spring.config.additional-location",
                "optional:" + home.resolve("config").toUri()));
        application.run(args);
    }
}
