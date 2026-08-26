package com.datagov.scrapper.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_resources", indexes = {
    @Index(name = "idx_resource_id", columnList = "resourceId", unique = true),
    @Index(name = "idx_title", columnList = "title"),
    @Index(name = "idx_created_date", columnList = "createdDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String resourceId; // index_name or uuid

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String source;

    private String orgType;

    @Column(columnDefinition = "TEXT")
    private String organizations; // JSON array or comma separated

    @Column(columnDefinition = "TEXT")
    private String sectors; // JSON array or comma separated

    @Column(columnDefinition = "TEXT")
    private String fieldsJson; // JSON array of field definitions

    @Column(columnDefinition = "TEXT")
    private String swaggerJson; // Complete Swagger JSON spec if fetched

    @Column(columnDefinition = "TEXT")
    private String apiUrl; // Direct endpoint URL

    private String active;

    private String visualizable;

    private String catalogUuid;

    private String createdDate;

    private String updatedDate;

    private LocalDateTime scrapedAt;
}
