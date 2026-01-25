package com.pgcluster.api.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ClusterCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;

    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$",
            message = "Slug must contain only lowercase letters, numbers, and hyphens (cannot start or end with hyphen)")
    @Size(min = 1, max = 50, message = "Slug must be between 1 and 50 characters")
    private String slug;

    @Pattern(regexp = "^(dedicated|shared)$", message = "Plan must be 'dedicated' or 'shared'")
    private String plan = "dedicated";

    @Pattern(regexp = "^(14|15|16|17)$", message = "PostgreSQL version must be 14, 15, 16, or 17")
    private String postgresVersion = "16";

    @Pattern(regexp = "^(cx[2345]3|cpx[123]1|cpx[345]1|cax[123]1)$",
            message = "Invalid node size. Allowed: cx23, cx33, cx43, cx53, cpx11, cpx21, cpx31, cpx41, cpx51, cax11, cax21, cax31")
    private String nodeSize = "cx23";

    @NotNull(message = "Node regions are required")
    @Size(min = 3, max = 3, message = "Must specify exactly 3 node regions")
    private List<String> nodeRegions;
}
