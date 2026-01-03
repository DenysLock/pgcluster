package com.pgcluster.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClusterCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;

    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Size(min = 3, max = 50, message = "Slug must be between 3 and 50 characters")
    private String slug;

    private String plan = "dedicated";

    private String postgresVersion = "16";

    private int nodeCount = 3;

    private String nodeSize = "cx23";

    private String region = "fsn1";
}
