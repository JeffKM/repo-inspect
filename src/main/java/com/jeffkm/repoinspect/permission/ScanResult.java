package com.jeffkm.repoinspect.permission;

import java.util.List;
import java.util.Set;

public record ScanResult(List<Finding> findings, Set<String> domains, boolean findingsTruncated,
    boolean domainsTruncated) {
}
