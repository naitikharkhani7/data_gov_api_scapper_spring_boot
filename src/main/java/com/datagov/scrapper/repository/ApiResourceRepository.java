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

    @Query(value = "SELECT id FROM api_resources ORDER BY id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Long> findPagedIdsDesc(@Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT id FROM api_resources ORDER BY id ASC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Long> findPagedIdsAsc(@Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT r FROM ApiResourceEntity r WHERE r.id IN (:ids)")
    List<ApiResourceEntity> findAllByIdInList(@Param("ids") List<Long> ids);

    @Query(value = "SELECT id FROM api_resources WHERE " +
           "(:search IS NULL OR LOWER(title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(resource_id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(organizations) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:sector IS NULL OR LOWER(sectors) LIKE LOWER(CONCAT('%', :sector, '%'))) " +
           "ORDER BY id DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Long> searchPagedIds(
            @Param("search") String search,
            @Param("sector") String sector,
            @Param("limit") int limit,
            @Param("offset") int offset
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
