package com.campus.lostfound.modules.item.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VersionRequest(@NotNull @Positive Long expectedVersion) {}
