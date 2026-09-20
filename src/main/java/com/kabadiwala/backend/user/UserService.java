package com.kabadiwala.backend.user;

import com.kabadiwala.backend.auth.UserPrincipal;
import com.kabadiwala.backend.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;

    public UserService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public UserProfile getOrCreateProfile(UserPrincipal principal) {
        return userProfileRepository.findById(principal.getId())
                .orElseGet(() -> {
                    UserProfile newProfile = new UserProfile(
                            principal.getId(),
                            principal.getEmail(),
                            principal.getUsername(),
                            principal.getRole()
                    );
                    return userProfileRepository.save(newProfile);
                });
    }

    @Transactional(readOnly = true)
    public UserProfile getById(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", id));
    }
}
