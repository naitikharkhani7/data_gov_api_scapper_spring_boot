package com.datagov.scrapper.repository;

import com.datagov.scrapper.model.ApiResourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiResourceRepository extends JpaRepository<ApiResourceEntity, Long> {

    Optional<ApiResourceEntity> findByResourceId(String resourceId);

    boolean existsByResourceId(String resourceId);

    @Query("SELECT r FROM ApiResourceEntity r")
    List<ApiResourceEntity> findPagedRecords(Pageable pageable);

    @Query("SELECT r FROM ApiResourceEntity r WHERE " +
           "(:search IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.resourceId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.organizations) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:sector IS NULL OR LOWER(r.sectors) LIKE LOWER(CONCAT('%', :sector, '%')))")
    List<ApiResourceEntity> searchResourcesPaged(
            @Param("search") String search,
            @Param("sector") String sector,
            Pageable pageable
    );

    @Query("SELECT count(r) FROM ApiResourceEntity r WHERE " +
           "(:search IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.resourceId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(r.organizations) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:sector IS NULL OR LOWER(r.sectors) LIKE LOWER(CONCAT('%', :sector, '%')))")
    long countFiltered(
            @Param("search") String search,
            @Param("sector") String sector
    );
}
