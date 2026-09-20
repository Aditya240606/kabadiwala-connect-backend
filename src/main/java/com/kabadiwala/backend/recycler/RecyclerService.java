package com.kabadiwala.backend.recycler;

import com.kabadiwala.backend.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecyclerService {

    private final RecyclerRepository recyclerRepository;

    public RecyclerService(RecyclerRepository recyclerRepository) {
        this.recyclerRepository = recyclerRepository;
    }

    @Transactional(readOnly = true)
    public List<RecyclerDto> findMatchingRecyclers(String categoryCode, String city) {
        return recyclerRepository.findByStatus("ACTIVE")
                .stream()
                .filter(r -> categoryCode == null || r.getAcceptedCategoryList().contains(categoryCode))
                .filter(r -> city == null || city.isBlank() || (r.getCity() != null && r.getCity().equalsIgnoreCase(city.trim())))
                .map(RecyclerDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Recycler getById(UUID id) {
        return recyclerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recycler", id));
    }
}
