package com.scriptoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScreenplayRequest {

    @NotBlank(message = "Screenplay text must not be blank")
    @Size(min = 100, max = 200000, message = "Screenplay must be between 100 and 200,000 characters")
    private String screenplay;

    /** Optional market context for budget simulation */
    private String market = "GENERAL"; // TOLLYWOOD | BOLLYWOOD | HOLLYWOOD | KOREAN | GENERAL
}
