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

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudflareClient {

    private static final String BASE_URL = "https://api.cloudflare.com/client/v4";

    @Value("${cloudflare.api-token}")
    private String apiToken;

    @Value("${cloudflare.zone-id}")
    private String zoneId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a DNS A record
     */
    @CircuitBreaker(name = "cloudflare")
    @Retry(name = "cloudflare")
    public DnsRecord createDnsRecord(String name, String ip, boolean proxied) {
        HttpHeaders headers = createHeaders();

        CreateDnsRequest request = new CreateDnsRequest();
        request.setType("A");
        request.setName(name);
        request.setContent(ip);
        request.setProxied(proxied);
        request.setTtl(proxied ? 1 : 300); // Auto TTL for proxied, 5min for DNS only

        HttpEntity<CreateDnsRequest> entity = new HttpEntity<>(request, headers);

        log.info("Creating DNS record: {} -> {}", name, ip);

        ResponseEntity<DnsResponse> response = restTemplate.exchange(
                BASE_URL + "/zones/" + zoneId + "/dns_records",
                HttpMethod.POST,
                entity,
                DnsResponse.class
        );

        if (response.getBody() != null && response.getBody().isSuccess()) {
            log.info("DNS record created: {} (ID: {})",
                    response.getBody().getResult().getName(),
                    response.getBody().getResult().getId());
            return response.getBody().getResult();
        }

        throw new RuntimeException("Failed to create DNS record: " +
                (response.getBody() != null ? response.getBody().getErrors() : "Unknown error"));
    }

    /**
     * Update a DNS record
     */
    @CircuitBreaker(name = "cloudflare")
    @Retry(name = "cloudflare")
    public DnsRecord updateDnsRecord(String recordId, String name, String ip, boolean proxied) {
        HttpHeaders headers = createHeaders();

        CreateDnsRequest request = new CreateDnsRequest();
        request.setType("A");
        request.setName(name);
        request.setContent(ip);
        request.setProxied(proxied);
        request.setTtl(proxied ? 1 : 300);

        HttpEntity<CreateDnsRequest> entity = new HttpEntity<>(request, headers);

        log.info("Updating DNS record {}: {} -> {}", recordId, name, ip);

        ResponseEntity<DnsResponse> response = restTemplate.exchange(
                BASE_URL + "/zones/" + zoneId + "/dns_records/" + recordId,
                HttpMethod.PUT,
                entity,
                DnsResponse.class
        );

        if (response.getBody() != null && response.getBody().isSuccess()) {
            return response.getBody().getResult();
        }

        throw new RuntimeException("Failed to update DNS record");
    }

    /**
     * Delete a DNS record
     */
    @CircuitBreaker(name = "cloudflare")
    @Retry(name = "cloudflare")
    public void deleteDnsRecord(String recordId) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        log.info("Deleting DNS record: {}", recordId);

        restTemplate.exchange(
                BASE_URL + "/zones/" + zoneId + "/dns_records/" + recordId,
                HttpMethod.DELETE,
                entity,
                Void.class
        );

        log.info("DNS record deleted: {}", recordId);
    }

    /**
     * Find DNS record by name
     */
    @CircuitBreaker(name = "cloudflare")
    @Retry(name = "cloudflare")
    public DnsRecord findDnsRecord(String name) {
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<ListDnsResponse> response = restTemplate.exchange(
                BASE_URL + "/zones/" + zoneId + "/dns_records?name=" + name,
                HttpMethod.GET,
                entity,
                ListDnsResponse.class
        );

        if (response.getBody() != null && response.getBody().isSuccess()) {
            List<DnsRecord> records = response.getBody().getResult();
            if (records != null && !records.isEmpty()) {
                return records.get(0);
            }
        }
        return null;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);
        return headers;
    }

    // DTOs

    @Data
    public static class CreateDnsRequest {
        private String type;
        private String name;
        private String content;
        private boolean proxied;
        private int ttl;
    }

    @Data
    public static class DnsResponse {
        private boolean success;
        private List<String> errors;
        private List<String> messages;
        private DnsRecord result;
    }

    @Data
    public static class ListDnsResponse {
        private boolean success;
        private List<String> errors;
        private List<DnsRecord> result;
    }

    @Data
    public static class DnsRecord {
        private String id;
        private String type;
        private String name;
        private String content;
        private boolean proxied;
        private int ttl;
        @JsonProperty("created_on")
        private String createdOn;
        @JsonProperty("modified_on")
        private String modifiedOn;
    }
}
