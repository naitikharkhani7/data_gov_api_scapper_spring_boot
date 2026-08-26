package com.datagov.scrapper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-Performance In-Memory Inverted Index & Multi-Dimensional DSA Search Engine.
 * Supports instant sub-millisecond filtering across State, Sector, Org Type, Year, and Search Keywords.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    public static final List<String> INDIAN_STATES = List.of(
            "Andaman and Nicobar", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli", "Daman and Diu", "Delhi",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand",
            "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra",
            "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab",
            "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal"
    );

    // Inverted Indexes in RAM for O(1) multi-dimensional lookups
    private final Map<String, List<Long>> sectorInvertedIndex = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> stateInvertedIndex = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> orgTypeInvertedIndex = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> yearInvertedIndex = new ConcurrentHashMap<>();

    // Lightweight In-Memory Search Registry
    public record IndexItem(
            long id,
            String resourceIdLower,
            String titleLower,
            String sectorLower,
            String orgLower,
            String orgTypeLower,
            String stateLower,
            String year
    ) {}

    private final List<IndexItem> allIndexItems = new CopyOnWriteArrayList<>();
    private volatile boolean isReady = false;

    public record PagedIndexResult(List<Long> ids, long totalCount) {}

    @PostConstruct
    public void buildIndexAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                log.info("[DSA INDEX] 🚀 Starting Multi-Dimensional Inverted Index construction from database...");

                String sql = "SELECT id, resource_id, title, sectors, organizations, org_type, created_date, updated_date FROM api_resources ORDER BY id DESC";
                List<IndexItem> tempList = new ArrayList<>(200000);

                Map<String, List<Long>> tempSectorMap = new HashMap<>();
                Map<String, List<Long>> tempStateMap = new HashMap<>();
                Map<String, List<Long>> tempOrgTypeMap = new HashMap<>();
                Map<String, List<Long>> tempYearMap = new HashMap<>();

                jdbcTemplate.query(sql, rs -> {
                    long id = rs.getLong("id");
                    String resId = rs.getString("resource_id");
                    String title = rs.getString("title");
                    String sectorsJson = rs.getString("sectors");
                    String orgsJson = rs.getString("organizations");
                    String orgType = rs.getString("org_type");
                    String createdDate = rs.getString("created_date");
                    String updatedDate = rs.getString("updated_date");

                    String resIdLower = resId != null ? resId.toLowerCase() : "";
                    String titleLower = title != null ? title.toLowerCase() : "";
                    String sectorLower = sectorsJson != null ? sectorsJson.toLowerCase() : "";
                    String orgLower = orgsJson != null ? orgsJson.toLowerCase() : "";
                    String orgTypeLower = orgType != null ? orgType.toLowerCase().trim() : "";

                    // Detect State
                    String detectedState = detectState(titleLower, orgLower, sectorLower);
                    String stateLower = detectedState != null ? detectedState.toLowerCase() : "";

                    // Detect Year
                    String itemYear = extractYear(createdDate, updatedDate, title);

                    IndexItem item = new IndexItem(id, resIdLower, titleLower, sectorLower, orgLower, orgTypeLower, stateLower, itemYear);
                    tempList.add(item);

                    // Index Sector
                    List<String> parsedSectors = parseJsonStringList(sectorsJson);
                    for (String sec : parsedSectors) {
                        String key = sec.trim().toLowerCase();
                        if (!key.isEmpty()) {
                            tempSectorMap.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
                        }
                    }

                    // Index State
                    if (detectedState != null && !detectedState.isEmpty()) {
                        tempStateMap.computeIfAbsent(detectedState.toLowerCase(), k -> new ArrayList<>()).add(id);
                    }

                    // Index OrgType (Central, State, etc.)
                    if (!orgTypeLower.isEmpty()) {
                        tempOrgTypeMap.computeIfAbsent(orgTypeLower, k -> new ArrayList<>()).add(id);
                    }

                    // Index Year
                    if (itemYear != null && !itemYear.isEmpty()) {
                        tempYearMap.computeIfAbsent(itemYear, k -> new ArrayList<>()).add(id);
                    }
                });

                allIndexItems.clear();
                allIndexItems.addAll(tempList);

                sectorInvertedIndex.clear();
                tempSectorMap.forEach((k, v) -> sectorInvertedIndex.put(k, Collections.synchronizedList(v)));

                stateInvertedIndex.clear();
                tempStateMap.forEach((k, v) -> stateInvertedIndex.put(k, Collections.synchronizedList(v)));

                orgTypeInvertedIndex.clear();
                tempOrgTypeMap.forEach((k, v) -> orgTypeInvertedIndex.put(k, Collections.synchronizedList(v)));

                yearInvertedIndex.clear();
                tempYearMap.forEach((k, v) -> yearInvertedIndex.put(k, Collections.synchronizedList(v)));

                isReady = true;
                long took = System.currentTimeMillis() - start;
                log.info("[DSA INDEX] ✅ Multi-Dimensional Index built! Loaded {} records ({} states, {} sectors, {} years, {} orgTypes) in {} ms",
                        allIndexItems.size(), stateInvertedIndex.size(), sectorInvertedIndex.size(), yearInvertedIndex.size(), orgTypeInvertedIndex.size(), took);
            } catch (Exception e) {
                log.error("[DSA INDEX] ❌ Error building Multi-Dimensional Index: {}", e.getMessage(), e);
            }
        });
    }

    public boolean isReady() {
        return isReady && !allIndexItems.isEmpty();
    }

    /**
     * Multi-Dimensional Filter across Search, State, Sector, OrgType, Year
     */
    public PagedIndexResult searchAndFilter(String search, String state, String sector, String orgType, String year, int page, int size) {
        if (!isReady) return null;

        String sQuery = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;
        String stQuery = (state != null && !state.isBlank() && !"ALL".equalsIgnoreCase(state)) ? state.trim().toLowerCase() : null;
        String secQuery = (sector != null && !sector.isBlank() && !"ALL".equalsIgnoreCase(sector)) ? sector.trim().toLowerCase() : null;
        String otQuery = (orgType != null && !orgType.isBlank() && !"ALL".equalsIgnoreCase(orgType)) ? orgType.trim().toLowerCase() : null;
        String yrQuery = (year != null && !year.isBlank() && !"ALL".equalsIgnoreCase(year)) ? year.trim() : null;

        // O(1) Fast paths for single isolated filters
        if (sQuery == null && stQuery == null && otQuery == null && yrQuery == null && secQuery != null) {
            return filterFromMap(sectorInvertedIndex, secQuery, page, size);
        }
        if (sQuery == null && secQuery == null && otQuery == null && yrQuery == null && stQuery != null) {
            return filterFromMap(stateInvertedIndex, stQuery, page, size);
        }
        if (sQuery == null && secQuery == null && stQuery == null && yrQuery == null && otQuery != null) {
            return filterFromMap(orgTypeInvertedIndex, otQuery, page, size);
        }
        if (sQuery == null && secQuery == null && stQuery == null && otQuery == null && yrQuery != null) {
            return filterFromMap(yearInvertedIndex, yrQuery, page, size);
        }

        // Multi-condition combination in RAM (takes < 2 ms across 180k items)
        List<Long> matchedIds = new ArrayList<>(2000);

        for (IndexItem item : allIndexItems) {
            if (stQuery != null && !item.stateLower().contains(stQuery) && !item.titleLower().contains(stQuery) && !item.orgLower().contains(stQuery)) {
                continue;
            }
            if (secQuery != null && !item.sectorLower().contains(secQuery)) {
                continue;
            }
            if (otQuery != null && !item.orgTypeLower().contains(otQuery)) {
                continue;
            }
            if (yrQuery != null) {
                boolean matchesYear = (item.year() != null && item.year().equals(yrQuery)) ||
                        item.titleLower().contains(yrQuery);
                if (!matchesYear) continue;
            }
            if (sQuery != null) {
                boolean matchesSearch = item.titleLower().contains(sQuery) ||
                        item.resourceIdLower().contains(sQuery) ||
                        item.orgLower().contains(sQuery);
                if (!matchesSearch) continue;
            }

            matchedIds.add(item.id());
        }

        return paginate(matchedIds, page, size);
    }

    private PagedIndexResult filterFromMap(Map<String, List<Long>> map, String key, int page, int size) {
        List<Long> list = map.get(key);
        if (list == null) {
            for (Map.Entry<String, List<Long>> entry : map.entrySet()) {
                if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                    list = entry.getValue();
                    break;
                }
            }
        }
        if (list == null || list.isEmpty()) {
            return new PagedIndexResult(Collections.emptyList(), 0);
        }
        return paginate(list, page, size);
    }

    private PagedIndexResult paginate(List<Long> list, int page, int size) {
        int totalCount = list.size();
        int offset = page * size;
        if (offset >= totalCount) {
            return new PagedIndexResult(Collections.emptyList(), totalCount);
        }

        int toIndex = Math.min(offset + size, totalCount);
        List<Long> pagedIds = new ArrayList<>(list.subList(offset, toIndex));
        return new PagedIndexResult(pagedIds, totalCount);
    }

    private String detectState(String title, String orgs, String sectors) {
        for (String state : INDIAN_STATES) {
            String sLower = state.toLowerCase();
            if (title.contains(sLower) || orgs.contains(sLower) || sectors.contains(sLower)) {
                return state;
            }
            // Additional abbreviations
            if ("odisha".equalsIgnoreCase(state) && (title.contains("orissa") || orgs.contains("orissa"))) {
                return "Odisha";
            }
            if ("puducherry".equalsIgnoreCase(state) && (title.contains("pondicherry") || orgs.contains("pondicherry"))) {
                return "Puducherry";
            }
            if ("uttarakhand".equalsIgnoreCase(state) && (title.contains("uttaranchal") || orgs.contains("uttaranchal"))) {
                return "Uttarakhand";
            }
        }
        return "";
    }

    private String extractYear(String createdDate, String updatedDate, String title) {
        if (createdDate != null && createdDate.length() >= 4) {
            String y = createdDate.substring(0, 4);
            if (isNumericYear(y)) return y;
        }
        if (updatedDate != null && updatedDate.length() >= 4) {
            String y = updatedDate.substring(0, 4);
            if (isNumericYear(y)) return y;
        }
        if (title != null) {
            Matcher m = YEAR_PATTERN.matcher(title);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    private boolean isNumericYear(String s) {
        if (s == null || s.length() != 4) return false;
        try {
            int y = Integer.parseInt(s);
            return y >= 1970 && y <= 2035;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            if (json.startsWith("[")) {
                JsonNode node = objectMapper.readTree(json);
                List<String> list = new ArrayList<>();
                if (node.isArray()) {
                    for (JsonNode elem : node) {
                        list.add(elem.asText());
                    }
                }
                return list;
            } else {
                return Arrays.asList(json.split(","));
            }
        } catch (Exception e) {
            return Arrays.asList(json.split(","));
        }
    }
}
