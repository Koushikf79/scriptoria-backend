package com.scriptoria.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "password must not be blank")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;

    @NotBlank(message = "fullName must not be blank")
    private String fullName;
}
