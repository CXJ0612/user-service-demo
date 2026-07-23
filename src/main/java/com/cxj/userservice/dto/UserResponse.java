package com.cxj.userservice.dto;

import java.time.Instant;

public record UserResponse(
		Long id,
		String username,
		String email,
		Instant createdAt) {
}
