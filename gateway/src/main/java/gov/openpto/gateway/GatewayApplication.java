package gov.openpto.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GatewayApplication {

    static {
        allowHostHeader();
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * The proxy preserves the client's Host header; the JDK HttpClient only allows setting it when this
     * property is present before its first use (it is read once, at class-init time).
     */
    static void allowHostHeader() {
        String key = "jdk.httpclient.allowRestrictedHeaders";
        String current = System.getProperty(key, "");
        if (!current.toLowerCase(java.util.Locale.ROOT).contains("host")) {
            System.setProperty(key, current.isBlank() ? "host" : current + ",host");
        }
    }

}
