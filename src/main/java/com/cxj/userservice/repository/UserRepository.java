package com.cxj.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cxj.userservice.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, Long> {

	boolean existsByUsername(String username);
}
