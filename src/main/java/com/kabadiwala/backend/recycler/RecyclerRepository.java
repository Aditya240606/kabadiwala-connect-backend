package com.kabadiwala.backend.recycler;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecyclerRepository extends JpaRepository<Recycler, UUID> {
    List<Recycler> findByStatus(String status);
}
