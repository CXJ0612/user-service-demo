package com.cxj.userservice.common;

import java.time.Instant;

public record ErrorResponse(
		int status,
		String error,
		String message,
		Instant timestamp) {
}
