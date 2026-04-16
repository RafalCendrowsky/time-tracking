package com.timetracking.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(@Email @NotBlank String email, @NotBlank @Size(min = 8) String password) {
}
