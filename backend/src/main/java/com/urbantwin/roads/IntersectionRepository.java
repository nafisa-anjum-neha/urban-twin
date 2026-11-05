package com.urbantwin.roads;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IntersectionRepository extends JpaRepository<Intersection, UUID> {
}
