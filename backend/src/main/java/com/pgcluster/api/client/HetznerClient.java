package com.pgcluster.api.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HetznerClient {

    private static final String BASE_URL = "https://api.hetzner.cloud/v1";

    @Value("${hetzner.api-token}")
    private String apiToken;

    @Value("${hetzner.ssh-key-ids}")
    private String sshKeyIds;

    @Value("${hetzner.snapshot-id:ubuntu-24.04}")
    private String snapshotId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a new server
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public ServerResponse createServer(CreateServerRequest request) {
        HttpHeaders headers = createHeaders();
        HttpEntity<CreateServerRequest> entity = new HttpEntity<>(request, headers);

        log.info("Creating Hetzner server: {}", request.getName());

        ResponseEntity<CreateServerResponse> response = restTemplate.exchange(
                BASE_URL + "/servers",
                HttpMethod.POST,
                entity,
                CreateServerResponse.class
        );

        if (response.getBody() != null) {
            log.info("Server created: {} (ID: {})",
                    response.getBody().getServer().getName(),
                    response.getBody().getServer().getId());
            return response.getBody().getServer();
        }
        throw new RuntimeException("Failed to create server");
    }

    /**
     * Delete a server
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public void deleteServer(Long serverId) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Deleting Hetzner server: {}", serverId);

        restTemplate.exchange(
                BASE_URL + "/servers/" + serverId,
                HttpMethod.DELETE,
                entity,
                Void.class
        );

        log.info("Server deleted: {}", serverId);
    }

    /**
     * List servers by label selector (e.g., "cluster=my-cluster")
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public List<ServerResponse> listServersByLabel(String labelSelector) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Listing Hetzner servers with label: {}", labelSelector);

        ResponseEntity<ServerListResponse> response = restTemplate.exchange(
                BASE_URL + "/servers?label_selector=" + labelSelector,
                HttpMethod.GET,
                entity,
                ServerListResponse.class
        );

        if (response.getBody() != null && response.getBody().getServers() != null) {
            log.info("Found {} servers with label: {}", response.getBody().getServers().size(), labelSelector);
            return response.getBody().getServers();
        }
        return List.of();
    }

    /**
     * Get server details
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public ServerResponse getServer(Long serverId) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<GetServerResponse> response = restTemplate.exchange(
                BASE_URL + "/servers/" + serverId,
                HttpMethod.GET,
                entity,
                GetServerResponse.class
        );

        if (response.getBody() != null) {
            return response.getBody().getServer();
        }
        throw new RuntimeException("Server not found: " + serverId);
    }

    /**
     * Get available locations/datacenters
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public List<LocationInfo> getLocations() {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Fetching Hetzner locations");

        ResponseEntity<LocationListResponse> response = restTemplate.exchange(
                BASE_URL + "/locations",
                HttpMethod.GET,
                entity,
                LocationListResponse.class
        );

        if (response.getBody() != null && response.getBody().getLocations() != null) {
            log.info("Found {} locations", response.getBody().getLocations().size());
            return response.getBody().getLocations();
        }
        throw new RuntimeException("Failed to fetch locations");
    }

    /**
     * Assign floating IP to server
     */
    public void assignFloatingIp(Long floatingIpId, Long serverId) {
        HttpHeaders headers = createHeaders();
        Map<String, Long> body = Map.of("server", serverId);
        HttpEntity<Map<String, Long>> entity = new HttpEntity<>(body, headers);

        log.info("Assigning floating IP {} to server {}", floatingIpId, serverId);

        restTemplate.exchange(
                BASE_URL + "/floating_ips/" + floatingIpId + "/actions/assign",
                HttpMethod.POST,
                entity,
                Void.class
        );

        log.info("Floating IP assigned successfully");
    }

    /**
     * Unassign floating IP from server
     */
    public void unassignFloatingIp(Long floatingIpId) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Unassigning floating IP {}", floatingIpId);

        restTemplate.exchange(
                BASE_URL + "/floating_ips/" + floatingIpId + "/actions/unassign",
                HttpMethod.POST,
                entity,
                Void.class
        );
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);
        return headers;
    }

    public String[] getSshKeyIds() {
        return sshKeyIds.split(",");
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    // DTOs

    @Data
    public static class CreateServerRequest {
        private String name;
        @JsonProperty("server_type")
        private String serverType;
        private String image;
        private String location;
        @JsonProperty("ssh_keys")
        private List<String> sshKeys;
        @JsonProperty("user_data")
        private String userData; // cloud-init script
        private Map<String, String> labels;

        public static CreateServerRequest builder() {
            return new CreateServerRequest();
        }

        public CreateServerRequest name(String name) { this.name = name; return this; }
        public CreateServerRequest serverType(String type) { this.serverType = type; return this; }
        public CreateServerRequest image(String image) { this.image = image; return this; }
        public CreateServerRequest location(String loc) { this.location = loc; return this; }
        public CreateServerRequest sshKeys(List<String> keys) { this.sshKeys = keys; return this; }
        public CreateServerRequest userData(String data) { this.userData = data; return this; }
        public CreateServerRequest labels(Map<String, String> labels) { this.labels = labels; return this; }
    }

    @Data
    public static class CreateServerResponse {
        private ServerResponse server;
        private Object action; // Hetzner returns action as an object
        @JsonProperty("root_password")
        private String rootPassword;
    }

    @Data
    public static class GetServerResponse {
        private ServerResponse server;
    }

    @Data
    public static class ServerListResponse {
        private List<ServerResponse> servers;
    }

    @Data
    public static class ServerResponse {
        private Long id;
        private String name;
        private String status;
        @JsonProperty("public_net")
        private PublicNet publicNet;
        @JsonProperty("server_type")
        private ServerType serverType;
        private Datacenter datacenter;
    }

    @Data
    public static class PublicNet {
        private Ipv4 ipv4;
        private Ipv6 ipv6;
    }

    @Data
    public static class Ipv4 {
        private String ip;
    }

    @Data
    public static class Ipv6 {
        private String ip;
    }

    @Data
    public static class ServerType {
        private String name;
        private int cores;
        private int memory;
        private int disk;
    }

    @Data
    public static class Datacenter {
        private String name;
        private Location location;
    }

    @Data
    public static class Location {
        private String name;
        private String city;
        private String country;
    }

    /**
     * Get server type details including location availability
     */
    @CircuitBreaker(name = "hetzner")
    @Retry(name = "hetzner")
    public ServerTypeInfo getServerType(String serverTypeName) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Fetching server type info for: {}", serverTypeName);

        ResponseEntity<ServerTypeListResponse> response = restTemplate.exchange(
                BASE_URL + "/server_types?name=" + serverTypeName,
                HttpMethod.GET,
                entity,
                ServerTypeListResponse.class
        );

        if (response.getBody() != null && response.getBody().getServerTypes() != null
                && !response.getBody().getServerTypes().isEmpty()) {
            return response.getBody().getServerTypes().get(0);
        }
        throw new RuntimeException("Server type not found: " + serverTypeName);
    }

    /**
     * Get set of location names where a server type is available
     */
    public java.util.Set<String> getAvailableLocationsForServerType(String serverTypeName) {
        ServerTypeInfo serverType = getServerType(serverTypeName);
        java.util.Set<String> availableLocations = new java.util.HashSet<>();

        if (serverType.getPrices() != null) {
            for (ServerTypePrice price : serverType.getPrices()) {
                if (price.getLocation() != null) {
                    availableLocations.add(price.getLocation());
                }
            }
        }

        log.info("Server type {} is available in {} locations: {}",
                serverTypeName, availableLocations.size(), availableLocations);
        return availableLocations;
    }

    // Location API DTOs
    @Data
    public static class LocationListResponse {
        private List<LocationInfo> locations;
    }

    @Data
    public static class LocationInfo {
        private Long id;
        private String name;        // "fsn1"
        private String description; // "Falkenstein DC Park 1"
        private String country;     // "DE"
        private String city;        // "Falkenstein"
        @JsonProperty("network_zone")
        private String networkZone; // "eu-central"
    }

    // Server Type API DTOs
    @Data
    public static class ServerTypeListResponse {
        @JsonProperty("server_types")
        private List<ServerTypeInfo> serverTypes;
    }

    @Data
    public static class ServerTypeInfo {
        private Long id;
        private String name;
        private String description;
        private int cores;
        private int memory;       // in MB
        private int disk;         // in GB
        private List<ServerTypePrice> prices;
    }

    @Data
    public static class ServerTypePrice {
        private String location;  // "fsn1"
        @JsonProperty("price_hourly")
        private PriceDetail priceHourly;
        @JsonProperty("price_monthly")
        private PriceDetail priceMonthly;
    }

    @Data
    public static class PriceDetail {
        private String net;
        private String gross;
    }
}
