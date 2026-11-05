package com.urbantwin.city;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** City / zone catalog (contract §7.1). */
@RestController
@Tag(name = "city")
public class CityController {

    record CityDto(UUID id, String name, String slug, long population, long zoneCount) {}

    record ZoneDto(UUID id, String name, String zoneType, UUID cityId, String geojson) {}

    private final CityRepository cities;
    private final ZoneRepository zones;

    CityController(CityRepository cities, ZoneRepository zones) {
        this.cities = cities;
        this.zones = zones;
    }

    @GetMapping("/api/v1/cities")
    public List<CityDto> cities() {
        return cities.findAll().stream()
                .map(c -> new CityDto(c.getId(), c.getName(), c.getSlug(), c.getPopulation(),
                        zones.findAll().stream().filter(z -> z.getCity() != null && z.getCity().getId().equals(c.getId())).count()))
                .toList();
    }

    @GetMapping("/api/v1/zones")
    public List<ZoneDto> zones(@RequestParam(required = false) UUID cityId) {
        return zones.findAll().stream()
                .filter(z -> cityId == null || (z.getCity() != null && z.getCity().getId().equals(cityId)))
                .map(z -> new ZoneDto(z.getId(), z.getName(), z.getZoneType(),
                        z.getCity() != null ? z.getCity().getId() : null, z.getGeojson()))
                .toList();
    }
}
