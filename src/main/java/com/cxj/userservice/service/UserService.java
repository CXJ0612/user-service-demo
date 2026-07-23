package com.cxj.userservice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cxj.userservice.common.DuplicateResourceException;
import com.cxj.userservice.common.ResourceNotFoundException;
import com.cxj.userservice.dto.UserCreateRequest;
import com.cxj.userservice.dto.UserResponse;
import com.cxj.userservice.dto.UserUpdateRequest;
import com.cxj.userservice.entity.AppUser;
import com.cxj.userservice.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional
	public UserResponse create(UserCreateRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new DuplicateResourceException("Username already exists: " + request.username());
		}
		AppUser user = new AppUser();
		user.setUsername(request.username());
		user.setEmail(request.email());
		AppUser saved = userRepository.save(user);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<UserResponse> findAll() {
		return userRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public UserResponse findById(Long id) {
		return userRepository.findById(id)
				.map(this::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
	}

	@Transactional
	public UserResponse update(Long id, UserUpdateRequest request) {
		AppUser user = userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
		if (!user.getUsername().equals(request.username())
				&& userRepository.existsByUsername(request.username())) {
			throw new DuplicateResourceException("Username already exists: " + request.username());
		}
		user.setUsername(request.username());
		user.setEmail(request.email());
		AppUser updated = userRepository.save(user);
		return toResponse(updated);
	}

	@Transactional
	public void delete(Long id) {
		if (!userRepository.existsById(id)) {
			throw new ResourceNotFoundException("User not found with id: " + id);
		}
		userRepository.deleteById(id);
	}

	private UserResponse toResponse(AppUser user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
	}
}
