package com.cxj.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
		@NotBlank @Size(max = 64) String username,
		@NotBlank @Email String email) {
}
